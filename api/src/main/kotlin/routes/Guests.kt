package eu.kasify.routes

import eu.kasify.authenticatedAndRateLimited
import eu.kasify.db.BookingsRepository
import eu.kasify.db.GuestsRepository
import eu.kasify.db.PropertiesRepository
import eu.kasify.db.SesCommunicationsRepository
import eu.kasify.db.model.PropertyAccessLevel
import eu.kasify.model.api.Guest
import eu.kasify.model.api.requests.GuestRegistrationRequest
import eu.kasify.model.api.requests.GuestsFilters
import eu.kasify.model.api.responses.GuestsResponse
import eu.kasify.routes.exceptions.ConflictException
import eu.kasify.routes.exceptions.ForbiddenException
import eu.kasify.routes.exceptions.NotFoundException
import eu.kasify.routes.extensions.getGuestsFilters
import eu.kasify.routes.extensions.getPageableWithLimit
import eu.kasify.routes.extensions.getPathBooking
import eu.kasify.routes.extensions.getProperty
import eu.kasify.security.getKasifyUser
import eu.kasify.services.AuthorizationService
import eu.kasify.services.CheckinService
import eu.kasify.services.GuestHydrationService
import eu.kasify.versionedRouting
import io.ktor.http.HttpStatusCode
import io.ktor.openapi.jsonSchema
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.plugins.MissingRequestParameterException
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.openapi.describe
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.utils.io.ExperimentalKtorApi
import org.koin.ktor.ext.inject
import kotlin.uuid.Uuid

@OptIn(ExperimentalKtorApi::class)
fun Application.configureGuestsRouting() {
    val guestsRepository by inject<GuestsRepository>()
    val guestHydrationService by inject<GuestHydrationService>()
    val bookingsRepository by inject<BookingsRepository>()
    val propertiesRepository by inject<PropertiesRepository>()
    val communicationsRepository by inject<SesCommunicationsRepository>()
    val authorizationService by inject<AuthorizationService>()
    val checkinService by inject<CheckinService>()

    versionedRouting {
        authenticatedAndRateLimited {
            get("/guests") {
                val user = call.getKasifyUser()
                val pageable = call.getPageableWithLimit()
                val filters: GuestsFilters = call.getGuestsFilters()

                val guests =
                    guestsRepository.getGuestsByAccount(
                        accountId = user.accountId,
                        userId = user.userId,
                        filters = filters,
                        pageable = pageable,
                    )
                val bookingContext =
                    guestsRepository.getGuestBookingContext(guests.content.map { it.bookingId })
                val apiGuests =
                    guestHydrationService.hydrate(
                        guests = guests.content,
                        bookingContext = bookingContext,
                        viewerAccountId = user.accountId,
                    )
                val response = GuestsResponse.from(apiGuests, guests)
                call.respond(response)
            }.describe {
                tag("Guests")
                summary = "List guests"
                description =
                    "Returns guests registered across bookings, optionally filtered by property, " +
                    "channel reference, or free-text query."
                parameters {
                    query("property_id") { description = "Restrict results to guests of this property" }
                    query("channel_id") { description = "Substring match on the booking's external channel reference" }
                    query("query") { description = "Free-text search across guest name, document, etc." }
                    query("page") { description = "Zero-based page index (default 0)" }
                    query("size") { description = "Page size (max 100)" }
                }
                responses {
                    HttpStatusCode.OK {
                        description = "Paginated guests with hydrated booking/property context"
                        schema = jsonSchema<GuestsResponse>()
                    }
                    HttpStatusCode.Unauthorized { description = "The request is not authenticated" }
                }
            }

            // Owner-side guest registration on a booking. Mirrors the public check-in
            // POST but is gated by Owner/Manager property access, so the property
            // operator can backfill or fix guest details without sharing the public
            // link with the guest.
            post("/bookings/{id}/guests") {
                val user = call.getKasifyUser()
                val booking = call.getPathBooking(bookingsRepository)
                val property = call.getProperty(propertiesRepository, booking.propertyId)
                requireOwnerOrManager(authorizationService, user, property)

                val request = call.receive<GuestRegistrationRequest>()
                val created = checkinService.createGuestForBooking(booking, property, request)
                val hydrated = guestHydrationService.hydrate(created)
                call.respond(HttpStatusCode.Created, hydrated)
            }.describe {
                tag("Guests")
                summary = "Register guest on a booking (owner-side)"
                description = "Authenticated, Owner/Manager only. Same SES Hospedajes validation as the public flow."
                parameters {
                    path("id") { description = "Booking id" }
                }
                requestBody {
                    description = "Guest details conforming to SES Hospedajes requirements"
                    required = true
                    schema = jsonSchema<GuestRegistrationRequest>()
                }
                responses {
                    HttpStatusCode.Created {
                        description = "The created guest"
                        schema = jsonSchema<Guest>()
                    }
                    HttpStatusCode.BadRequest {
                        description = "Validation failed, check-in closed (`checkin.closed`), booking full (`checkin.booking_full`), or duplicate document (`checkin.duplicate_document`)"
                    }
                    HttpStatusCode.Forbidden { description = "Caller is not Owner or Manager of the booking's property" }
                    HttpStatusCode.NotFound { description = "Booking or property not found" }
                }
            }

            patch("/guests/{id}") {
                val user = call.getKasifyUser()
                val guestId = call.getGuestIdPath()
                val guest =
                    guestsRepository.getGuest(guestId)
                        ?: throw NotFoundException(Errors.ERROR_GUEST_NOT_FOUND, code = Errors.CODE_GUEST_NOT_FOUND)
                val booking =
                    bookingsRepository.getBooking(guest.bookingId)
                        ?: throw NotFoundException(Errors.ERROR_BOOKING_NOT_FOUND)
                val property = call.getProperty(propertiesRepository, booking.propertyId)
                requireOwnerOrManager(authorizationService, user, property)
                requireNotSesSubmitted(communicationsRepository, booking)

                val request = call.receive<GuestRegistrationRequest>()
                val updated = checkinService.replaceGuestForBooking(guest, booking, request)
                val hydrated = guestHydrationService.hydrate(updated)
                call.respond(hydrated)
            }.describe {
                tag("Guests")
                summary = "Update guest (owner-side)"
                description = "Authenticated, Owner/Manager only. Replaces all editable fields with the values in the request."
                parameters {
                    path("id") { description = "Guest id" }
                }
                requestBody {
                    description = "Full guest record (same shape as registration)"
                    required = true
                    schema = jsonSchema<GuestRegistrationRequest>()
                }
                responses {
                    HttpStatusCode.OK {
                        description = "The updated guest"
                        schema = jsonSchema<Guest>()
                    }
                    HttpStatusCode.BadRequest {
                        description = "Validation failed or duplicate document (`checkin.duplicate_document`)"
                    }
                    HttpStatusCode.Conflict {
                        description = "Guest data already submitted to SES Hospedajes and locked (`guest.ses_locked`)"
                    }
                    HttpStatusCode.Forbidden { description = "Caller is not Owner or Manager of the guest's property" }
                    HttpStatusCode.NotFound { description = "Guest not found (`guest.not_found`)" }
                }
            }

            delete("/guests/{id}") {
                val user = call.getKasifyUser()
                val guestId = call.getGuestIdPath()
                val guest =
                    guestsRepository.getGuest(guestId)
                        ?: throw NotFoundException(Errors.ERROR_GUEST_NOT_FOUND, code = Errors.CODE_GUEST_NOT_FOUND)
                val booking =
                    bookingsRepository.getBooking(guest.bookingId)
                        ?: throw NotFoundException(Errors.ERROR_BOOKING_NOT_FOUND)
                val property = call.getProperty(propertiesRepository, booking.propertyId)
                requireOwnerOrManager(authorizationService, user, property)
                requireNotSesSubmitted(communicationsRepository, booking)

                if (!guestsRepository.deleteGuest(guestId)) {
                    throw NotFoundException(Errors.ERROR_GUEST_NOT_FOUND, code = Errors.CODE_GUEST_NOT_FOUND)
                }
                call.respond(HttpStatusCode.NoContent)
            }.describe {
                tag("Guests")
                summary = "Delete guest (owner-side)"
                description =
                    "Authenticated, Owner/Manager only. Permanently removes the guest. Blocked once the booking has " +
                    "been submitted to SES Hospedajes, since that data must be retained for legal compliance."
                parameters {
                    path("id") { description = "Guest id" }
                }
                responses {
                    HttpStatusCode.NoContent { description = "The guest was deleted" }
                    HttpStatusCode.Conflict {
                        description = "Guest data already submitted to SES Hospedajes and locked (`guest.ses_locked`)"
                    }
                    HttpStatusCode.Forbidden { description = "Caller is not Owner or Manager of the guest's property" }
                    HttpStatusCode.NotFound { description = "Guest not found (`guest.not_found`)" }
                }
            }
        }
    }
}

/**
 * Guards owner-side guest mutations: once a booking's guest data has been submitted to
 * SES Hospedajes, the Spanish registry requires it be retained for 3 years, so edits and
 * deletes are rejected.
 */
private suspend fun requireNotSesSubmitted(
    communicationsRepository: SesCommunicationsRepository,
    booking: eu.kasify.model.Booking,
) {
    if (communicationsRepository.isPvAltaFiled(booking.id)) {
        throw ConflictException(Errors.ERROR_GUEST_SES_LOCKED, code = Errors.CODE_GUEST_SES_LOCKED)
    }
}

private fun io.ktor.server.application.ApplicationCall.getGuestIdPath(): Uuid {
    val id = parameters["id"] ?: throw MissingRequestParameterException("Missing guest ID")
    return try {
        Uuid.parse(id)
    } catch (_: Exception) {
        throw eu.kasify.routes.exceptions
            .BadParamRequestException("Guest ID has an incorrect format")
    }
}

private suspend fun requireOwnerOrManager(
    authorizationService: AuthorizationService,
    user: eu.kasify.security.KasifyUserPrincipal,
    property: eu.kasify.model.Property,
) {
    val access = authorizationService.getAccess(user, property)
    if (!access.isAccountMember && access.accessLevel != PropertyAccessLevel.Manager) {
        throw ForbiddenException("Owner or manager access required.")
    }
}
