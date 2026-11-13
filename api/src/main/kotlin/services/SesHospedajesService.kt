package eu.kasify.services

import eu.kasify.db.BookingsRepository
import eu.kasify.db.GeoRepository
import eu.kasify.db.GuestRegistryRepository
import eu.kasify.db.GuestsRepository
import eu.kasify.db.IntegrationsRepository
import eu.kasify.db.NewGuestRegistryEntry
import eu.kasify.db.PropertiesRepository
import eu.kasify.db.SesCommunicationsRepository
import eu.kasify.db.SesHospedajesSubmissionsRepository
import eu.kasify.db.model.BookingChannel
import eu.kasify.db.model.BookingStatus
import eu.kasify.db.model.DocumentType
import eu.kasify.db.model.Gender
import eu.kasify.db.model.SesCodigoEstado
import eu.kasify.model.Booking
import eu.kasify.model.Guest
import eu.kasify.model.GuestRegistrySnapshot
import eu.kasify.model.Property
import eu.kasify.model.SesCommunication
import eu.kasify.model.SesCommunicationOperation
import eu.kasify.model.SesCommunicationStatus
import eu.kasify.model.SesCommunicationType
import eu.kasify.model.SesHospedajesIntegration
import eu.kasify.plugins.ses.SesAddress
import eu.kasify.plugins.ses.SesCancelRequest
import eu.kasify.plugins.ses.SesCommunicationResponse
import eu.kasify.plugins.ses.SesContract
import eu.kasify.plugins.ses.SesGuest
import eu.kasify.plugins.ses.SesHospedajesPlugin
import eu.kasify.plugins.ses.SesParteViajerosRequest
import eu.kasify.plugins.ses.SesReservaGuest
import eu.kasify.plugins.ses.SesReservaHospedajeRequest
import eu.kasify.plugins.ses.perCommunicationError
import eu.kasify.plugins.ses.singleCommunicationCodigo
import eu.kasify.plugins.ses.solicitudXml
import eu.kasify.plugins.ses.toSesTipoPago
import eu.kasify.routes.Errors
import eu.kasify.routes.exceptions.ConflictException
import eu.kasify.routes.exceptions.NotFoundException
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.sentry.Sentry
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import kotlin.uuid.Uuid

class SesHospedajesService(
    private val integrationsRepository: IntegrationsRepository,
    private val geoRepository: GeoRepository,
    private val bookingsRepository: BookingsRepository = BookingsRepository(),
    private val propertiesRepository: PropertiesRepository = PropertiesRepository(),
    private val guestsRepository: GuestsRepository = GuestsRepository(),
    private val submissionsRepository: SesHospedajesSubmissionsRepository = SesHospedajesSubmissionsRepository(),
    private val communicationsRepository: SesCommunicationsRepository = SesCommunicationsRepository(),
    private val guestRegistryRepository: GuestRegistryRepository = GuestRegistryRepository(),
    // Optional HTTP client override; tests pass a Ktor MockEngine-backed client to avoid real network calls.
    private val httpClient: HttpClient? = null,
    // Flags/clears the integration-level credential-error state and fires the one-time owner email.
    private val credentialsGuard: SesCredentialsGuard = SesCredentialsGuard(integrationsRepository),
    // Flags/clears the property-level config-error state (wrong establishment code) and fires the
    // one-time owner email — the property-scoped analog of [credentialsGuard].
    private val propertyConfigGuard: SesPropertyConfigGuard = SesPropertyConfigGuard(propertiesRepository),
    // Notifies the owners when SES rejects a booking's data (comm row `rejected`) — per event,
    // no pause flag; frequency is bounded by the rejected-alta retry gate.
    private val bookingRejectedNotifier: SesBookingRejectedNotifier = SesBookingRejectedNotifier(),
) {
    private val logger = KotlinLogging.logger {}

    /**
     * Outcome of a per-booking SES submission orchestrated by [submitAlta] / [submitCancellation].
     * Callers (the scheduled job, the admin manual-submit endpoint) branch on this
     * to decide what to log, what to surface, and whether to capture in Sentry.
     */
    sealed class ManualSesSubmitResult {
        data class Submitted(
            val lote: String?,
            val submissionId: Uuid,
        ) : ManualSesSubmitResult()

        data class RejectedBySes(
            val codigo: Int,
            val descripcion: String,
            val submissionId: Uuid,
        ) : ManualSesSubmitResult()

        /**
         * SES rejected the call with a credential-family código (bad WS credentials / portal
         * config — see [SesCredentialsGuard.CREDENTIAL_ERROR_CODES]). The comm row is recorded as
         * `failed` (retry-eligible), the integration is flagged, and auto-submissions for its
         * assigned properties are paused until the owner fixes the credentials. Also returned
         * (without any SES call or comm-row write) when the auto job skips an already-flagged
         * integration via `honorCredentialsPause`.
         */
        data class CredentialsError(
            val codigo: Int,
            val descripcion: String,
            val submissionId: Uuid?,
        ) : ManualSesSubmitResult()

        /**
         * SES reported the property's establishment code as unknown (see
         * [SesFailureClassifier.isPropertyConfigError]). The comm row is recorded as `failed`
         * (retry-eligible), the property is flagged, and auto-submissions for this property are
         * paused until the owner fixes the code (sibling properties keep filing). Also returned
         * (without any SES call or comm-row write) when the auto job skips an already-flagged
         * property via `honorScopedPauses`.
         */
        data class PropertyConfigError(
            val descripcion: String,
            val submissionId: Uuid?,
        ) : ManualSesSubmitResult()

        data class UpstreamError(
            val message: String,
            val cause: Throwable,
            val submissionId: Uuid?,
        ) : ManualSesSubmitResult()
    }

    /**
     * Thrown by the alta submit path when SES has already accepted a submission
     * (codigo=0, lote returned) but the post-success DB writes failed even after
     * retries. The communication is in an inconsistent state: the parte has been
     * filed with the Spanish registry, but no `accepted`/`submitted` comm row was
     * persisted, so the submission job will re-pick the booking on the next tick
     * and re-POST — producing a duplicate filing. Callers MUST escalate (Sentry,
     * page) so an operator can intervene before that tick.
     */
    class SesPostSuccessPersistenceException(
        val bookingId: Uuid,
        val lote: String?,
        cause: Throwable,
    ) : RuntimeException(
            "SES accepted booking $bookingId (lote=$lote) but DB persistence failed",
            cause,
        )

    /**
     * Outcome of the eligibility checks ([checkPvEligibility] / [checkRhEligibility]).
     * `Ineligible.code` is one of the `ses_hospedajes.not_eligible.*` error codes the admin
     * endpoint reports back as a 409.
     */
    sealed class SesEligibility {
        object Eligible : SesEligibility()

        data class Ineligible(
            val code: String,
            val message: String,
        ) : SesEligibility()
    }

    /**
     * Build the absolute scheduled check-in instant. `booking.checkinTime` is a
     * wall-clock [java.time.LocalTime] already in the property's timezone
     * (set in `Booking.fromResultRow` via `atZoneSameInstant(propertyZoneId)`), so
     * we must apply the same zone to get back to a real instant. Falls back to UTC
     * when the property is missing or its tzone is unparseable — callers treat
     * `null` as "skip the grace check."
     */
    private fun scheduledCheckinAt(
        booking: Booking,
        property: Property?,
    ): OffsetDateTime? {
        val zoneId =
            property?.tzone?.let {
                runCatching { java.time.ZoneId.of(it) }.getOrNull()
            } ?: java.time.ZoneOffset.UTC
        return runCatching {
            booking.from
                .atTime(booking.checkinTime)
                .atZone(zoneId)
                .toOffsetDateTime()
        }.getOrNull()
    }

    private suspend fun mapGuest(guest: Guest): SesGuest {
        val nationalityAlpha3 =
            guest.nationalityCountryId?.let { countryId ->
                geoRepository.getCountryById(countryId)?.alpha3
            }

        val residenceCountry =
            guest.residenceCountryId?.let { countryId ->
                geoRepository.getCountryById(countryId)
            }

        val residenceCity =
            guest.residenceCityId?.let { cityId ->
                geoRepository.getCityById(cityId)
            }

        val isSpain = residenceCountry?.alpha3?.equals("ESP", ignoreCase = true) == true

        val address =
            SesAddress(
                direccion = guest.residenceAddress ?: "No disponible",
                direccionComplementaria = null,
                codigoMunicipio = if (isSpain) residenceCity?.ineCode else null,
                nombreMunicipio = if (!isSpain) (residenceCity?.name ?: guest.residenceCity) else null,
                codigoPostal = guest.residencePostalCode ?: "00000",
                pais = residenceCountry?.alpha3 ?: "ESP",
            )

        val phone =
            if (guest.phone != null) {
                val prefix = guest.phonePrefix ?: ""
                "$prefix${guest.phone}".trim()
            } else {
                null
            }

        return SesGuest(
            rol = "VI",
            nombre = guest.firstName,
            apellido1 = guest.lastName,
            apellido2 = guest.secondLastName,
            tipoDocumento = mapDocumentType(guest.documentType),
            numeroDocumento = guest.documentNumber,
            soporteDocumento = guest.documentSupportNumber,
            fechaNacimiento = guest.dateOfBirth ?: LocalDate.of(1900, 1, 1),
            nacionalidad = nationalityAlpha3,
            sexo = mapGender(guest.sex),
            direccion = address,
            telefono = phone,
            correo = guest.email,
            parentesco = guest.relationshipCode,
        )
    }

    // =====================================================================================
    // ses_communication-backed orchestration (PV + RH unified; alta + anulación).
    // These are the paths the jobs and admin endpoints use; the column-based methods above
    // are legacy and removed in the cleanup phase.
    // =====================================================================================

    /**
     * Submit a single alta (PV or RH) for a booking, persisting the outcome to `ses_communication`.
     * Eligibility is the single gate for both the auto job and the admin submit endpoints — neither
     * bypasses it. Ineligible requests throw [ConflictException] (a `ses_hospedajes.*` code → 409)
     * and write no rows.
     */
    suspend fun submitAlta(
        bookingId: Uuid,
        type: SesCommunicationType,
        // True for the auto job: skip (without a SOAP call) when the integration is already flagged
        // with a credential error or the property with a config error, so the tick that discovers
        // an outage doesn't keep hammering SES for its remaining bookings. Manual admin submits
        // pass false — they are the unpaused way to test a freshly fixed configuration.
        honorScopedPauses: Boolean = false,
    ): ManualSesSubmitResult {
        val booking =
            bookingsRepository.getBooking(bookingId)
                ?: throw NotFoundException("Booking $bookingId not found", code = "booking.not_found")
        val property =
            propertiesRepository.getProperty(booking.propertyId)
                ?: throw NotFoundException(
                    "Property ${booking.propertyId} not found for booking $bookingId",
                    code = "property.not_found",
                )
        val integration = resolveIntegrationForProperty(property)
        val guests = guestsRepository.getAllGuestsByBooking(booking.id)
        val existingAlta = communicationsRepository.getByKey(bookingId, type, SesCommunicationOperation.Alta)

        val eligibility =
            when (type) {
                SesCommunicationType.PV ->
                    checkPvEligibility(booking, property, integration, guests.size, existingAlta)
                SesCommunicationType.RH ->
                    checkRhEligibility(booking, property, integration, existingAlta)
            }
        if (eligibility is SesEligibility.Ineligible) {
            throw ConflictException(message = eligibility.message, code = eligibility.code)
        }

        // Eligibility passed → the integration is non-null (commonAltaGuards rejects both the
        // unassigned and the missing/disabled case) and the establishment code is present.
        val activeIntegration = requireNotNull(integration) { "eligibility guarantees a resolved integration" }

        pausedCredentialsResult(activeIntegration, honorScopedPauses)?.let { paused ->
            logger.info {
                "Skipping $type alta for booking ${booking.id}: integration ${activeIntegration.id} " +
                    "has a credential error (code=${paused.codigo})"
            }
            return paused
        }
        pausedPropertyConfigResult(property, honorScopedPauses)?.let { paused ->
            logger.info {
                "Skipping $type alta for booking ${booking.id}: property ${property.id} " +
                    "has a config error (${paused.descripcion})"
            }
            return paused
        }

        // The payload (request + exact solicitud XML + frozen snapshot) is built inside the same
        // try as the SOAP call: payload construction does DB lookups (guest geo mapping) and can
        // fail too, and every failure must land as a recorded 'failed' attempt, not an unhandled 500.
        var builtRequestXml: String? = null
        val (payload, response) =
            try {
                val built = buildAltaPayload(type, property, booking, activeIntegration, guests)
                builtRequestXml = built.requestXml
                built to withPlugin(activeIntegration) { plugin -> built.send(plugin) }
            } catch (e: Exception) {
                return recordAltaSoapFailure(booking, type, e, builtRequestXml)
            }

        if (response.codigo == 0) {
            credentialsGuard.onSesSuccess(activeIntegration)
            return persistAltaSuccess(booking, property, activeIntegration, type, response, payload)
        }
        // Non-zero código: route by failure scope (see SesFailureClassifier) — the blocking state
        // is recorded where the fix lives (integration, property, or the booking data itself).
        return when (SesFailureClassifier.classifyRejection(response.codigo, response.descripcion)) {
            is SesFailureCause.IntegrationCredentials ->
                persistCredentialsFailure(
                    booking,
                    type,
                    SesCommunicationOperation.Alta,
                    response,
                    activeIntegration,
                    payload.requestXml,
                )
            SesFailureCause.PropertyConfig ->
                persistPropertyConfigFailure(
                    booking,
                    property,
                    type,
                    SesCommunicationOperation.Alta,
                    response,
                    payload.requestXml,
                )
            SesFailureCause.BookingData -> persistAltaRejection(booking, property, type, response, payload.requestXml)
        }
    }

    /**
     * Send the anulación (annulment) for one accepted alta, identified by its communication id, and
     * persist the outcome to the anulación comm row. Same single-phase shape as [submitAlta].
     * Ineligible (the row isn't an accepted alta with a código, or an active anulación already exists)
     * throws [ConflictException] → 409; an unknown id → 404.
     */
    suspend fun submitCancellation(
        communicationId: Uuid,
        // Same semantics as [submitAlta]'s honorScopedPauses.
        honorScopedPauses: Boolean = false,
    ): ManualSesSubmitResult {
        val alta =
            communicationsRepository.getById(communicationId)
                ?: throw NotFoundException(
                    "SES communication $communicationId not found",
                    code = Errors.CODE_SES_HOSPEDAJES_COMMUNICATION_NOT_FOUND,
                )
        val type = alta.communicationType
        val booking =
            bookingsRepository.getBooking(alta.bookingId)
                ?: throw NotFoundException("Booking ${alta.bookingId} not found", code = "booking.not_found")
        val property =
            propertiesRepository.getProperty(booking.propertyId)
                ?: throw NotFoundException(
                    "Property ${booking.propertyId} not found for booking ${alta.bookingId}",
                    code = "property.not_found",
                )
        val integration =
            resolveIntegrationForProperty(property)
                ?: throw propertyIntegrationConflict(property)

        pausedCredentialsResult(integration, honorScopedPauses)?.let { paused ->
            logger.info {
                "Skipping $type anulación for booking ${booking.id}: integration ${integration.id} " +
                    "has a credential error (code=${paused.codigo})"
            }
            return paused
        }
        pausedPropertyConfigResult(property, honorScopedPauses)?.let { paused ->
            logger.info {
                "Skipping $type anulación for booking ${booking.id}: property ${property.id} " +
                    "has a config error (${paused.descripcion})"
            }
            return paused
        }

        // Only an accepted alta with a captured código can be annulled.
        if (alta.operation != SesCommunicationOperation.Alta ||
            alta.status != SesCommunicationStatus.Accepted ||
            alta.codigoComunicacion == null
        ) {
            throw ConflictException(
                message = Errors.ERROR_SES_HOSPEDAJES_NOT_ELIGIBLE_NO_ACCEPTED_ALTA,
                code = Errors.CODE_SES_HOSPEDAJES_NOT_ELIGIBLE_NO_ACCEPTED_ALTA,
            )
        }
        val existingAnulacion = communicationsRepository.getByKey(alta.bookingId, type, SesCommunicationOperation.Anulacion)
        if (existingAnulacion != null && existingAnulacion.status in ACTIVE_ANULACION_STATUSES) {
            throw ConflictException(
                message = Errors.ERROR_SES_HOSPEDAJES_ALREADY_CANCELLED,
                code = Errors.CODE_SES_HOSPEDAJES_ALREADY_CANCELLED,
            )
        }

        val response =
            try {
                cancelSoap(type, integration, alta.codigoComunicacion)
            } catch (e: Exception) {
                return recordCancelSoapFailure(booking, type, e)
            }

        if (response.codigo == 0) {
            credentialsGuard.onSesSuccess(integration)
            return persistCancelSuccess(booking, type, alta, response)
        }
        // Same scope-routed classification as submitAlta.
        return when (SesFailureClassifier.classifyRejection(response.codigo, response.descripcion)) {
            is SesFailureCause.IntegrationCredentials ->
                persistCredentialsFailure(booking, type, SesCommunicationOperation.Anulacion, response, integration)
            SesFailureCause.PropertyConfig ->
                persistPropertyConfigFailure(booking, property, type, SesCommunicationOperation.Anulacion, response)
            SesFailureCause.BookingData -> persistCancelRejection(booking, type, response)
        }
    }

    /**
     * Manually re-poll a single communication via consultaLote (the admin "refresh" action),
     * updating its status/estado/last_error and capturing the codigo_comunicacion. Same per-batch
     * recording as the scheduled poll job; SOAP failures are recorded to the wire log and swallowed
     * so a refresh never 500s. A communication without a lote (never submitted) is a no-op.
     * Returns the owning booking id so callers can refresh their view.
     */
    suspend fun pollCommunication(communicationId: Uuid): Uuid {
        val comm =
            communicationsRepository.getById(communicationId)
                ?: throw NotFoundException(
                    "SES communication $communicationId not found",
                    code = Errors.CODE_SES_HOSPEDAJES_COMMUNICATION_NOT_FOUND,
                )
        val lote = comm.lote ?: return comm.bookingId
        val booking =
            bookingsRepository.getBooking(comm.bookingId)
                ?: throw NotFoundException("Booking ${comm.bookingId} not found", code = "booking.not_found")
        val property =
            propertiesRepository.getProperty(booking.propertyId)
                ?: throw NotFoundException(
                    "Property ${booking.propertyId} not found for booking ${comm.bookingId}",
                    code = "property.not_found",
                )
        val integration =
            resolveIntegrationForProperty(property)
                ?: throw propertyIntegrationConflict(property)

        withPlugin(integration) { plugin ->
            val response =
                try {
                    plugin.consultBatch(listOf(lote))
                } catch (e: Exception) {
                    val msg = (e.message ?: e::class.simpleName ?: "Unknown error").take(1000)
                    logger.error(e) { "Manual consultBatch failed for communication ${comm.id}: $msg" }
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
                    return@withPlugin
                }

            // consultaLote uses the same WS credentials as submissions: a credential-family Layer-1
            // código flags the integration; any successful response self-heals a stale flag.
            if (SesCredentialsGuard.isCredentialError(response.codigo)) {
                credentialsGuard.onCredentialError(integration, response.codigo, response.descripcion)
            } else if (response.codigo == 0) {
                credentialsGuard.onSesSuccess(integration)
            }

            val batch = response.resultados.firstOrNull { it.lote == lote } ?: return@withPlugin
            val estado = SesCodigoEstado.fromCodigoOrUnknown(batch.codigoEstado)
            val perCommError = batch.perCommunicationError()
            val polledCodigo = batch.singleCommunicationCodigo()
            submissionsRepository.recordQueryAttempt(
                bookingId = comm.bookingId,
                sesLote = batch.lote,
                respuestaCodigo = 0,
                respuestaDescripcion = response.descripcion,
                sesEstado = estado,
                sesEstadoDesc = batch.descEstado,
                responseXml = response.rawXml.ifBlank { null },
                errorText = perCommError,
                sesCommunicationId = comm.id,
            )
            // A terminal-error estado whose per-comm error identifies a wrong establishment code is
            // a property-scoped failure: record the comm as `failed` (not `rejected`) and flag the
            // property so its auto-submissions pause. A successful estado proves the code works and
            // self-heals any stale property flag. Same routing as the scheduled poll job.
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
                propertyConfigGuard.onConfigError(property, perCommError)
            } else if (estado.isSuccess()) {
                propertyConfigGuard.onSesSuccess(property)
            }
            mirrorEstadoToRegistry(comm, lote, estado, polledCodigo)
        }
        return comm.bookingId
    }

    /**
     * Mirror a polled estado (and late-arriving código) onto the alta's libro-registro entry so the
     * registro proves tramitación on its own. Best-effort: the comm row is the source of truth for
     * the polling state machine, so a failure here only logs.
     */
    private suspend fun mirrorEstadoToRegistry(
        comm: SesCommunication,
        lote: String,
        estado: SesCodigoEstado,
        codigoComunicacion: String?,
    ) {
        if (comm.operation != SesCommunicationOperation.Alta) return
        runCatching {
            guestRegistryRepository.updateEstado(comm.bookingId, comm.communicationType, lote, estado, codigoComunicacion)
        }.onFailure {
            logger.warn(it) { "Failed to mirror estado onto libro-registro entry for booking ${comm.bookingId}" }
        }
    }

    // --- Eligibility (pure; reads the already-loaded comm row, not the DB) -------------------

    fun checkPvEligibility(
        booking: Booking,
        property: Property?,
        integration: SesHospedajesIntegration?,
        guestCount: Int,
        existingAlta: SesCommunication?,
        now: OffsetDateTime = OffsetDateTime.now(),
    ): SesEligibility {
        commonAltaGuards(booking, property, integration)?.let { return it }
        alreadyFiledGuard(existingAlta)?.let { return it }
        if (booking.checkinCompletedAt == null) {
            return SesEligibility.Ineligible(
                code = Errors.CODE_SES_HOSPEDAJES_NOT_ELIGIBLE_NO_CHECKIN_COMPLETED,
                message = Errors.ERROR_SES_HOSPEDAJES_NOT_ELIGIBLE_NO_CHECKIN_COMPLETED,
            )
        }
        val scheduledCheckin = scheduledCheckinAt(booking, property)
        if (scheduledCheckin != null && scheduledCheckin.isAfter(now)) {
            return SesEligibility.Ineligible(
                code = Errors.CODE_SES_HOSPEDAJES_NOT_ELIGIBLE_BEFORE_CHECKIN,
                message = Errors.ERROR_SES_HOSPEDAJES_NOT_ELIGIBLE_BEFORE_CHECKIN,
            )
        }
        if (guestCount == 0) {
            return SesEligibility.Ineligible(
                code = Errors.CODE_SES_HOSPEDAJES_NOT_ELIGIBLE_NO_GUESTS,
                message = Errors.ERROR_SES_HOSPEDAJES_NOT_ELIGIBLE_NO_GUESTS,
            )
        }
        return SesEligibility.Eligible
    }

    /**
     * RH is direct-only: SES requires an RH for every booking, but Airbnb/Booking self-file it, so
     * Kasify files RH only for direct bookings, and only when the account opts in. This gate lives
     * here so both the job and the admin `submit-rh` endpoint enforce it identically — neither can
     * file RH for a non-direct channel or override a host's opt-out. RH drops PV's check-in gate.
     */
    fun checkRhEligibility(
        booking: Booking,
        property: Property?,
        integration: SesHospedajesIntegration?,
        existingAlta: SesCommunication?,
    ): SesEligibility {
        commonAltaGuards(booking, property, integration)?.let { return it }
        alreadyFiledGuard(existingAlta)?.let { return it }
        if (booking.channel != BookingChannel.Direct) {
            return SesEligibility.Ineligible(
                code = Errors.CODE_SES_HOSPEDAJES_NOT_ELIGIBLE_RH_NOT_DIRECT_BOOKING,
                message = Errors.ERROR_SES_HOSPEDAJES_NOT_ELIGIBLE_RH_NOT_DIRECT_BOOKING,
            )
        }
        if (integration?.rhDirectBookingsEnabled != true) {
            return SesEligibility.Ineligible(
                code = Errors.CODE_SES_HOSPEDAJES_NOT_ELIGIBLE_RH_DISABLED,
                message = Errors.ERROR_SES_HOSPEDAJES_NOT_ELIGIBLE_RH_DISABLED,
            )
        }
        // RH is filed off the booking's principal guest (not the PV guest list), so it only needs a
        // principal name — not registered guests.
        if (booking.guestFirstName.isNullOrBlank() || booking.guestLastName.isNullOrBlank()) {
            return SesEligibility.Ineligible(
                code = Errors.CODE_SES_HOSPEDAJES_NOT_ELIGIBLE_NO_GUEST_NAME,
                message = Errors.ERROR_SES_HOSPEDAJES_NOT_ELIGIBLE_NO_GUEST_NAME,
            )
        }
        // SES requires the titular to carry at least one contact (teléfono o correo) — see spec
        // §3.1.1 RH persona ("Obligatorio incluir una de estas tres etiquetas: telefono, telefono2 o
        // correo"). Filing without one yields a partial-error (codigoEstado 6), so gate it here.
        if (booking.guestPhone.isNullOrBlank() && booking.guestEmail.isNullOrBlank()) {
            return SesEligibility.Ineligible(
                code = Errors.CODE_SES_HOSPEDAJES_NOT_ELIGIBLE_NO_CONTACT,
                message = Errors.ERROR_SES_HOSPEDAJES_NOT_ELIGIBLE_NO_CONTACT,
            )
        }
        return SesEligibility.Eligible
    }

    private fun commonAltaGuards(
        booking: Booking,
        property: Property?,
        integration: SesHospedajesIntegration?,
    ): SesEligibility.Ineligible? {
        if (integration == null) {
            // Strict assignment: distinguish "property never linked to an integration" from
            // "assigned integration is gone/disabled" so the fix is obvious to the user.
            return if (property?.sesIntegrationId == null) {
                SesEligibility.Ineligible(
                    code = Errors.CODE_SES_HOSPEDAJES_NOT_ELIGIBLE_PROPERTY_NOT_LINKED,
                    message = Errors.ERROR_SES_HOSPEDAJES_NOT_ELIGIBLE_PROPERTY_NOT_LINKED,
                )
            } else {
                SesEligibility.Ineligible(
                    code = Errors.CODE_SES_HOSPEDAJES_NOT_ELIGIBLE_NO_INTEGRATION,
                    message = Errors.ERROR_SES_HOSPEDAJES_NOT_ELIGIBLE_NO_INTEGRATION,
                )
            }
        }
        if (property?.sesEstablishmentCode.isNullOrBlank()) {
            return SesEligibility.Ineligible(
                code = Errors.CODE_SES_HOSPEDAJES_NOT_ELIGIBLE_NO_ESTABLISHMENT_CODE,
                message = Errors.ERROR_SES_HOSPEDAJES_NOT_ELIGIBLE_NO_ESTABLISHMENT_CODE,
            )
        }
        if (booking.status == BookingStatus.Cancelled) {
            return SesEligibility.Ineligible(
                code = Errors.CODE_SES_HOSPEDAJES_NOT_ELIGIBLE_CANCELLED,
                message = Errors.ERROR_SES_HOSPEDAJES_NOT_ELIGIBLE_CANCELLED,
            )
        }
        return null
    }

    // An alta already submitted/processing/accepted is "already filed" — a feed re-import does not
    // auto-re-submit (v1: re-activation is scoped out; a re-appearing booking stays as-is).
    private fun alreadyFiledGuard(existingAlta: SesCommunication?): SesEligibility.Ineligible? {
        if (existingAlta != null && existingAlta.status in ALREADY_FILED_STATUSES) {
            return SesEligibility.Ineligible(
                code = Errors.CODE_SES_HOSPEDAJES_NOT_ELIGIBLE_ALREADY_SUBMITTED,
                message = Errors.ERROR_SES_HOSPEDAJES_NOT_ELIGIBLE_ALREADY_SUBMITTED,
            )
        }
        return null
    }

    // --- SOAP builders (comm-row path) ------------------------------------------------------

    /**
     * A fully-built alta ready to send: the contract, the exact solicitud XML SES will receive,
     * the frozen [GuestRegistrySnapshot] for the libro registro, and the closure that performs
     * the SOAP call. Built before sending so every outcome path (success, rejection, transport
     * failure) can archive what was communicated.
     */
    private class AltaPayload(
        val establishmentCode: String,
        val contract: SesContract,
        val requestXml: String,
        val snapshot: GuestRegistrySnapshot,
        val send: suspend (SesHospedajesPlugin) -> SesCommunicationResponse,
    )

    private suspend fun buildAltaPayload(
        type: SesCommunicationType,
        property: Property,
        booking: Booking,
        integration: SesHospedajesIntegration,
        guests: List<Guest>,
    ): AltaPayload {
        val establishmentCode =
            property.sesEstablishmentCode
                ?: throw IllegalStateException("Property ${property.id} has no SES establishment code configured")

        // PV (Parte de Viajeros) reports the registered guests, so numPersonas = guests.size.
        // RH (Reserva de Hospedaje) is filed pre-check-in off the booking, before any guest registers,
        // so it uses the booking's own person counts and the booking's principal guest as the titular.
        val numPersonas =
            when (type) {
                SesCommunicationType.PV -> guests.size
                SesCommunicationType.RH -> bookingPersonCount(booking)
            }

        val contract =
            SesContract(
                referencia = booking.channelId,
                fechaContrato = booking.createdAt,
                fechaEntrada = LocalDateTime.of(booking.from, booking.checkinTime),
                fechaSalida = LocalDateTime.of(booking.to, booking.checkoutTime),
                numPersonas = numPersonas,
                numHabitaciones = property.roomsCount,
                internet = property.hasInternet,
                tipoPago = booking.paymentType.toSesTipoPago(),
            )

        return when (type) {
            SesCommunicationType.PV -> {
                require(guests.isNotEmpty()) { "At least one guest is required for a PV alta" }
                val request =
                    SesParteViajerosRequest(
                        arrendadorCode = integration.arrendadorCode,
                        establishmentCode = establishmentCode,
                        contract = contract,
                        guests = guests.map { mapGuest(it) },
                    )
                AltaPayload(
                    establishmentCode = establishmentCode,
                    contract = contract,
                    requestXml = request.solicitudXml(),
                    snapshot = GuestRegistrySnapshot.fromPv(contract, request.guests),
                ) { plugin -> plugin.submitParteViajeros(request) }
            }
            SesCommunicationType.RH -> {
                // RH carries only the booking's principal guest as the titular — not the
                // (post-check-in) PV guest list, which may not exist yet at reservation time.
                val request =
                    SesReservaHospedajeRequest(
                        arrendadorCode = integration.arrendadorCode,
                        establishmentCode = establishmentCode,
                        contract = contract,
                        guests = listOf(buildRhTitular(booking)),
                    )
                AltaPayload(
                    establishmentCode = establishmentCode,
                    contract = contract,
                    requestXml = request.solicitudXml(),
                    snapshot = GuestRegistrySnapshot.fromRh(contract, request.guests),
                ) { plugin -> plugin.submitReservaHospedaje(request) }
            }
        }
    }

    private fun bookingPersonCount(booking: Booking): Int {
        val total = (booking.adultsCount ?: 0) + (booking.childrenCount ?: 0) + (booking.infantsCount ?: 0)
        return if (total > 0) total else 1
    }

    // The RH titular is the booking's principal guest (the only guest data available pre-check-in).
    private fun buildRhTitular(booking: Booking): SesReservaGuest =
        SesReservaGuest(
            rol = "TI",
            nombre = booking.guestFirstName.orEmpty(),
            apellido1 = booking.guestLastName.orEmpty(),
            apellido2 = null,
            tipoDocumento = null,
            numeroDocumento = null,
            fechaNacimiento = null,
            nacionalidad = null,
            sexo = null,
            direccion = null,
            telefono = booking.guestPhone,
            telefono2 = null,
            correo = booking.guestEmail,
        )

    private suspend fun cancelSoap(
        type: SesCommunicationType,
        integration: SesHospedajesIntegration,
        codigoComunicacion: String,
    ): SesCommunicationResponse =
        withPlugin(integration) { plugin ->
            plugin.cancelCommunication(
                SesCancelRequest(
                    arrendadorCode = integration.arrendadorCode,
                    tipoComunicacion = type.name, // "PV" | "RH"
                    codigoComunicacion = codigoComunicacion,
                ),
            )
        }

    /**
     * Resolve the integration assigned to file for [property] — strict, no account-level
     * fallback: an unassigned property never submits, regardless of how many integrations the
     * account has. Returns null when the property is unassigned or the assigned integration is
     * gone/disabled; callers distinguish the two cases via `property.sesIntegrationId`
     * (see [propertyIntegrationConflict] and [commonAltaGuards]).
     */
    private suspend fun resolveIntegrationForProperty(property: Property): SesHospedajesIntegration? {
        val integrationId = property.sesIntegrationId ?: return null
        return integrationsRepository
            .getSesHospedajesIntegrationById(property.accountId, integrationId)
            ?.takeIf { it.enabled }
    }

    /**
     * Non-null when the caller honors the credential-error pause and [integration] is already
     * flagged: submitting would just repeat the same credential error against SES, so the auto
     * job skips without a SOAP call or comm-row churn (relevant in the tick that discovers an
     * outage — later ticks never even discover the paused items).
     */
    private fun pausedCredentialsResult(
        integration: SesHospedajesIntegration,
        honorScopedPauses: Boolean,
    ): ManualSesSubmitResult.CredentialsError? {
        if (!honorScopedPauses) return null
        val codigo = integration.credentialsErrorCode ?: return null
        return ManualSesSubmitResult.CredentialsError(
            codigo = codigo,
            descripcion = integration.credentialsErrorMessage ?: "Credentials flagged",
            submissionId = null,
        )
    }

    /**
     * Non-null when the caller honors the property-config pause and [property] is already flagged:
     * submitting would just repeat the same establishment-code error against SES, so the auto job
     * skips without a SOAP call or comm-row churn — the property-scoped analog of
     * [pausedCredentialsResult].
     */
    private fun pausedPropertyConfigResult(
        property: Property,
        honorScopedPauses: Boolean,
    ): ManualSesSubmitResult.PropertyConfigError? {
        if (!honorScopedPauses) return null
        val message = property.sesConfigErrorMessage ?: return null
        return ManualSesSubmitResult.PropertyConfigError(descripcion = message, submissionId = null)
    }

    /** 409 for an unresolvable integration: unassigned property vs missing/disabled integration. */
    private fun propertyIntegrationConflict(property: Property): ConflictException =
        if (property.sesIntegrationId == null) {
            ConflictException(
                message = Errors.ERROR_SES_HOSPEDAJES_NOT_ELIGIBLE_PROPERTY_NOT_LINKED,
                code = Errors.CODE_SES_HOSPEDAJES_NOT_ELIGIBLE_PROPERTY_NOT_LINKED,
            )
        } else {
            ConflictException(
                message = Errors.ERROR_SES_HOSPEDAJES_NOT_ELIGIBLE_NO_INTEGRATION,
                code = Errors.CODE_SES_HOSPEDAJES_NOT_ELIGIBLE_NO_INTEGRATION,
            )
        }

    private suspend fun <T> withPlugin(
        integration: SesHospedajesIntegration,
        block: suspend (SesHospedajesPlugin) -> T,
    ): T {
        val plugin =
            SesHospedajesPlugin(
                sesUsername = integration.sesUsername,
                sesPassword = integration.sesPassword,
                httpClient = httpClient,
            )
        return try {
            block(plugin)
        } finally {
            plugin.close()
        }
    }

    // --- Persistence (comm-row path) --------------------------------------------------------

    private suspend fun persistAltaSuccess(
        booking: Booking,
        property: Property,
        integration: SesHospedajesIntegration,
        type: SesCommunicationType,
        response: SesCommunicationResponse,
        payload: AltaPayload,
    ): ManualSesSubmitResult {
        // Both writers emit one <comunicacion> per booking, so SES returns at most one código.
        // More than one means a writer changed and the single-column model no longer holds — fail loudly.
        val codigos = response.resultados.mapNotNull { it.codigoComunicacion }
        if (codigos.size > 1) {
            val err =
                "SES returned ${codigos.size} comunicación códigos for booking ${booking.id} ($type); " +
                    "expected exactly one. Refusing to persist."
            logger.error { err }
            val commId =
                runCatching { communicationsRepository.upsertFailed(booking.id, type, SesCommunicationOperation.Alta, err) }
                    .getOrNull()
            val submissionId =
                runCatching { recordAltaAudit(booking.id, response, err, commId, payload.requestXml) }.getOrNull()
            return ManualSesSubmitResult.UpstreamError(message = err, cause = IllegalStateException(err), submissionId = submissionId)
        }

        val result =
            try {
                val commId =
                    retryDb("upsertSubmitted(alta) booking=${booking.id}") {
                        communicationsRepository.upsertSubmitted(
                            bookingId = booking.id,
                            type = type,
                            operation = SesCommunicationOperation.Alta,
                            lote = response.lote,
                            codigoComunicacion = codigos.firstOrNull(),
                        )
                    }
                val submissionId =
                    retryDb("recordSubmitAttempt(alta success) booking=${booking.id}") {
                        recordAltaAudit(booking.id, response, errorText = null, commId = commId, requestXml = payload.requestXml)
                    }
                logger.info { "Booking ${booking.id} $type alta submitted to SES (batch ${response.lote})" }
                ManualSesSubmitResult.Submitted(lote = response.lote, submissionId = submissionId)
            } catch (e: Exception) {
                escalatePostSuccessFailure(booking, response, e)
            }

        // Libro registro (RD 933/2021): archive the accepted parte. Runs after the comm/audit
        // writes so a registry failure never destabilizes the SES state machine.
        recordRegistryEntry(booking, property, integration, type, response, codigos.firstOrNull(), payload)
        return result
    }

    /**
     * Append the accepted alta to `guest_registry_entries` (the libro registro). Failures are
     * escalated to Sentry but never thrown: the SES comm state is already consistent, so the entry
     * can be backfilled by an operator, whereas throwing here would misreport the submission.
     */
    private suspend fun recordRegistryEntry(
        booking: Booking,
        property: Property,
        integration: SesHospedajesIntegration,
        type: SesCommunicationType,
        response: SesCommunicationResponse,
        codigoComunicacion: String?,
        payload: AltaPayload,
    ) {
        try {
            val provinceName =
                property.provinceId?.let { provinceId ->
                    geoRepository.getProvincesByIds(setOf(provinceId))[provinceId]?.name
                }
            retryDb("guestRegistry.record booking=${booking.id}") {
                guestRegistryRepository.record(
                    NewGuestRegistryEntry(
                        accountId = property.accountId,
                        propertyId = property.id,
                        bookingId = booking.id,
                        communicationType = type,
                        sesArrendadorCode = integration.arrendadorCode,
                        arrendadorNif = property.arrendadorNif,
                        sesEstablishmentCode = payload.establishmentCode,
                        propertyName = property.name,
                        propertyAddress = property.address,
                        postalCode = property.zipcode,
                        city = property.city,
                        province = provinceName,
                        contractReference = payload.contract.referencia,
                        contractDate = payload.contract.fechaContrato,
                        bookingChannel = booking.channel,
                        checkinDate = booking.from,
                        checkoutDate = booking.to,
                        numGuests = payload.contract.numPersonas,
                        snapshot = payload.snapshot,
                        requestXml = payload.requestXml,
                        sesLote = response.lote,
                        codigoComunicacion = codigoComunicacion,
                        submittedAt = OffsetDateTime.now(),
                    ),
                )
            }
        } catch (e: Exception) {
            logger.error(e) {
                "Failed to write libro-registro entry for booking ${booking.id} ($type). " +
                    "SES state is consistent; the entry must be backfilled manually."
            }
            Sentry.captureException(e) { scope ->
                scope.setTag("kind", "guest_registry_persistence")
                scope.setTag("booking_id", booking.id.toString())
            }
        }
    }

    private suspend fun persistAltaRejection(
        booking: Booking,
        property: Property,
        type: SesCommunicationType,
        response: SesCommunicationResponse,
        requestXml: String? = null,
    ): ManualSesSubmitResult {
        val err = "SES rejected: code=${response.codigo} desc=${response.descripcion}"
        return try {
            val commId =
                retryDb("upsertRejected(alta) booking=${booking.id}") {
                    communicationsRepository.upsertRejected(booking.id, type, SesCommunicationOperation.Alta, err)
                }
            val submissionId =
                retryDb("recordSubmitAttempt(alta rejection) booking=${booking.id}") {
                    recordAltaAudit(booking.id, response, errorText = err, commId = commId, requestXml = requestXml)
                }
            bookingRejectedNotifier.notifyOwners(booking, property, type, response.descripcion)
            ManualSesSubmitResult.RejectedBySes(
                codigo = response.codigo,
                descripcion = response.descripcion,
                submissionId = submissionId,
            )
        } catch (e: Exception) {
            val msg = (e.message ?: e::class.simpleName ?: "Unknown error").take(1000)
            logger.error(e) { "SES rejected booking ${booking.id} but persistence exhausted retries: $msg" }
            ManualSesSubmitResult.UpstreamError(message = msg, cause = e, submissionId = null)
        }
    }

    /**
     * SES rejected the call with a credential-family código. The comm row is marked `failed` — not
     * `rejected` — because the booking data is fine and the submission must be re-picked
     * automatically once the credentials are fixed (a `failed` alta is never gated by the
     * rejected-alta retry gate). The account-level pause comes from the integration flag set via
     * [SesCredentialsGuard.onCredentialError], which also notifies the owners on the first failure.
     * All writes are best-effort so the [ManualSesSubmitResult.CredentialsError] outcome is always
     * reported to the caller.
     */
    private suspend fun persistCredentialsFailure(
        booking: Booking,
        type: SesCommunicationType,
        operation: SesCommunicationOperation,
        response: SesCommunicationResponse,
        integration: SesHospedajesIntegration,
        requestXml: String? = null,
    ): ManualSesSubmitResult {
        val err = "SES credentials error: code=${response.codigo} desc=${response.descripcion}"
        logger.error { "SES rejected $type $operation for booking ${booking.id} with a credential error: $err" }
        val commId =
            runCatching { communicationsRepository.upsertFailed(booking.id, type, operation, err) }
                .onFailure { logger.error(it) { "Error writing failed comm row for credential error." } }
                .getOrNull()
        val submissionId =
            runCatching { recordAltaAudit(booking.id, response, errorText = err, commId = commId, requestXml = requestXml) }
                .onFailure { logger.error(it) { "Error saving the SES submission audit row." } }
                .getOrNull()
        credentialsGuard.onCredentialError(integration, response.codigo, response.descripcion)
        return ManualSesSubmitResult.CredentialsError(
            codigo = response.codigo,
            descripcion = response.descripcion,
            submissionId = submissionId,
        )
    }

    /**
     * SES rejected the call because the property's establishment code is unknown. The comm row is
     * marked `failed` — not `rejected` — because the booking data is fine and the submission must
     * be re-picked automatically once the code is fixed (recovery must never depend on the
     * booking-edit watermark of the rejected-alta retry gate: the fix lives on the property). The
     * property-level pause comes from the flag set via [SesPropertyConfigGuard.onConfigError],
     * which also notifies the owners on the first failure. All writes are best-effort so the
     * [ManualSesSubmitResult.PropertyConfigError] outcome is always reported to the caller.
     */
    private suspend fun persistPropertyConfigFailure(
        booking: Booking,
        property: Property,
        type: SesCommunicationType,
        operation: SesCommunicationOperation,
        response: SesCommunicationResponse,
        requestXml: String? = null,
    ): ManualSesSubmitResult {
        val err = "SES establishment-code error: code=${response.codigo} desc=${response.descripcion}"
        logger.error { "SES rejected $type $operation for booking ${booking.id} with a property-config error: $err" }
        val commId =
            runCatching { communicationsRepository.upsertFailed(booking.id, type, operation, err) }
                .onFailure { logger.error(it) { "Error writing failed comm row for property-config error." } }
                .getOrNull()
        val submissionId =
            runCatching { recordAltaAudit(booking.id, response, errorText = err, commId = commId, requestXml = requestXml) }
                .onFailure { logger.error(it) { "Error saving the SES submission audit row." } }
                .getOrNull()
        propertyConfigGuard.onConfigError(property, response.descripcion)
        return ManualSesSubmitResult.PropertyConfigError(
            descripcion = response.descripcion,
            submissionId = submissionId,
        )
    }

    private suspend fun recordAltaSoapFailure(
        booking: Booking,
        type: SesCommunicationType,
        cause: Exception,
        requestXml: String? = null,
    ): ManualSesSubmitResult {
        val msg = (cause.message ?: cause::class.simpleName ?: "Unknown error").take(1000)
        logger.error(cause) { "Failed to submit SES $type alta for booking ${booking.id}" }
        val commId =
            runCatching { communicationsRepository.upsertFailed(booking.id, type, SesCommunicationOperation.Alta, msg) }
                .onFailure { logger.error(it) { "Error writing failed alta comm row." } }
                .getOrNull()
        val submissionId =
            runCatching {
                submissionsRepository.recordSubmitAttempt(
                    bookingId = booking.id,
                    respuestaCodigo = null,
                    respuestaDescripcion = null,
                    sesLote = null,
                    responseXml = null,
                    errorText = msg,
                    sesCommunicationId = commId,
                    requestXml = requestXml,
                )
            }.onFailure { logger.error(it) { "Error saving the SES submission audit row." } }
                .getOrNull()
        return ManualSesSubmitResult.UpstreamError(message = msg, cause = cause, submissionId = submissionId)
    }

    private suspend fun persistCancelSuccess(
        booking: Booking,
        type: SesCommunicationType,
        alta: SesCommunication,
        response: SesCommunicationResponse,
    ): ManualSesSubmitResult =
        try {
            val commId =
                retryDb("upsertSubmitted(anulacion) booking=${booking.id}") {
                    communicationsRepository.upsertSubmitted(
                        bookingId = booking.id,
                        type = type,
                        operation = SesCommunicationOperation.Anulacion,
                        lote = response.lote,
                        codigoComunicacion = alta.codigoComunicacion,
                        cancelsId = alta.id,
                    )
                }
            val submissionId =
                retryDb("recordSubmitAttempt(anulacion success) booking=${booking.id}") {
                    recordAltaAudit(booking.id, response, errorText = null, commId = commId)
                }
            logger.info { "Booking ${booking.id} $type anulación submitted to SES (batch ${response.lote})" }
            ManualSesSubmitResult.Submitted(lote = response.lote, submissionId = submissionId)
        } catch (e: Exception) {
            escalatePostSuccessFailure(booking, response, e)
        }

    private suspend fun persistCancelRejection(
        booking: Booking,
        type: SesCommunicationType,
        response: SesCommunicationResponse,
    ): ManualSesSubmitResult {
        val err = "SES rejected anulación: code=${response.codigo} desc=${response.descripcion}"
        return try {
            val commId =
                retryDb("upsertRejected(anulacion) booking=${booking.id}") {
                    communicationsRepository.upsertRejected(booking.id, type, SesCommunicationOperation.Anulacion, err)
                }
            val submissionId =
                retryDb("recordSubmitAttempt(anulacion rejection) booking=${booking.id}") {
                    recordAltaAudit(booking.id, response, errorText = err, commId = commId)
                }
            ManualSesSubmitResult.RejectedBySes(
                codigo = response.codigo,
                descripcion = response.descripcion,
                submissionId = submissionId,
            )
        } catch (e: Exception) {
            val msg = (e.message ?: e::class.simpleName ?: "Unknown error").take(1000)
            logger.error(e) { "SES rejected anulación booking ${booking.id} but persistence exhausted retries: $msg" }
            ManualSesSubmitResult.UpstreamError(message = msg, cause = e, submissionId = null)
        }
    }

    private suspend fun recordCancelSoapFailure(
        booking: Booking,
        type: SesCommunicationType,
        cause: Exception,
    ): ManualSesSubmitResult {
        val msg = (cause.message ?: cause::class.simpleName ?: "Unknown error").take(1000)
        logger.error(cause) { "Failed to send SES $type anulación for booking ${booking.id}" }
        val commId =
            runCatching { communicationsRepository.upsertFailed(booking.id, type, SesCommunicationOperation.Anulacion, msg) }
                .onFailure { logger.error(it) { "Error writing failed anulación comm row." } }
                .getOrNull()
        val submissionId =
            runCatching {
                submissionsRepository.recordSubmitAttempt(
                    bookingId = booking.id,
                    respuestaCodigo = null,
                    respuestaDescripcion = null,
                    sesLote = null,
                    responseXml = null,
                    errorText = msg,
                    sesCommunicationId = commId,
                )
            }.onFailure { logger.error(it) { "Error saving the SES anulación audit row." } }
                .getOrNull()
        return ManualSesSubmitResult.UpstreamError(message = msg, cause = cause, submissionId = submissionId)
    }

    private suspend fun recordAltaAudit(
        bookingId: Uuid,
        response: SesCommunicationResponse,
        errorText: String?,
        commId: Uuid?,
        requestXml: String? = null,
    ): Uuid =
        submissionsRepository.recordSubmitAttempt(
            bookingId = bookingId,
            respuestaCodigo = response.codigo,
            respuestaDescripcion = response.descripcion,
            sesLote = response.lote,
            responseXml = response.rawXml.ifBlank { null },
            errorText = errorText,
            sesCommunicationId = commId,
            requestXml = requestXml,
        )

    // SES accepted (codigo=0) but the post-success DB writes exhausted retries. The comm row was not
    // written, so the next tick re-discovers and re-submits → duplicate filing. Escalate so an
    // operator can intervene.
    private fun escalatePostSuccessFailure(
        booking: Booking,
        response: SesCommunicationResponse,
        e: Exception,
    ): Nothing {
        logger.error(e) {
            "SES accepted booking ${booking.id} (lote=${response.lote}) but DB persistence exhausted " +
                "retries. The booking is still selectable and WILL be re-submitted next tick. " +
                "Manual reconciliation required."
        }
        Sentry.captureException(e) { scope ->
            scope.setTag("kind", "ses_post_success_persistence")
            scope.setTag("booking_id", booking.id.toString())
            response.lote?.let { scope.setTag("ses_lote", it) }
        }
        throw SesPostSuccessPersistenceException(bookingId = booking.id, lote = response.lote, cause = e)
    }

    companion object {
        private val ALREADY_FILED_STATUSES =
            setOf(
                SesCommunicationStatus.Submitted,
                SesCommunicationStatus.Processing,
                SesCommunicationStatus.Accepted,
            )

        private val ACTIVE_ANULACION_STATUSES =
            setOf(
                SesCommunicationStatus.Submitted,
                SesCommunicationStatus.Processing,
                SesCommunicationStatus.Accepted,
                SesCommunicationStatus.Rejected,
            )

        fun mapDocumentType(type: DocumentType?): String? =
            when (type) {
                DocumentType.DNI -> "NIF"
                DocumentType.NIE -> "NIE"
                DocumentType.Passport -> "PAS"
                DocumentType.Other -> "OTRO"
                null -> null
            }

        fun mapGender(gender: Gender?): String? =
            when (gender) {
                Gender.Male -> "H"
                Gender.Female -> "M"
                Gender.Other -> "X"
                null -> null
            }
    }
}
