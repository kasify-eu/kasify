package eu.kasify.routes

import eu.kasify.authenticatedAndRateLimited
import eu.kasify.db.BookingsRepository
import eu.kasify.db.SesCommunicationsRepository
import eu.kasify.db.SesHospedajesSubmissionsRepository
import eu.kasify.model.Booking
import eu.kasify.model.SesCommunicationType
import eu.kasify.model.api.responses.AdminSesBookingDetailResponse
import eu.kasify.routes.exceptions.BadParamRequestException
import eu.kasify.routes.exceptions.ConflictException
import eu.kasify.routes.exceptions.NotFoundException
import eu.kasify.routes.extensions.getPathBooking
import eu.kasify.security.requireRole
import eu.kasify.services.SesHospedajesService
import eu.kasify.versionedRouting
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.HttpStatusCode
import io.ktor.openapi.jsonSchema
import io.ktor.server.application.Application
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.openapi.describe
import io.ktor.server.routing.openapi.hide
import io.ktor.server.routing.post
import io.ktor.utils.io.ExperimentalKtorApi
import org.koin.ktor.ext.inject
import kotlin.uuid.Uuid
import eu.kasify.model.api.Booking as ApiBooking
import eu.kasify.model.api.SesHospedajesSubmission as ApiSesHospedajesSubmission

private val logger = KotlinLogging.logger {}

/**
 * Platform-admin SES Hospedajes monitoring endpoints. Bypasses `property_access`
 * — admins are platform staff, not crew — and relies on plain serialization to
 * expose all `@VisibleTo` fields (the annotation is only enforced by
 * `SecuritySerializer`, which we deliberately skip here).
 */
@OptIn(ExperimentalKtorApi::class)
fun Application.configureAdminSesMonitoringRouting() {
    val bookingsRepository by inject<BookingsRepository>()
    val submissionsRepository by inject<SesHospedajesSubmissionsRepository>()
    val communicationsRepository by inject<SesCommunicationsRepository>()
    val sesService by inject<SesHospedajesService>()

    versionedRouting {
        authenticatedAndRateLimited {
            requireRole("admin") {
                get("/admin/ses/bookings/{id}/submissions") {
                    val booking = call.getPathBooking(bookingsRepository)

                    val payload =
                        buildAdminSesDetailResponse(
                            booking = booking,
                            submissionsRepository = submissionsRepository,
                            communicationsRepository = communicationsRepository,
                        )
                    call.respond(payload)
                }.describe {
                    tag("Admin · SES")
                    summary = "Get a booking's SES Hospedajes audit log (admin)"
                    parameters {
                        path("id") { description = "Booking id" }
                    }
                    responses {
                        HttpStatusCode.OK {
                            description = "Booking plus full submission history (newest first)"
                            schema = jsonSchema<AdminSesBookingDetailResponse>()
                        }
                        HttpStatusCode.NotFound {
                            description = "Booking not found. Returns `booking.not_found`."
                        }
                        HttpStatusCode.Unauthorized { description = "Not authenticated" }
                        HttpStatusCode.Forbidden { description = "Caller is not an admin" }
                    }
                }

                post("/admin/ses/bookings/{id}/submit") {
                    val booking = call.getPathBooking(bookingsRepository)
                    val result = sesService.submitAlta(booking.id, SesCommunicationType.PV)
                    respondWithSubmitResult(
                        call,
                        booking,
                        result,
                        "PV submit",
                        bookingsRepository,
                        submissionsRepository,
                        communicationsRepository,
                    )
                }.describe {
                    tag("Admin · SES")
                    summary = "Manually submit a booking's Parte de Viajeros to SES (admin)"
                    parameters {
                        path("id") { description = "Booking id" }
                    }
                    responses {
                        HttpStatusCode.OK {
                            description = "SES accepted the submission (codigo == 0)."
                            schema = jsonSchema<AdminSesBookingDetailResponse>()
                        }
                        HttpStatusCode.Accepted {
                            description = "SES returned a non-zero rejection code (audit row persisted)."
                            schema = jsonSchema<AdminSesBookingDetailResponse>()
                        }
                        HttpStatusCode.Conflict {
                            description =
                                "Booking is not eligible. Returns `ses_hospedajes.not_eligible.<reason>` " +
                                "(property_not_linked, no_integration, no_establishment_code, no_checkin_completed, " +
                                "already_submitted, before_checkin, no_guests, cancelled). Also returned as " +
                                "`ses_hospedajes.invalid_credentials` when SES rejected the account's WS " +
                                "credentials (the integration is flagged and auto-submissions pause), and as " +
                                "`ses_hospedajes.property_config_error` when SES does not recognize the " +
                                "property's establishment code (the property is flagged and its " +
                                "auto-submissions pause)."
                        }
                        HttpStatusCode.NotFound {
                            description = "Booking not found. Returns `booking.not_found`."
                        }
                        HttpStatusCode.BadGateway {
                            description = "SES upstream error after audit row written."
                        }
                        HttpStatusCode.Unauthorized { description = "Not authenticated" }
                        HttpStatusCode.Forbidden { description = "Caller is not an admin" }
                    }
                }

                post("/admin/ses/bookings/{id}/submit-rh") {
                    val booking = call.getPathBooking(bookingsRepository)
                    val result = sesService.submitAlta(booking.id, SesCommunicationType.RH)
                    respondWithSubmitResult(
                        call,
                        booking,
                        result,
                        "RH submit",
                        bookingsRepository,
                        submissionsRepository,
                        communicationsRepository,
                    )
                }.describe {
                    tag("Admin · SES")
                    summary = "Manually submit a booking's Reserva de Hospedaje to SES (admin)"
                    parameters {
                        path("id") { description = "Booking id" }
                    }
                    responses {
                        HttpStatusCode.OK {
                            description = "SES accepted the submission (codigo == 0)."
                            schema = jsonSchema<AdminSesBookingDetailResponse>()
                        }
                        HttpStatusCode.Accepted {
                            description = "SES returned a non-zero rejection code (audit row persisted)."
                            schema = jsonSchema<AdminSesBookingDetailResponse>()
                        }
                        HttpStatusCode.Conflict {
                            description =
                                "Booking is not eligible. Gated identically to the auto job — a non-direct " +
                                "booking returns `ses_hospedajes.not_eligible.rh_not_direct_booking` and an " +
                                "account with RH disabled returns `ses_hospedajes.not_eligible.rh_disabled`. " +
                                "Other reasons mirror /submit, including `ses_hospedajes.invalid_credentials`."
                        }
                        HttpStatusCode.NotFound {
                            description = "Booking not found. Returns `booking.not_found`."
                        }
                        HttpStatusCode.BadGateway {
                            description = "SES upstream error after audit row written."
                        }
                        HttpStatusCode.Unauthorized { description = "Not authenticated" }
                        HttpStatusCode.Forbidden { description = "Caller is not an admin" }
                    }
                }

                post("/admin/ses/communications/{id}/poll") {
                    val communicationId = call.pathCommunicationId()
                    val bookingId = sesService.pollCommunication(communicationId)
                    val booking =
                        bookingsRepository.getBooking(bookingId)
                            ?: throw NotFoundException("Booking $bookingId not found", code = "booking.not_found")
                    call.respond(
                        HttpStatusCode.OK,
                        buildAdminSesDetailResponse(booking, submissionsRepository, communicationsRepository),
                    )
                }.describe {
                    tag("Admin · SES")
                    summary = "Re-poll a single SES communication (consultaLote) now (admin)"
                    parameters {
                        path("id") { description = "SES communication id" }
                    }
                    responses {
                        HttpStatusCode.OK {
                            description = "Communication re-polled; returns the refreshed booking + audit log."
                            schema = jsonSchema<AdminSesBookingDetailResponse>()
                        }
                        HttpStatusCode.Conflict {
                            description = "No enabled SES integration for the account (`ses_hospedajes.not_eligible.no_integration`)."
                        }
                        HttpStatusCode.NotFound {
                            description = "Communication not found. Returns `ses_hospedajes.communication_not_found`."
                        }
                        HttpStatusCode.BadRequest { description = "`id` is not a valid UUID." }
                        HttpStatusCode.Unauthorized { description = "Not authenticated" }
                        HttpStatusCode.Forbidden { description = "Caller is not an admin" }
                    }
                }

                post("/admin/ses/communications/{id}/cancel") {
                    val communicationId = call.pathCommunicationId()
                    val result = sesService.submitCancellation(communicationId)
                    val comm =
                        communicationsRepository.getById(communicationId)
                            ?: throw NotFoundException(
                                Errors.ERROR_SES_HOSPEDAJES_COMMUNICATION_NOT_FOUND,
                                code = Errors.CODE_SES_HOSPEDAJES_COMMUNICATION_NOT_FOUND,
                            )
                    val booking =
                        bookingsRepository.getBooking(comm.bookingId)
                            ?: throw NotFoundException("Booking ${comm.bookingId} not found", code = "booking.not_found")
                    respondWithSubmitResult(
                        call,
                        booking,
                        result,
                        "${comm.communicationType.name} anulación",
                        bookingsRepository,
                        submissionsRepository,
                        communicationsRepository,
                    )
                }.describe {
                    tag("Admin · SES")
                    summary = "Anular (cancel) a single accepted SES communication (admin)"
                    parameters {
                        path("id") { description = "SES communication id (the accepted alta to annul)" }
                    }
                    responses {
                        HttpStatusCode.OK {
                            description = "SES accepted the anulación (codigo == 0)."
                            schema = jsonSchema<AdminSesBookingDetailResponse>()
                        }
                        HttpStatusCode.Accepted {
                            description = "SES returned a non-zero rejection code (audit row persisted)."
                            schema = jsonSchema<AdminSesBookingDetailResponse>()
                        }
                        HttpStatusCode.Conflict {
                            description =
                                "Not eligible to cancel. Returns `ses_hospedajes.not_eligible.no_accepted_alta` " +
                                "(the communication isn't an accepted alta with a código), " +
                                "`ses_hospedajes.already_cancelled` (an anulación is already in progress or completed), " +
                                "or `ses_hospedajes.invalid_credentials` (SES rejected the account's WS credentials)."
                        }
                        HttpStatusCode.BadRequest { description = "`id` is not a valid UUID." }
                        HttpStatusCode.NotFound {
                            description = "Communication not found. Returns `ses_hospedajes.communication_not_found`."
                        }
                        HttpStatusCode.BadGateway {
                            description = "SES upstream error after audit row written."
                        }
                        HttpStatusCode.Unauthorized { description = "Not authenticated" }
                        HttpStatusCode.Forbidden { description = "Caller is not an admin" }
                    }
                }
                // Internal platform-admin API — kept out of the public OpenAPI spec.
            }.hide()
        }
    }
}

/**
 * Shared response mapping for the manual submit / submit-rh / cancel endpoints. Re-fetches the
 * booking + comm rows so the detail drawer reflects post-operation state, then maps the service
 * result to a status code: accepted → 200, SES-rejected → 202, upstream error → 502. Ineligible
 * requests throw a [eu.kasify.routes.exceptions.ConflictException] upstream (→ 409) before reaching here.
 */
private suspend fun respondWithSubmitResult(
    call: io.ktor.server.application.ApplicationCall,
    booking: Booking,
    result: SesHospedajesService.ManualSesSubmitResult,
    label: String,
    bookingsRepository: BookingsRepository,
    submissionsRepository: SesHospedajesSubmissionsRepository,
    communicationsRepository: SesCommunicationsRepository,
) {
    val refreshed = bookingsRepository.getBooking(booking.id) ?: booking
    val payload =
        buildAdminSesDetailResponse(
            booking = refreshed,
            submissionsRepository = submissionsRepository,
            communicationsRepository = communicationsRepository,
        )

    when (result) {
        is SesHospedajesService.ManualSesSubmitResult.Submitted ->
            call.respond(HttpStatusCode.OK, payload)

        is SesHospedajesService.ManualSesSubmitResult.RejectedBySes ->
            call.respond(HttpStatusCode.Accepted, payload)

        // Credential-family SES error: the service has already flagged the integration (pausing
        // auto-submissions) and recorded the failed attempt. Surface the dedicated code so the
        // dashboard tells the operator to fix the credentials rather than the booking data.
        is SesHospedajesService.ManualSesSubmitResult.CredentialsError ->
            throw ConflictException(
                message = "${Errors.ERROR_SES_HOSPEDAJES_INVALID_CREDENTIALS} (SES code ${result.codigo})",
                code = Errors.CODE_SES_HOSPEDAJES_INVALID_CREDENTIALS,
            )

        // Establishment-code SES error: the service has flagged the property (pausing its
        // auto-submissions) and recorded the failed attempt. Surface the dedicated code so the
        // dashboard tells the operator to fix the property's SES config, not the booking data.
        is SesHospedajesService.ManualSesSubmitResult.PropertyConfigError ->
            throw ConflictException(
                message = "${Errors.ERROR_SES_HOSPEDAJES_PROPERTY_CONFIG_ERROR} (${result.descripcion})",
                code = Errors.CODE_SES_HOSPEDAJES_PROPERTY_CONFIG_ERROR,
            )

        is SesHospedajesService.ManualSesSubmitResult.UpstreamError -> {
            logger.error(result.cause) { "Manual SES $label failed for booking ${booking.id}: ${result.message}" }
            // 502: the audit row + comm-row outcome are already persisted.
            call.respond(HttpStatusCode.BadGateway, payload)
        }
    }
}

private fun io.ktor.server.application.ApplicationCall.pathCommunicationId(): Uuid {
    val raw = parameters["id"] ?: throw BadParamRequestException("Missing communication id", code = "invalid_parameter")
    return runCatching { Uuid.parse(raw) }
        .getOrElse { throw BadParamRequestException("Invalid communication id", code = "invalid_parameter") }
}

private suspend fun buildAdminSesDetailResponse(
    booking: Booking,
    submissionsRepository: SesHospedajesSubmissionsRepository,
    communicationsRepository: SesCommunicationsRepository,
): AdminSesBookingDetailResponse {
    val submissions =
        submissionsRepository
            .listSubmissionsForBooking(booking.id)
            .map { ApiSesHospedajesSubmission.from(it, includeResponseXml = true) }
    val communications = communicationsRepository.listByBooking(booking.id)

    return AdminSesBookingDetailResponse(
        booking = ApiBooking.from(booking, sesCommunications = communications, role = "owner"),
        submissions = submissions,
    )
}
