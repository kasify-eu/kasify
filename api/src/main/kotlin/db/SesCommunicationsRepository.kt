package eu.kasify.db

import eu.kasify.db.model.BookingStatus
import eu.kasify.db.model.BookingsTable
import eu.kasify.db.model.PropertiesTable
import eu.kasify.db.model.SesCodigoEstado
import eu.kasify.db.model.SesCommunicationsTable
import eu.kasify.model.SesCommunication
import eu.kasify.model.SesCommunicationOperation
import eu.kasify.model.SesCommunicationStatus
import eu.kasify.model.SesCommunicationType
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.innerJoin
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import java.time.OffsetDateTime
import kotlin.uuid.Uuid

class SesCommunicationsRepository {
    suspend fun getByKey(
        bookingId: Uuid,
        type: SesCommunicationType,
        operation: SesCommunicationOperation,
    ): SesCommunication? =
        dbQuery {
            SesCommunicationsTable
                .selectAll()
                .where {
                    (SesCommunicationsTable.bookingId eq bookingId) and
                        (SesCommunicationsTable.communicationType eq type) and
                        (SesCommunicationsTable.operation eq operation)
                }.map { SesCommunication.from(it) }
                .firstOrNull()
        }

    suspend fun getById(id: Uuid): SesCommunication? =
        dbQuery {
            SesCommunicationsTable
                .selectAll()
                .where { SesCommunicationsTable.id eq id }
                .map { SesCommunication.from(it) }
                .firstOrNull()
        }

    suspend fun listByBooking(bookingId: Uuid): List<SesCommunication> =
        dbQuery {
            SesCommunicationsTable
                .selectAll()
                .where { SesCommunicationsTable.bookingId eq bookingId }
                .map { SesCommunication.from(it) }
        }

    suspend fun listByBookings(bookingIds: Collection<Uuid>): Map<Uuid, List<SesCommunication>> {
        if (bookingIds.isEmpty()) return emptyMap()
        return dbQuery {
            SesCommunicationsTable
                .selectAll()
                .where { SesCommunicationsTable.bookingId inList bookingIds.toList() }
                .map { SesCommunication.from(it) }
                .groupBy { it.bookingId }
        }
    }

    /**
     * Whether the booking has filed its PV (Parte de Viajeros) alta with SES — i.e. a PV alta
     * communication exists in submitted/processing/accepted. Drives the guest-data retention guard:
     * once guest data has been reported to the Spanish registry it must be retained for 3 years, so
     * owner-side guest edits/deletes are locked. (Replaces the old `bookings.ses_submitted_at` check.)
     */
    suspend fun isPvAltaFiled(bookingId: Uuid): Boolean =
        dbQuery {
            SesCommunicationsTable
                .selectAll()
                .where {
                    (SesCommunicationsTable.bookingId eq bookingId) and
                        (SesCommunicationsTable.communicationType eq SesCommunicationType.PV) and
                        (SesCommunicationsTable.operation eq SesCommunicationOperation.Alta) and
                        (
                            SesCommunicationsTable.status inList
                                listOf(
                                    SesCommunicationStatus.Submitted,
                                    SesCommunicationStatus.Processing,
                                    SesCommunicationStatus.Accepted,
                                )
                        )
                }.limit(1)
                .any()
        }

    suspend fun upsertSubmitted(
        bookingId: Uuid,
        type: SesCommunicationType,
        operation: SesCommunicationOperation,
        lote: String?,
        codigoComunicacion: String?,
        cancelsId: Uuid? = null,
    ): Uuid =
        dbQuery {
            val now = OffsetDateTime.now()
            val existingId = findId(bookingId, type, operation)
            if (existingId == null) {
                val result =
                    SesCommunicationsTable.insert {
                        it[SesCommunicationsTable.bookingId] = bookingId
                        it[communicationType] = type
                        it[SesCommunicationsTable.operation] = operation
                        it[SesCommunicationsTable.cancelsId] = cancelsId
                        it[status] = SesCommunicationStatus.Submitted
                        it[SesCommunicationsTable.lote] = lote
                        it[SesCommunicationsTable.codigoComunicacion] = codigoComunicacion
                        it[submittedAt] = now
                        it[lastAttemptAt] = now
                        it[lastError] = null
                        it[estado] = null
                        it[estadoDesc] = null
                        it[createdAt] = now
                        it[updatedAt] = now
                    }
                result[SesCommunicationsTable.id]
            } else {
                SesCommunicationsTable.update({ SesCommunicationsTable.id eq existingId }) {
                    it[SesCommunicationsTable.cancelsId] = cancelsId
                    it[status] = SesCommunicationStatus.Submitted
                    it[SesCommunicationsTable.lote] = lote
                    it[SesCommunicationsTable.codigoComunicacion] = codigoComunicacion
                    it[submittedAt] = now
                    it[lastAttemptAt] = now
                    it[lastError] = null
                    // Clear any terminal estado/desc from a prior lote's poll. Otherwise a resubmission
                    // keeps the stale estado (e.g. 6_tramitado_con_errores_parciales) and listPendingPoll —
                    // which selects on estado — never re-polls the new lote. See upsertSubmitted test.
                    it[estado] = null
                    it[estadoDesc] = null
                    it[updatedAt] = now
                }
                existingId
            }
        }

    suspend fun upsertRejected(
        bookingId: Uuid,
        type: SesCommunicationType,
        operation: SesCommunicationOperation,
        error: String,
    ): Uuid = upsertError(bookingId, type, operation, SesCommunicationStatus.Rejected, error)

    suspend fun upsertFailed(
        bookingId: Uuid,
        type: SesCommunicationType,
        operation: SesCommunicationOperation,
        error: String,
    ): Uuid = upsertError(bookingId, type, operation, SesCommunicationStatus.Failed, error)

    private suspend fun upsertError(
        bookingId: Uuid,
        type: SesCommunicationType,
        operation: SesCommunicationOperation,
        newStatus: SesCommunicationStatus,
        error: String,
    ): Uuid =
        dbQuery {
            val now = OffsetDateTime.now()
            val truncatedError = error.take(1000)
            val existingId = findId(bookingId, type, operation)
            if (existingId == null) {
                val result =
                    SesCommunicationsTable.insert {
                        it[SesCommunicationsTable.bookingId] = bookingId
                        it[communicationType] = type
                        it[SesCommunicationsTable.operation] = operation
                        it[status] = newStatus
                        it[lastAttemptAt] = now
                        it[lastError] = truncatedError
                        it[createdAt] = now
                        it[updatedAt] = now
                    }
                result[SesCommunicationsTable.id]
            } else {
                SesCommunicationsTable.update({ SesCommunicationsTable.id eq existingId }) {
                    it[status] = newStatus
                    it[lastAttemptAt] = now
                    it[lastError] = truncatedError
                    it[updatedAt] = now
                }
                existingId
            }
        }

    /**
     * The single discovery query that drives all anulaciones: accepted altas whose booking is
     * cancelled and which have **no active anulación** yet for that `(booking, type)`. An *active*
     * anulación is one in `submitted/processing/accepted/rejected` — a `failed` anulación is treated
     * as absent so the next tick retries it; a `rejected` one is terminal and is not retried.
     */
    suspend fun acceptedAltasForCancelledBookingsWithoutAnulacion(limit: Int): List<SesCommunication> =
        dbQuery {
            val candidates =
                SesCommunicationsTable
                    .innerJoin(BookingsTable, { SesCommunicationsTable.bookingId }, { BookingsTable.id })
                    .innerJoin(PropertiesTable, { BookingsTable.propertyId }, { PropertiesTable.id })
                    .selectAll()
                    .where {
                        (SesCommunicationsTable.operation eq SesCommunicationOperation.Alta) and
                            (SesCommunicationsTable.status eq SesCommunicationStatus.Accepted) and
                            (BookingsTable.status eq BookingStatus.Cancelled) and
                            // Strict SES submission gate (assignment + enabled + unflagged
                            // integration + unflagged property config) — shared, see
                            // SesCredentialsErrorGate.kt: an anulación through a flagged
                            // integration or property would fail with the same error.
                            sesPropertySubmittable()
                    }.map { SesCommunication.from(it) }

            if (candidates.isEmpty()) {
                emptyList()
            } else {
                val activeAnulacionKeys =
                    SesCommunicationsTable
                        .selectAll()
                        .where {
                            (SesCommunicationsTable.operation eq SesCommunicationOperation.Anulacion) and
                                (
                                    SesCommunicationsTable.status inList
                                        listOf(
                                            SesCommunicationStatus.Submitted,
                                            SesCommunicationStatus.Processing,
                                            SesCommunicationStatus.Accepted,
                                            SesCommunicationStatus.Rejected,
                                        )
                                ) and
                                (SesCommunicationsTable.bookingId inList candidates.map { it.bookingId })
                        }.map { it[SesCommunicationsTable.bookingId] to it[SesCommunicationsTable.communicationType] }
                        .toSet()

                candidates
                    .filter { (it.bookingId to it.communicationType) !in activeAnulacionKeys }
                    .take(limit)
            }
        }

    suspend fun listPendingPoll(limit: Int): List<SesCommunication> =
        dbQuery {
            SesCommunicationsTable
                .innerJoin(BookingsTable, { SesCommunicationsTable.bookingId }, { BookingsTable.id })
                .innerJoin(PropertiesTable, { BookingsTable.propertyId }, { PropertiesTable.id })
                .selectAll()
                .where {
                    (SesCommunicationsTable.lote.isNotNull()) and
                        // Integration-level gate only (assignment + enabled + unflagged
                        // integration) — shared, see SesCredentialsErrorGate.kt. Credential-gated
                        // comms stay pending and return to the poll set once the pause lifts.
                        // Deliberately NOT sesPropertySubmittable(): a property-config flag pauses
                        // new submissions but polls must continue, so the property's in-flight
                        // lotes reach a terminal estado — otherwise a stale lote polled after the
                        // code was fixed would re-flag the property with its old error.
                        sesPropertyLinkedToUsableIntegration() and
                        (
                            SesCommunicationsTable.estado.isNull() or
                                (
                                    SesCommunicationsTable.estado inList
                                        listOf(SesCodigoEstado.EnProceso_4, SesCodigoEstado.Pendiente_5)
                                ) or
                                // Accepted altas whose per-communication código hasn't been captured yet
                                // (SES only returns it via consultaLote) — re-poll once to grab it so the
                                // alta becomes cancellable. Drops out as soon as the código is stored.
                                (
                                    (SesCommunicationsTable.operation eq SesCommunicationOperation.Alta) and
                                        (SesCommunicationsTable.status eq SesCommunicationStatus.Accepted) and
                                        SesCommunicationsTable.codigoComunicacion.isNull()
                                )
                        )
                }.limit(limit)
                .map { SesCommunication.from(it) }
        }

    suspend fun markPolled(
        id: Uuid,
        estado: SesCodigoEstado,
        estadoDesc: String?,
        lastError: String? = null,
        codigoComunicacion: String? = null,
        // Overrides the estado-derived status. Used for property-config errors (wrong establishment
        // code): the booking data is fine, so the comm lands as `failed` (retry-eligible, re-picked
        // automatically once the property flag clears) instead of `rejected` (gated on a booking
        // edit that would never come — the fix is on the property).
        statusOverride: SesCommunicationStatus? = null,
    ) {
        val newStatus =
            statusOverride
                ?: when {
                    estado.isSuccess() -> SesCommunicationStatus.Accepted
                    !estado.isTerminal() -> SesCommunicationStatus.Processing
                    else -> SesCommunicationStatus.Rejected
                }
        dbQuery {
            val now = OffsetDateTime.now()
            SesCommunicationsTable.update({ SesCommunicationsTable.id eq id }) {
                it[status] = newStatus
                it[SesCommunicationsTable.estado] = estado
                it[SesCommunicationsTable.estadoDesc] = estadoDesc
                // Mirror the latest poll's per-communication errors (codigoEstado 6) into last_error;
                // null clears any stale error when a later poll succeeds.
                it[SesCommunicationsTable.lastError] = lastError?.take(1000)
                // SES returns the per-communication código only via consultaLote — capture it here so
                // the alta can be cancelled later. Only set when present; never clear an existing one.
                if (codigoComunicacion != null) {
                    it[SesCommunicationsTable.codigoComunicacion] = codigoComunicacion
                }
                it[lastAttemptAt] = now
                it[updatedAt] = now
            }
        }
    }

    // Runs the lookup inside the caller's dbQuery transaction; not suspend so it
    // shares the surrounding transaction rather than opening a new one.
    private fun findId(
        bookingId: Uuid,
        type: SesCommunicationType,
        operation: SesCommunicationOperation,
    ): Uuid? =
        SesCommunicationsTable
            .selectAll()
            .where {
                (SesCommunicationsTable.bookingId eq bookingId) and
                    (SesCommunicationsTable.communicationType eq type) and
                    (SesCommunicationsTable.operation eq operation)
            }.map { it[SesCommunicationsTable.id] }
            .firstOrNull()
}
