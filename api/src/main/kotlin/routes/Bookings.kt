package eu.kasify.routes
import eu.kasify.authenticatedAndRateLimited
import eu.kasify.db.BookingAccessCodesRepository
import eu.kasify.db.BookingsRepository
import eu.kasify.db.GuestsRepository
import eu.kasify.db.PropertiesRepository
import eu.kasify.db.SesCommunicationsRepository
import eu.kasify.db.TasksRepository
import eu.kasify.db.model.PropertyAccessLevel
import eu.kasify.model.api.Booking
import eu.kasify.model.api.SecuritySerializer
import eu.kasify.model.api.requests.BookingData
import eu.kasify.model.api.requests.BookingsFilters
import eu.kasify.model.api.responses.BookingsResponse
import eu.kasify.model.api.toVisibility
import eu.kasify.routes.exceptions.ConflictException
import eu.kasify.routes.exceptions.InternalServerException
import eu.kasify.routes.exceptions.NotFoundException
import eu.kasify.routes.extensions.getBookingsFilters
import eu.kasify.routes.extensions.getPageableWithLimit
import eu.kasify.routes.extensions.getPathBooking
import eu.kasify.routes.extensions.getPathProperty
import eu.kasify.routes.extensions.getProperty
import eu.kasify.security.getKasifyUser
import eu.kasify.services.AuthorizationService
import eu.kasify.services.BookingService
import eu.kasify.services.GuestHydrationService
import eu.kasify.services.SmartlocksService
import eu.kasify.utils.createLocalTimeUtc
import eu.kasify.versionedRouting
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.HttpStatusCode
import io.ktor.openapi.jsonSchema
import io.ktor.server.application.Application
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.openapi.describe
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.utils.io.ExperimentalKtorApi
import io.perracodex.exposed.pagination.Pageable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.koin.ktor.ext.inject

@OptIn(ExperimentalKtorApi::class)
fun Application.configureBookingsRouting() {
    val service by inject<BookingsRepository>()
    val bookingService by inject<BookingService>()
    val authorizationService by inject<AuthorizationService>()
    val propertiesService by inject<PropertiesRepository>()
    val bookingAccessCodesRepository by inject<BookingAccessCodesRepository>()
    val guestsRepository by inject<GuestsRepository>()
    val communicationsRepository by inject<SesCommunicationsRepository>()
    val guestHydrationService by inject<GuestHydrationService>()
    val tasksRepository by inject<TasksRepository>()
    val smartlocksService by inject<SmartlocksService>()
    val logger = KotlinLogging.logger {}

    versionedRouting {
        authenticatedAndRateLimited {
            get("/properties/{id}/bookings/current") {
                val user = call.getKasifyUser()
                val property = call.getPathProperty(propertiesService)

                val access = authorizationService.getAccess(user, property)
                if (!access.hasAccess) {
                    throw eu.kasify.routes.exceptions
                        .ForbiddenException("Access to property denied.")
                }
                if (access.accessLevel == PropertyAccessLevel.Contractor) {
                    throw eu.kasify.routes.exceptions
                        .ForbiddenException("Booking access denied.")
                }

                val currentBooking =
                    service.getCurrentBooking(property.id)
                        ?: throw NotFoundException(
                            "No current booking found for this property",
                            code = "booking.no_current",
                        )

                val apiBooking = Booking.from(currentBooking, property, role = access.role)
                call.respond(SecuritySerializer.serialize(apiBooking, access))
            }.describe {
                tag("Bookings")
                summary = "Get current booking"
                parameters {
                    path("id") { description = "Property id" }
                }
                responses {
                    HttpStatusCode.OK {
                        description = "The currently ongoing booking for the property"
                        schema = jsonSchema<Booking>()
                    }
                    HttpStatusCode.Forbidden {
                        description = "Caller lacks access to the property, or is a contractor"
                    }
                    HttpStatusCode.NotFound {
                        description = "No current booking. Returns `booking.no_current`"
                    }
                }
            }

            get("/bookings") {
                val user = call.getKasifyUser()
                val pageable: Pageable = call.getPageableWithLimit()
                val filters: BookingsFilters = call.getBookingsFilters()

                val bookings = service.getBookingsForUser(user, filters, pageable)

                val propertyIds = bookings.content.map { it.propertyId }.distinct()
                val accessByProperty = authorizationService.getAccessForProperties(user, propertyIds)
                val commsByBooking =
                    communicationsRepository.listByBookings(bookings.content.map { it.id })

                val serializedBookings =
                    JsonArray(
                        bookings.content.mapNotNull { dbBooking ->
                            val access = accessByProperty[dbBooking.propertyId] ?: return@mapNotNull null
                            val apiBooking =
                                Booking.from(
                                    dbBooking,
                                    role = access.role,
                                    sesCommunications = commsByBooking[dbBooking.id],
                                )
                            SecuritySerializer.serialize(apiBooking, access)
                        },
                    )

                call.respond(
                    buildJsonObject {
                        put("bookings", serializedBookings)
                        put("page", JsonPrimitive(bookings.details.pageIndex ?: 0))
                        put("size", JsonPrimitive(bookings.details.elementsPerPage ?: 0))
                        put("total", JsonPrimitive(bookings.details.totalElements ?: 0))
                    },
                )
            }.describe {
                tag("Bookings")
                summary = "List bookings"
                parameters {
                    query("page") { description = "Zero-based page index (default 0)" }
                    query("size") { description = "Page size (max 100)" }
                    query("from") { description = "Filter bookings starting on or after this date (ISO-8601)" }
                    query("to") { description = "Filter bookings ending on or before this date (ISO-8601)" }
                    query("propertyId") { description = "Restrict to a single property" }
                    query("channel") { description = "Filter by booking channel" }
                    query("query") { description = "Free-text search across guest fields and notes" }
                    query("sortBy") { description = "Sort field: `CHECKIN` or `CHECKOUT`" }
                    query("sortDescending") { description = "Sort descending when true" }
                }
                responses {
                    HttpStatusCode.OK {
                        description = "Paginated bookings across accessible properties"
                        schema = jsonSchema<BookingsResponse>()
                    }
                    HttpStatusCode.Unauthorized { description = "The request is not authenticated" }
                }
            }

            get("/properties/{id}/bookings") {
                val user = call.getKasifyUser()
                val property = call.getPathProperty(propertiesService)
                val pageable: Pageable = call.getPageableWithLimit()
                val filters: BookingsFilters = call.getBookingsFilters()

                val access = authorizationService.getAccess(user, property)
                if (!access.hasAccess) {
                    throw eu.kasify.routes.exceptions
                        .ForbiddenException("Access to property denied.")
                }
                if (access.accessLevel == PropertyAccessLevel.Contractor) {
                    throw eu.kasify.routes.exceptions
                        .ForbiddenException("Booking access denied.")
                }

                val bookings = service.getBookingsByProperty(property.id, access.toVisibility(), filters, pageable)
                val apiBookings = bookings.content.map { Booking.from(it, role = access.role) }
                val serializedBookings = SecuritySerializer.serialize(apiBookings, access)

                call.respond(
                    buildJsonObject {
                        put("bookings", serializedBookings)
                        put("page", JsonPrimitive(bookings.details.pageIndex ?: 0))
                        put("size", JsonPrimitive(bookings.details.elementsPerPage ?: 0))
                        put("total", JsonPrimitive(bookings.details.totalElements ?: 0))
                    },
                )
            }.describe {
                tag("Bookings")
                summary = "List property bookings"
                parameters {
                    path("id") { description = "Property id" }
                    query("page") { description = "Zero-based page index (default 0)" }
                    query("size") { description = "Page size (max 100)" }
                    query("from") { description = "Filter bookings starting on or after this date (ISO-8601)" }
                    query("to") { description = "Filter bookings ending on or before this date (ISO-8601)" }
                    query("channel") { description = "Filter by booking channel" }
                    query("query") { description = "Free-text search across guest fields and notes" }
                    query("sortBy") { description = "Sort field: `CHECKIN` or `CHECKOUT`" }
                    query("sortDescending") { description = "Sort descending when true" }
                }
                responses {
                    HttpStatusCode.OK {
                        description = "Paginated bookings for the property"
                        schema = jsonSchema<BookingsResponse>()
                    }
                    HttpStatusCode.Forbidden {
                        description = "Caller lacks access to the property, or is a contractor"
                    }
                }
            }

            post("/properties/{id}/bookings") {
                val user = call.getKasifyUser()
                val property = call.getPathProperty(propertiesService)

                // Only owners can create bookings
                authorizationService.requireOwner(user, property)

                // Validation plugin handles all validation including required dates
                val bookingRequest = call.receive<BookingData>()
                require(bookingRequest.from != null) { "Check-in date (from) is required" }
                require(bookingRequest.to != null) { "Check-out date (to) is required" }

                // Dates are already validated in the model, so it's safe to force with !!
                val existingBookings = service.count(property.id, bookingRequest.from, bookingRequest.to)
                if (existingBookings > 0) {
                    throw ConflictException(Errors.ERROR_BOOKING_DATES_CONFLICT, code = "booking.dates_conflict")
                }

                val result = bookingService.createBookingWithCleaningTask(bookingRequest, property.id)
                call.respond(Booking.from(result, property, role = "owner"))
            }.describe {
                tag("Bookings")
                summary = "Create booking"
                parameters {
                    path("id") { description = "Property id" }
                }
                requestBody {
                    description = "Booking data. `from` and `to` are required."
                    required = true
                    schema = jsonSchema<BookingData>()
                }
                responses {
                    HttpStatusCode.OK {
                        description = "The created booking"
                        schema = jsonSchema<Booking>()
                    }
                    HttpStatusCode.BadRequest {
                        description = "Missing or invalid fields (e.g. `from` or `to` not supplied)"
                    }
                    HttpStatusCode.Forbidden {
                        description = "Caller is not the property owner"
                    }
                    HttpStatusCode.Conflict {
                        description = "Overlapping booking exists. Returns `booking.dates_conflict`"
                    }
                }
            }

            get("/bookings/{id}") {
                val user = call.getKasifyUser()
                val booking = call.getPathBooking(service)
                val property = call.getProperty(propertiesService, booking.propertyId)

                val access = authorizationService.getAccess(user, property)
                if (!access.hasAccess) {
                    throw eu.kasify.routes.exceptions
                        .ForbiddenException("Access to property denied.")
                }
                if (access.accessLevel == PropertyAccessLevel.Contractor) {
                    throw eu.kasify.routes.exceptions
                        .ForbiddenException("Booking access denied.")
                }

                val accessCodes = bookingAccessCodesRepository.getByBookingId(booking.id)
                val guests = guestsRepository.getAllGuestsByBooking(booking.id)
                val apiGuests = guestHydrationService.hydrate(guests)
                val cleaningTask =
                    tasksRepository
                        .getCleaningTaskByBookingId(booking.id)
                        ?.takeIf { authorizationService.canSeeCleaningTask(access, user.userId, it.assignedTo) }
                val sesCommunications = communicationsRepository.listByBooking(booking.id)
                val apiBooking =
                    Booking.from(
                        booking,
                        property,
                        accessCodes,
                        apiGuests,
                        cleaningTask,
                        sesCommunications = sesCommunications,
                        role = access.role,
                    )
                call.respond(SecuritySerializer.serialize(apiBooking, access))
            }.describe {
                tag("Bookings")
                summary = "Get booking"
                parameters {
                    path("id") { description = "Booking id" }
                }
                responses {
                    HttpStatusCode.OK {
                        description = "The booking, including access codes, guests, and cleaning task when visible"
                        schema = jsonSchema<Booking>()
                    }
                    HttpStatusCode.Forbidden {
                        description = "Caller lacks access to the property, or is a contractor"
                    }
                    HttpStatusCode.NotFound { description = "Booking not found" }
                }
            }

            patch("/bookings/{id}") {
                val user = call.getKasifyUser()
                val booking = call.getPathBooking(service)
                val bookingRequest = call.receive<BookingData>()
                val property = call.getProperty(propertiesService, booking.propertyId)

                // Only owners can modify bookings
                authorizationService.requireOwner(user, property)

                bookingService.updateBooking(booking.id, bookingRequest)

                val updatedBooking =
                    service.getBooking(booking.id)
                        ?: throw InternalServerException("Failed to retrieve updated booking")

                val datesChanged =
                    bookingRequest.from != null ||
                        bookingRequest.to != null ||
                        bookingRequest.checkinTime != null ||
                        bookingRequest.checkoutTime != null
                if (datesChanged) {
                    try {
                        val fromUtc = createLocalTimeUtc(updatedBooking.from, updatedBooking.checkinTime, property.tzone)
                        val toUtc = createLocalTimeUtc(updatedBooking.to, updatedBooking.checkoutTime, property.tzone)
                        smartlocksService.updateAccessCodeDatesForBooking(
                            bookingId = updatedBooking.id,
                            accountId = property.accountId,
                            fromUtc = fromUtc,
                            toUtc = toUtc,
                        )
                    } catch (e: Exception) {
                        logger.error(e) { "Failed to propagate booking ${updatedBooking.id} date change to smart-lock access codes" }
                    }
                }

                val accessCodes = bookingAccessCodesRepository.getByBookingId(updatedBooking.id)
                val guests = guestsRepository.getAllGuestsByBooking(updatedBooking.id)
                val apiGuests = guestHydrationService.hydrate(guests)
                val cleaningTask = tasksRepository.getCleaningTaskByBookingId(updatedBooking.id)
                val sesCommunications = communicationsRepository.listByBooking(updatedBooking.id)
                val apiBooking =
                    Booking.from(
                        updatedBooking,
                        property,
                        accessCodes,
                        apiGuests,
                        cleaningTask,
                        sesCommunications = sesCommunications,
                        role = "owner",
                    )
                call.respond(apiBooking)
            }.describe {
                tag("Bookings")
                summary = "Update booking"
                parameters {
                    path("id") { description = "Booking id" }
                }
                requestBody {
                    description = "Fields to update on the booking. All fields are optional."
                    required = true
                    schema = jsonSchema<BookingData>()
                }
                responses {
                    HttpStatusCode.OK {
                        description = "The updated booking"
                        schema = jsonSchema<Booking>()
                    }
                    HttpStatusCode.Forbidden {
                        description = "Caller is not the property owner"
                    }
                    HttpStatusCode.NotFound { description = "Booking not found" }
                    HttpStatusCode.Conflict {
                        description =
                            "Guest counts cannot be modified after the SES report has been " +
                            "submitted (code: booking.ses_already_submitted)"
                    }
                }
            }
        }
    }
}
