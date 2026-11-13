package eu.kasify.db

import eu.kasify.db.model.BookingChannel
import eu.kasify.db.model.GuestRegistryEntriesTable
import eu.kasify.db.model.SesCodigoEstado
import eu.kasify.model.GuestRegistryEntry
import eu.kasify.model.GuestRegistrySnapshot
import eu.kasify.model.SesCommunicationType
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.jdbc.update
import java.time.LocalDate
import java.time.OffsetDateTime
import kotlin.uuid.Uuid

/**
 * Everything needed to write one libro-registro entry. `entryNumber` is deliberately absent —
 * the repository assigns it (correlative per property) inside the insert transaction.
 */
data class NewGuestRegistryEntry(
    val accountId: Uuid,
    val propertyId: Uuid,
    val bookingId: Uuid,
    val communicationType: SesCommunicationType,
    val sesArrendadorCode: String,
    val arrendadorNif: String?,
    val sesEstablishmentCode: String,
    val propertyName: String,
    val propertyAddress: String,
    val postalCode: String?,
    val city: String?,
    val province: String?,
    val contractReference: String,
    val contractDate: LocalDate,
    val bookingChannel: BookingChannel,
    val checkinDate: LocalDate,
    val checkoutDate: LocalDate,
    val numGuests: Int,
    val snapshot: GuestRegistrySnapshot,
    val requestXml: String,
    val sesLote: String?,
    val codigoComunicacion: String?,
    val submittedAt: OffsetDateTime,
)

class GuestRegistryRepository {
    /**
     * Append one entry to the libro registro, assigning the next correlative `entry_number` for the
     * property. Idempotent per `(booking, communication_type, ses_lote)`: the lote identifies one
     * SES submission, so a retried persistence step after a partial failure (same lote) writes
     * nothing and returns `null`, while a re-filed alta (a new submission with a fresh lote, e.g.
     * after the first was rejected on poll and the data corrected) appends a new entry.
     *
     * Entries are immutable once written — there is deliberately no update path for the snapshot.
     */
    suspend fun record(entry: NewGuestRegistryEntry): Uuid? =
        dbQuery {
            // Serialize concurrent inserts for the same property so max+1 below cannot race.
            // Transaction-scoped lock: released automatically at commit/rollback.
            TransactionManager
                .current()
                .exec("SELECT pg_advisory_xact_lock(hashtextextended('guest_registry:${entry.propertyId}', 0))")

            val alreadyRecorded =
                GuestRegistryEntriesTable
                    .selectAll()
                    .where {
                        (GuestRegistryEntriesTable.bookingId eq entry.bookingId) and
                            (GuestRegistryEntriesTable.communicationType eq entry.communicationType) and
                            (GuestRegistryEntriesTable.sesLote eq entry.sesLote)
                    }.limit(1)
                    .any()
            if (alreadyRecorded) return@dbQuery null

            val nextNumber =
                GuestRegistryEntriesTable
                    .selectAll()
                    .where { GuestRegistryEntriesTable.propertyId eq entry.propertyId }
                    .orderBy(GuestRegistryEntriesTable.entryNumber, SortOrder.DESC)
                    .limit(1)
                    .firstOrNull()
                    ?.get(GuestRegistryEntriesTable.entryNumber)
                    ?.plus(1) ?: 1L

            val result =
                GuestRegistryEntriesTable.insert {
                    it[accountId] = entry.accountId
                    it[propertyId] = entry.propertyId
                    it[bookingId] = entry.bookingId
                    it[entryNumber] = nextNumber
                    it[communicationType] = entry.communicationType
                    it[sesArrendadorCode] = entry.sesArrendadorCode
                    it[arrendadorNif] = entry.arrendadorNif
                    it[sesEstablishmentCode] = entry.sesEstablishmentCode
                    it[propertyName] = entry.propertyName
                    it[propertyAddress] = entry.propertyAddress
                    it[postalCode] = entry.postalCode
                    it[city] = entry.city
                    it[province] = entry.province
                    it[contractReference] = entry.contractReference
                    it[contractDate] = entry.contractDate
                    it[bookingChannel] = entry.bookingChannel
                    it[checkinDate] = entry.checkinDate
                    it[checkoutDate] = entry.checkoutDate
                    it[numGuests] = entry.numGuests
                    it[snapshot] = entry.snapshot
                    it[requestXml] = entry.requestXml
                    it[sesLote] = entry.sesLote
                    it[codigoComunicacion] = entry.codigoComunicacion
                    it[sesEstado] = null
                    it[submittedAt] = entry.submittedAt
                    it[createdAt] = OffsetDateTime.now()
                }
            result[GuestRegistryEntriesTable.id]
        }

    /**
     * Mirror the final consultaLote outcome (and the per-communication código, when it only became
     * known via polling) onto the entry, so the registro proves tramitación on its own. Matched by
     * lote so a poll only ever touches the entry of the submission it queried — never an earlier
     * re-filed alta's entry for the same booking. The parte content itself is never touched.
     */
    suspend fun updateEstado(
        bookingId: Uuid,
        type: SesCommunicationType,
        sesLote: String,
        estado: SesCodigoEstado,
        codigoComunicacion: String? = null,
    ): Boolean =
        dbQuery {
            GuestRegistryEntriesTable.update({
                (GuestRegistryEntriesTable.bookingId eq bookingId) and
                    (GuestRegistryEntriesTable.communicationType eq type) and
                    (GuestRegistryEntriesTable.sesLote eq sesLote)
            }) {
                it[sesEstado] = estado
                if (codigoComunicacion != null) {
                    it[GuestRegistryEntriesTable.codigoComunicacion] = codigoComunicacion
                }
            } > 0
        }

    /** Latest entry for the booking × type — a re-filed alta appends a new entry per submission. */
    suspend fun getByBookingAndType(
        bookingId: Uuid,
        type: SesCommunicationType,
    ): GuestRegistryEntry? =
        dbQuery {
            GuestRegistryEntriesTable
                .selectAll()
                .where {
                    (GuestRegistryEntriesTable.bookingId eq bookingId) and
                        (GuestRegistryEntriesTable.communicationType eq type)
                }.orderBy(GuestRegistryEntriesTable.entryNumber, SortOrder.DESC)
                .limit(1)
                .firstOrNull()
                ?.let { GuestRegistryEntry.from(it) }
        }

    /** Entries for a property, optionally bounded by check-in date, in libro order (entry_number). */
    suspend fun listByProperty(
        propertyId: Uuid,
        checkinFrom: LocalDate? = null,
        checkinTo: LocalDate? = null,
    ): List<GuestRegistryEntry> =
        dbQuery {
            GuestRegistryEntriesTable
                .selectAll()
                .where {
                    var predicate = GuestRegistryEntriesTable.propertyId eq propertyId
                    checkinFrom?.let { from -> predicate = predicate and (GuestRegistryEntriesTable.checkinDate greaterEq from) }
                    checkinTo?.let { to -> predicate = predicate and (GuestRegistryEntriesTable.checkinDate lessEq to) }
                    predicate
                }.orderBy(GuestRegistryEntriesTable.entryNumber, SortOrder.ASC)
                .map { GuestRegistryEntry.from(it) }
        }
}
