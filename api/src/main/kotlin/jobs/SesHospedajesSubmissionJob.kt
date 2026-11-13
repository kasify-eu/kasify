package eu.kasify.jobs

import eu.kasify.db.BookingsRepository
import eu.kasify.db.GeoRepository
import eu.kasify.db.GuestsRepository
import eu.kasify.db.IntegrationsRepository
import eu.kasify.db.PropertiesRepository
import eu.kasify.db.SesCommunicationsRepository
import eu.kasify.db.SesHospedajesSubmissionsRepository
import eu.kasify.model.SesCommunicationType
import eu.kasify.security.EncryptionService
import eu.kasify.services.SesHospedajesService
import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.oshai.kotlinlogging.withLoggingContext
import io.sentry.Sentry
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.slf4j.MDCContext
import org.quartz.Job
import org.quartz.JobExecutionContext
import kotlin.uuid.Uuid

private val logger = KotlinLogging.logger {}

class SesHospedajesSubmissionJob(
    private val bookingsRepository: BookingsRepository,
    private val communicationsRepository: SesCommunicationsRepository,
    private val sesService: SesHospedajesService,
) : Job {
    // Quartz instantiates jobs via the no-arg constructor; production deps are wired here.
    // The private secondary constructor lets us share a single PropertiesRepository between
    // the SES service's dependency tree and the IntegrationsRepository it owns.
    constructor() : this(PropertiesRepository())

    private constructor(propertiesRepository: PropertiesRepository) : this(
        bookingsRepository = BookingsRepository(),
        communicationsRepository = SesCommunicationsRepository(),
        sesService =
            SesHospedajesService(
                integrationsRepository = IntegrationsRepository(propertiesRepository, EncryptionService()),
                geoRepository = GeoRepository(),
                bookingsRepository = BookingsRepository(),
                propertiesRepository = propertiesRepository,
                guestsRepository = GuestsRepository(),
                submissionsRepository = SesHospedajesSubmissionsRepository(),
                communicationsRepository = SesCommunicationsRepository(),
            ),
    )

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
        // (a) PV altas — always run.
        val pvPending = bookingsRepository.getBookingsPendingPvAlta()
        logger.info { "SesHospedajesSubmissionJob: ${pvPending.size} PV alta candidate(s)" }
        pvPending.forEach { booking ->
            withLoggingContext("booking_id" to booking.id.toString()) {
                submitAlta(booking.id, SesCommunicationType.PV)
            }
        }

        // (b) RH altas — gated behind a runtime flag (default off) so the rollout can be flipped
        // without a redeploy. The gate only governs *new* RH altas; anulaciones below always run.
        if (rhAutoSubmitEnabled()) {
            val rhPending = bookingsRepository.getBookingsPendingRhAlta()
            logger.info { "SesHospedajesSubmissionJob: ${rhPending.size} RH alta candidate(s)" }
            rhPending.forEach { booking ->
                withLoggingContext("booking_id" to booking.id.toString()) {
                    submitAlta(booking.id, SesCommunicationType.RH)
                }
            }
        } else {
            val rhCandidates = bookingsRepository.getBookingsPendingRhAlta().size
            logger.info { "RH auto-submit disabled; skipping $rhCandidates candidate(s)" }
        }

        // (c) anulaciones — always run. Discovery yields both PV and RH accepted altas of cancelled
        // bookings without an active anulación, each as a separate item, so a PV failure can't block RH.
        val toCancel = communicationsRepository.acceptedAltasForCancelledBookingsWithoutAnulacion(ANULACION_BATCH_SIZE)
        logger.info { "SesHospedajesSubmissionJob: ${toCancel.size} anulación candidate(s)" }
        toCancel.forEach { alta ->
            withLoggingContext("booking_id" to alta.bookingId.toString()) {
                runItem("${alta.communicationType} anulación", alta.bookingId) {
                    sesService.submitCancellation(alta.id, honorScopedPauses = true)
                }
            }
        }
    }

    private suspend fun submitAlta(
        bookingId: Uuid,
        type: SesCommunicationType,
    ) = runItem("$type alta", bookingId) { sesService.submitAlta(bookingId, type, honorScopedPauses = true) }

    // Shared per-item orchestration: identical Sentry / SesPostSuccessPersistenceException / catch-all
    // handling for altas and anulaciones, so one bad item never aborts the remaining work.
    private suspend fun runItem(
        label: String,
        bookingId: Uuid,
        action: suspend () -> SesHospedajesService.ManualSesSubmitResult,
    ) {
        try {
            when (val result = action()) {
                is SesHospedajesService.ManualSesSubmitResult.Submitted -> Unit
                is SesHospedajesService.ManualSesSubmitResult.RejectedBySes ->
                    Sentry.captureMessage(
                        "SES $label rejected for booking $bookingId: code=${result.codigo} desc=${result.descripcion}",
                    )
                // Credential-family error: the guard has flagged the integration (pausing discovery
                // for its assigned properties) and Sentry/owner-email fired once on the transition,
                // so no per-booking capture here — a broken credential would otherwise spam one
                // event per pending booking in the tick that discovered it.
                is SesHospedajesService.ManualSesSubmitResult.CredentialsError ->
                    logger.warn {
                        "SES $label for booking $bookingId hit a credential error " +
                            "(code=${result.codigo}); the integration's auto-submissions are paused"
                    }
                // Property-config error: same one-time-notification semantics as CredentialsError,
                // scoped to the property — the guard flagged it and fired Sentry/owner-email on the
                // transition, so no per-booking capture here.
                is SesHospedajesService.ManualSesSubmitResult.PropertyConfigError ->
                    logger.warn {
                        "SES $label for booking $bookingId hit a property-config error " +
                            "(${result.descripcion}); the property's auto-submissions are paused"
                    }
                is SesHospedajesService.ManualSesSubmitResult.UpstreamError ->
                    Sentry.captureException(result.cause)
            }
        } catch (e: SesHospedajesService.SesPostSuccessPersistenceException) {
            // SES has filed, but post-success persistence retries exhausted — the service has
            // already Sentry-captured this with structured tags. Swallow so the loop continues.
            logger.error(e) {
                "Booking $bookingId ($label) left inconsistent (lote=${e.lote}); operator intervention " +
                    "required before next tick to avoid a duplicate SES filing."
            }
        } catch (e: Exception) {
            // Catch-all so a single problem booking (missing property, ineligibility, unexpected
            // error) never aborts the loop and skips the remaining items.
            logger.warn(e) { "Booking $bookingId ($label) skipped: ${e.message}" }
            Sentry.captureException(e)
        }
    }

    private fun rhAutoSubmitEnabled(): Boolean = System.getenv("SES_RH_AUTO_SUBMIT_ENABLED")?.toBoolean() == true

    private companion object {
        const val ANULACION_BATCH_SIZE = 100
    }
}
