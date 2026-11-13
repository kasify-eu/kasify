package eu.kasify.jobs

import eu.kasify.db.BookingsRepository
import eu.kasify.db.GuestRegistryRepository
import eu.kasify.db.IntegrationsRepository
import eu.kasify.db.PropertiesRepository
import eu.kasify.db.SesCommunicationsRepository
import eu.kasify.db.SesHospedajesSubmissionsRepository
import eu.kasify.db.model.SesCodigoEstado
import eu.kasify.model.Property
import eu.kasify.model.SesCommunication
import eu.kasify.model.SesCommunicationOperation
import eu.kasify.model.SesCommunicationStatus
import eu.kasify.model.SesHospedajesIntegration
import eu.kasify.plugins.ses.SesBatchInfo
import eu.kasify.plugins.ses.SesHospedajesPlugin
import eu.kasify.plugins.ses.perCommunicationError
import eu.kasify.plugins.ses.singleCommunicationCodigo
import eu.kasify.security.EncryptionService
import eu.kasify.services.SesBookingRejectedNotifier
import eu.kasify.services.SesCredentialsGuard
import eu.kasify.services.SesFailureClassifier
import eu.kasify.services.SesPropertyConfigGuard
import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.oshai.kotlinlogging.withLoggingContext
import io.ktor.client.HttpClient
import io.sentry.Sentry
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.slf4j.MDCContext
import org.quartz.Job
import org.quartz.JobExecutionContext
import kotlin.uuid.Uuid

private val logger = KotlinLogging.logger {}

/**
 * Polls SES Hospedajes for the batch status (codigoEstado 1-6) of `ses_communication` rows that
 * have a lote but have not yet reached a terminal estado. Operation-blind: an alta or an anulación,
 * PV or RH, all take the same path. Each poll inserts a `query` row in `ses_hospedajes_submissions`
 * (linked via `ses_communication_id`) and updates the comm row's status/estado via [markPolled].
 *
 * Polling continues for non-terminal states (`4_en_proceso`, `5_pendiente`); terminal states take
 * the row out of the [SesCommunicationsRepository.listPendingPoll] index.
 */
class SesHospedajesPollJob(
    private val bookingsRepository: BookingsRepository = BookingsRepository(),
    private val propertiesRepository: PropertiesRepository = PropertiesRepository(),
    private val integrationsRepository: IntegrationsRepository =
        IntegrationsRepository(propertiesRepository, EncryptionService()),
    private val submissionsRepository: SesHospedajesSubmissionsRepository = SesHospedajesSubmissionsRepository(),
    private val communicationsRepository: SesCommunicationsRepository = SesCommunicationsRepository(),
    private val guestRegistryRepository: GuestRegistryRepository = GuestRegistryRepository(),
    // Optional HTTP client override; tests pass a Ktor MockEngine-backed client to avoid real network calls.
    private val httpClient: HttpClient? = null,
    // Flags the integration when consultaLote reports a credential-family error (same WS credentials
    // as submissions) and fires the one-time owner email.
    private val credentialsGuard: SesCredentialsGuard = SesCredentialsGuard(integrationsRepository),
    // Flags the property when a polled estado reports an unknown establishment code (and clears the
    // flag when a later estado for the property succeeds) — see SesPropertyConfigGuard.
    private val propertyConfigGuard: SesPropertyConfigGuard = SesPropertyConfigGuard(propertiesRepository),
    // Notifies the owners when a polled estado 6 rejects a booking's data — see SesBookingRejectedNotifier.
    private val bookingRejectedNotifier: SesBookingRejectedNotifier = SesBookingRejectedNotifier(),
) : Job {
    override fun execute(context: JobExecutionContext?) {
        val fireId = context?.fireInstanceId ?: "unknown"
        // MDCContext propagates SLF4J MDC across coroutine suspensions (e.g. dbQuery's
        // thread switches), so withLoggingContext entries below survive every dispatcher hop.
        runBlocking(MDCContext()) {
            withLoggingContext("fire_id" to fireId) {
                runOnce()
            }
        }
    }

    internal suspend fun runOnce() {
        val comms = communicationsRepository.listPendingPoll(POLL_BATCH_SIZE)
        if (comms.isEmpty()) {
            logger.debug { "SesHospedajesPollJob: no communications to poll" }
            return
        }
        logger.info { "SesHospedajesPollJob: polling ${comms.size} communication(s)" }

        groupByIntegration(comms).forEach { (group, commsForIntegration) ->
            pollIntegration(group, commsForIntegration)
        }
    }

    /** Grouping key: the integration assigned to the comm's property, plus the owning account. */
    private data class IntegrationGroup(
        val accountId: Uuid,
        val integrationId: Uuid,
    )

    /** A pollable comm with its resolved property — the property is needed to route estado errors by scope. */
    private data class PolledComm(
        val comm: SesCommunication,
        val property: Property,
    )

    // listPendingPoll returns bare comm rows (only booking_id), so resolve booking → property →
    // assigned integration here to build the per-integration plugin. The strict participation
    // gate (assignment + enabled + unflagged integration) is already applied in SQL by
    // listPendingPoll, so an unassigned property here can only be a race with a concurrent
    // unassignment. Properties are cached per tick.
    private suspend fun groupByIntegration(comms: List<SesCommunication>): Map<IntegrationGroup, List<PolledComm>> {
        val propertiesById = mutableMapOf<Uuid, Property?>()
        val result = mutableMapOf<IntegrationGroup, MutableList<PolledComm>>()
        comms.forEach { comm ->
            val booking = bookingsRepository.getBooking(comm.bookingId) ?: return@forEach
            val property =
                propertiesById.getOrPut(booking.propertyId) {
                    propertiesRepository.getProperty(booking.propertyId)
                } ?: return@forEach
            val integrationId = property.sesIntegrationId ?: return@forEach
            result
                .getOrPut(IntegrationGroup(property.accountId, integrationId)) { mutableListOf() }
                .add(PolledComm(comm, property))
        }
        return result
    }

    private suspend fun pollIntegration(
        group: IntegrationGroup,
        comms: List<PolledComm>,
    ) {
        // listPendingPoll already gates on assignment + enabled + unflagged (shared SQL gate, see
        // SesCredentialsErrorGate.kt); this re-check only covers a change committed between that
        // query and this load. The comms stay pending for the next tick either way.
        val integration =
            integrationsRepository
                .getSesHospedajesIntegrationById(group.accountId, group.integrationId)
                ?.takeIf { it.enabled && it.credentialsErrorCode == null }
        if (integration == null) {
            logger.info {
                "SES integration ${group.integrationId} (account ${group.accountId}) is no longer " +
                    "usable (removed, disabled, or credential-flagged); skipping ${comms.size} pending poll(s)"
            }
            return
        }

        val commsByLote = comms.associateBy { requireNotNull(it.comm.lote) }
        val plugin =
            SesHospedajesPlugin(
                sesUsername = integration.sesUsername,
                sesPassword = integration.sesPassword,
                httpClient = httpClient,
            )
        try {
            for (loteChunk in commsByLote.keys.chunked(LOTES_PER_QUERY)) {
                // A credential error dooms every remaining chunk of this integration in the same
                // way — stop instead of repeating the failing consultaLote.
                if (!pollChunk(plugin, integration, loteChunk, commsByLote)) break
            }
        } finally {
            plugin.close()
        }
    }

    /** Polls one chunk; returns false when the integration's credentials are dead (stop polling it). */
    private suspend fun pollChunk(
        plugin: SesHospedajesPlugin,
        integration: SesHospedajesIntegration,
        lotes: List<String>,
        commsByLote: Map<String, PolledComm>,
    ): Boolean {
        val response =
            try {
                plugin.consultBatch(lotes)
            } catch (e: Exception) {
                val msg = (e.message ?: e::class.simpleName ?: "Unknown error").take(1000)
                logger.error(e) { "consultBatch failed for ${lotes.size} lote(s): $msg" }
                Sentry.captureException(e)
                lotes.forEach { lote ->
                    val comm = commsByLote[lote]?.comm ?: return@forEach
                    withLoggingContext("booking_id" to comm.bookingId.toString(), "lote" to lote) {
                        runCatching {
                            submissionsRepository.recordQueryAttempt(
                                bookingId = comm.bookingId,
                                sesLote = lote,
                                respuestaCodigo = null,
                                respuestaDescripcion = null,
                                sesEstado = null,
                                sesEstadoDesc = null,
                                responseXml = null,
                                errorText = msg,
                                sesCommunicationId = comm.id,
                            )
                        }
                    }
                }
                // Transient transport failure — the next chunk may still succeed.
                return true
            }

        // consultaLote uses the same WS credentials as submissions: a credential-family Layer-1
        // código flags the integration (pausing further polls/submissions and notifying the owners
        // once). The comm rows stay pending and are re-polled after the credentials are fixed.
        if (SesCredentialsGuard.isCredentialError(response.codigo)) {
            credentialsGuard.onCredentialError(integration, response.codigo, response.descripcion)
            return false
        }

        response.resultados.forEach { batch ->
            val polled = commsByLote[batch.lote]
            if (polled == null) {
                logger.warn { "consultBatch returned unknown lote ${batch.lote}; ignoring" }
                return@forEach
            }
            withLoggingContext("booking_id" to polled.comm.bookingId.toString(), "lote" to batch.lote) {
                recordBatchResult(polled, batch, response.descripcion, response.rawXml)
            }
        }
        return true
    }

    private suspend fun recordBatchResult(
        polled: PolledComm,
        batch: SesBatchInfo,
        responseDescripcion: String,
        responseXml: String,
    ) {
        val comm = polled.comm
        val estado = SesCodigoEstado.fromCodigoOrUnknown(batch.codigoEstado)
        if (estado == SesCodigoEstado.CodigoDesconocido_99) {
            logger.warn { "Unrecognised codigoEstado=${batch.codigoEstado} for lote ${batch.lote}; storing as CodigoDesconocido" }
        }
        // codigoEstado 6 (tramitado con errores parciales) carries the actual per-communication
        // validation errors under resultadoComunicaciones; surface them so the user sees *why* it
        // failed, not just the generic lote-level descEstado.
        val perCommError = batch.perCommunicationError()
        // SES returns the per-communication código only in the consultaLote response (never in the
        // submit response), so this poll is where an accepted alta's código becomes available.
        val polledCodigo = batch.singleCommunicationCodigo()
        submissionsRepository.recordQueryAttempt(
            bookingId = comm.bookingId,
            sesLote = batch.lote,
            respuestaCodigo = 0,
            respuestaDescripcion = responseDescripcion,
            sesEstado = estado,
            sesEstadoDesc = batch.descEstado,
            responseXml = responseXml.ifBlank { null },
            errorText = perCommError,
            sesCommunicationId = comm.id,
        )
        // A terminal-error estado whose per-comm error identifies a wrong establishment code is a
        // property-scoped failure (see SesFailureClassifier): record the comm as `failed` — not
        // `rejected` — and flag the property so its auto-submissions pause and the owner is told
        // once. A successful estado proves the code works and self-heals any stale property flag.
        val isPropertyConfigError =
            estado.isTerminal() && !estado.isSuccess() && SesFailureClassifier.isPropertyConfigError(perCommError)
        communicationsRepository.markPolled(
            comm.id,
            estado,
            batch.descEstado,
            lastError = perCommError,
            codigoComunicacion = polledCodigo,
            statusOverride = if (isPropertyConfigError) SesCommunicationStatus.Failed else null,
        )
        if (isPropertyConfigError) {
            propertyConfigGuard.onConfigError(polled.property, perCommError)
        } else if (estado.isSuccess()) {
            propertyConfigGuard.onSesSuccess(polled.property)
        } else if (estado == SesCodigoEstado.TramitadoConErroresParciales_6 &&
            comm.operation == SesCommunicationOperation.Alta
        ) {
            // Estado 6 with a non-establishment error = the booking data itself was rejected
            // (comm row `rejected` above) — tell the owners which booking to fix. The other
            // terminal-error estados (2/3/99) are not owner-fixable and stay Sentry/log-only.
            notifyBookingRejected(comm, polled.property, perCommError ?: batch.descEstado)
        }
        // Mirror the polled estado (and late-arriving código) onto the alta's libro-registro entry.
        // Best-effort: the comm row is the source of truth for the polling state machine.
        if (comm.operation == SesCommunicationOperation.Alta) {
            runCatching {
                guestRegistryRepository.updateEstado(comm.bookingId, comm.communicationType, batch.lote, estado, polledCodigo)
            }.onFailure {
                logger.warn(it) { "Failed to mirror estado onto libro-registro entry for booking ${comm.bookingId}" }
            }
        }
        logger.info {
            "Communication ${comm.id} (booking ${comm.bookingId}) lote ${batch.lote}: " +
                "codigoEstado=${batch.codigoEstado} ($estado / ${batch.descEstado})" +
                (perCommError?.let { " — $it" } ?: "")
        }
    }

    /**
     * Resolve the rejected alta's booking and notify the owners. Best-effort: the comm row is
     * already `rejected` and the notifier itself escalates a lost notification, so a booking
     * lookup failure here only logs.
     */
    private suspend fun notifyBookingRejected(
        comm: SesCommunication,
        property: Property,
        errorMessage: String?,
    ) {
        runCatching {
            val booking =
                bookingsRepository.getBooking(comm.bookingId)
                    ?: error("booking ${comm.bookingId} not found")
            bookingRejectedNotifier.notifyOwners(booking, property, comm.communicationType, errorMessage)
        }.onFailure {
            logger.error(it) { "Failed to notify owners of the rejected booking ${comm.bookingId}" }
        }
    }

    private companion object {
        const val POLL_BATCH_SIZE = 100
        const val LOTES_PER_QUERY = 50
    }
}
