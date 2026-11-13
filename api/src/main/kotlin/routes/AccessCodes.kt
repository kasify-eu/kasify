package eu.kasify.routes

import eu.kasify.authenticatedAndRateLimited
import eu.kasify.db.BookingAccessCodesRepository
import eu.kasify.db.BookingsRepository
import eu.kasify.db.PropertiesRepository
import eu.kasify.db.SmartlocksRepository
import eu.kasify.db.model.PropertyAccessLevel
import eu.kasify.model.api.SmartLockInfo
import eu.kasify.model.api.responses.KeysResponse
import eu.kasify.plugins.nuki.NukiUnauthorizedException
import eu.kasify.routes.exceptions.ConflictException
import eu.kasify.routes.exceptions.ForbiddenException
import eu.kasify.routes.exceptions.InternalServerException
import eu.kasify.routes.exceptions.NotAcceptableException
import eu.kasify.routes.exceptions.NotFoundException
import eu.kasify.routes.extensions.getPathAccessCodeBooking
import eu.kasify.routes.extensions.getPathBooking
import eu.kasify.routes.extensions.getProperty
import eu.kasify.security.getKasifyUser
import eu.kasify.services.AuthorizationService
import eu.kasify.services.SmartlocksService
import eu.kasify.utils.createLocalTimeUtc
import eu.kasify.versionedRouting
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.HttpStatusCode
import io.ktor.openapi.jsonSchema
import io.ktor.server.application.Application
import io.ktor.server.response.respond
import io.ktor.server.routing.delete
import io.ktor.server.routing.openapi.describe
import io.ktor.server.routing.post
import io.ktor.utils.io.ExperimentalKtorApi
import org.koin.ktor.ext.inject
import java.time.LocalDate
import eu.kasify.model.api.BookingAccessCode as ApiBookingAccessCode

private val logger = KotlinLogging.logger {}

@OptIn(ExperimentalKtorApi::class)
fun Application.configureDigitalKeysRouting() {
    val bookingsRepository by inject<BookingsRepository>()
    val bookingAccessCodesRepository by inject<BookingAccessCodesRepository>()
    val authorizationService by inject<AuthorizationService>()
    val propertiesService by inject<PropertiesRepository>()
    val smartlocksRepository by inject<SmartlocksRepository>()
    val smartlocksService by inject<SmartlocksService>()

    versionedRouting {
        authenticatedAndRateLimited {
            // Create digital key for a booking
            post("/bookings/{id}/accesscode") {
                val user = call.getKasifyUser()
                val booking = call.getPathBooking(bookingsRepository)
                val property = call.getProperty(propertiesService, booking.propertyId)

                val access = authorizationService.getAccess(user, property)
                if (!access.isAccountMember && access.accessLevel != PropertyAccessLevel.Manager) {
                    throw ForbiddenException("Manager access required.")
                }

                // Check if booking is already completed
                if (booking.to.isBefore(LocalDate.now())) {
                    throw ForbiddenException(Errors.ERROR_BOOKING_ALREADY_COMPLETED, code = "booking.already_completed")
                }

                // Check if access codes already exist for this booking
                val existingCodes = bookingAccessCodesRepository.getByBookingId(booking.id)
                if (existingCodes.isNotEmpty()) {
                    throw ConflictException(
                        Errors.ERROR_ACCESS_CODES_ALREADY_EXIST_IN_BOOKING,
                        code = "access_code.already_exists",
                    )
                }

                val smartlocks = smartlocksRepository.getSmartlocksByProperty(property.id)
                if (smartlocks.isEmpty()) {
                    throw NotAcceptableException(Errors.ERROR_SMARTLOCKS_NOT_CONFIGURED, code = "smartlock.not_configured")
                }

                val codeFromUtc = createLocalTimeUtc(booking.from, booking.checkinTime, property.tzone)
                val codeToUtc = createLocalTimeUtc(booking.to, booking.checkoutTime, property.tzone)

                // Create the codes in the configured smartlocks
                try {
                    val result = smartlocksService.createAccessCodes(property, booking, smartlocks, codeFromUtc, codeToUtc)
                    val response =
                        KeysResponse(
                            accessCode = result.accessCode,
                            accessCodes = result.accessCodes.map { ApiBookingAccessCode.from(it) },
                            failedSmartlocks = result.failedSmartlocks.map { SmartLockInfo.from(it) },
                        )
                    call.respond(HttpStatusCode.OK, response)
                } catch (_: NukiUnauthorizedException) {
                    throw ForbiddenException("Forbidden access to Nuki API", code = "integration.nuki_unauthorized")
                } catch (e: Exception) {
                    logger.error(e) { "Failed to create access codes for booking ${booking.id}: ${e.message}" }
                    throw InternalServerException("Cannot access access code")
                }
            }.describe {
                tag("AccessCodes")
                summary = "Create access codes"
                description = "Generates an access code on each smart lock configured for the property. Codes are valid from the booking's arrival time until its departure time. Partial failures are reported in `failed_smartlocks` rather than failing the request. Restricted to owners and managers; staff and contractors are rejected."
                parameters {
                    path("id") { description = "Booking id" }
                }
                responses {
                    HttpStatusCode.OK {
                        description = "Access codes created. `failed_smartlocks` lists any locks the provider rejected."
                        schema = jsonSchema<KeysResponse>()
                    }
                    HttpStatusCode.Forbidden { description = "Caller is not an owner or manager of the booking's property" }
                    HttpStatusCode.Forbidden {
                        description = "Booking already completed (`booking.already_completed`) or Nuki rejected the credentials (`integration.nuki_unauthorized`)"
                    }
                    HttpStatusCode.NotFound { description = "Booking or property not found" }
                    HttpStatusCode.NotAcceptable {
                        description = "Property has no smart locks configured. Returns `smartlock.not_configured`"
                    }
                    HttpStatusCode.Conflict {
                        description = "Access codes already exist for this booking. Returns `access_code.already_exists`"
                    }
                }
            }

            delete("accesscodes/{id}") {
                val user = call.getKasifyUser()
                val accessCode = call.getPathAccessCodeBooking(bookingAccessCodesRepository)
                val booking =
                    bookingsRepository.getBooking(accessCode.bookingId)
                        ?: throw NotFoundException(Errors.ERROR_BOOKING_NOT_FOUND)

                val property = call.getProperty(propertiesService, booking.propertyId)

                val access = authorizationService.getAccess(user, property)
                if (!access.isAccountMember && access.accessLevel != PropertyAccessLevel.Manager) {
                    throw ForbiddenException("Manager access required.")
                }

                smartlocksService.deleteAccessCode(accessCode, property.accountId)

                call.respond(HttpStatusCode.NoContent)
            }.describe {
                tag("AccessCodes")
                summary = "Delete access code"
                description = "Revokes the access code: deletes it from Kasify and from the smart-lock provider (e.g. Nuki, Unifi). Restricted to owners and managers; staff and contractors are rejected."
                parameters {
                    path("id") { description = "Access code id" }
                }
                responses {
                    HttpStatusCode.NoContent { description = "Access code revoked" }
                    HttpStatusCode.Forbidden { description = "Caller is not an owner or manager of the booking's property" }
                    HttpStatusCode.NotFound { description = "Access code or booking not found" }
                }
            }
        }
    }
}
