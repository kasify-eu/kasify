package eu.kasify.routes

import eu.kasify.authenticatedAndRateLimited
import eu.kasify.db.BookingAccessCodesRepository
import eu.kasify.db.BookingsRepository
import eu.kasify.db.GuestsRepository
import eu.kasify.db.PropertiesRepository
import eu.kasify.db.PropertyChannelIntegrationsRepository
import eu.kasify.db.SesCommunicationsRepository
import eu.kasify.db.SmartlocksRepository
import eu.kasify.db.TasksRepository
import eu.kasify.db.UsersRepository
import eu.kasify.model.api.AdminGuest
import eu.kasify.model.api.AdminProperty
import eu.kasify.model.api.Calendar
import eu.kasify.model.api.User
import eu.kasify.model.api.responses.AdminBookingsPageResponse
import eu.kasify.model.api.responses.AdminGuestsPageResponse
import eu.kasify.model.api.responses.AdminPropertiesPageResponse
import eu.kasify.model.api.responses.AdminUsersPageResponse
import eu.kasify.routes.exceptions.BadParamRequestException
import eu.kasify.routes.extensions.getBookingsFilters
import eu.kasify.routes.extensions.getConfiguredFilter
import eu.kasify.routes.extensions.getGuestsFilters
import eu.kasify.routes.extensions.getPageableWithLimit
import eu.kasify.routes.extensions.getPathBooking
import eu.kasify.routes.extensions.getProperty
import eu.kasify.security.requireRole
import eu.kasify.services.GuestHydrationService
import eu.kasify.versionedRouting
import io.ktor.http.HttpStatusCode
import io.ktor.openapi.jsonSchema
import io.ktor.server.application.Application
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.openapi.describe
import io.ktor.server.routing.openapi.hide
import io.ktor.utils.io.ExperimentalKtorApi
import io.perracodex.exposed.pagination.Pageable
import org.koin.ktor.ext.inject
import kotlin.uuid.Uuid
import eu.kasify.model.api.Booking as ApiBooking

/**
 * Platform-admin read-only listing endpoints (properties, bookings, guests) that back the
 * admin visibility views. Like the other admin routes, these bypass `property_access`
 * — admins are platform staff, not crew — and rely on plain serialization to expose all
 * `@VisibleTo` fields (the annotation is only enforced by `SecuritySerializer`, which we
 * deliberately skip here).
 */
@OptIn(ExperimentalKtorApi::class)
fun Application.configureAdminListsRouting() {
    val propertiesRepository by inject<PropertiesRepository>()
    val bookingsRepository by inject<BookingsRepository>()
    val guestsRepository by inject<GuestsRepository>()
    val usersRepository by inject<UsersRepository>()
    val bookingAccessCodesRepository by inject<BookingAccessCodesRepository>()
    val tasksRepository by inject<TasksRepository>()
    val communicationsRepository by inject<SesCommunicationsRepository>()
    val propertyChannelIntegrationsRepository by inject<PropertyChannelIntegrationsRepository>()
    val smartlocksRepository by inject<SmartlocksRepository>()
    val guestHydrationService by inject<GuestHydrationService>()

    versionedRouting {
        authenticatedAndRateLimited {
            requireRole("admin") {
                get("/admin/users") {
                    val query = call.parameters["query"]?.takeIf { it.isNotBlank() }
                    val pageable: Pageable = call.getPageableWithLimit()

                    val users = usersRepository.getAllUsers(query, pageable)
                    val response =
                        AdminUsersPageResponse(
                            items = users.content.map { User.from(it) },
                            page = users.details.pageIndex,
                            size = users.details.elementsPerPage,
                            total = users.details.totalElements,
                        )

                    call.respond(response)
                }.describe {
                    tag("Admin")
                    summary = "List all users across accounts (admin)"
                    parameters {
                        query("page") { description = "Zero-based page index (default 0)" }
                        query("size") { description = "Page size (max 100)" }
                        query("query") { description = "Free-text across user name, last name and email" }
                    }
                    responses {
                        HttpStatusCode.OK {
                            description = "Paginated users"
                            schema = jsonSchema<AdminUsersPageResponse>()
                        }
                        HttpStatusCode.Unauthorized { description = "Not authenticated" }
                        HttpStatusCode.Forbidden { description = "Caller is not an admin" }
                    }
                }

                get("/admin/properties") {
                    val query = call.parameters["query"]?.takeIf { it.isNotBlank() }
                    val propertyId =
                        call.parameters["property_id"]?.let {
                            try {
                                Uuid.parse(it)
                            } catch (_: Exception) {
                                throw BadParamRequestException("Invalid 'property_id' format")
                            }
                        }
                    val pageable: Pageable = call.getPageableWithLimit()

                    val properties =
                        propertiesRepository.getAllPropertiesWithOwner(
                            query,
                            pageable,
                            propertyId,
                            sesConfigured = call.getConfiguredFilter("ses"),
                            nukiConfigured = call.getConfiguredFilter("nuki"),
                            unifiConfigured = call.getConfiguredFilter("unifi"),
                        )

                    // Enrich the page with each property's channel calendars and configured
                    // smart-lock vendors in single batch queries, grouped by property id, so
                    // the admin view can show integrations without an N+1 lookup per row.
                    val propertyIds = properties.content.map { it.id }
                    val calendarsByProperty =
                        propertyChannelIntegrationsRepository
                            .getIntegrationsByPropertyIds(propertyIds)
                            .groupBy({ it.propertyId }, { Calendar.from(it) })
                    val vendorsByProperty = smartlocksRepository.getVendorsByPropertyIds(propertyIds)

                    val response =
                        AdminPropertiesPageResponse(
                            items =
                                properties.content.map {
                                    AdminProperty.from(
                                        it,
                                        calendarsByProperty[it.id].orEmpty(),
                                        vendorsByProperty[it.id].orEmpty(),
                                    )
                                },
                            page = properties.details.pageIndex,
                            size = properties.details.elementsPerPage,
                            total = properties.details.totalElements,
                        )

                    call.respond(response)
                }.describe {
                    tag("Admin")
                    summary = "List all properties with their owner (admin)"
                    parameters {
                        query("page") { description = "Zero-based page index (default 0)" }
                        query("size") { description = "Page size (max 100)" }
                        query("query") { description = "Free-text across property name/city and owner name/email" }
                        query("property_id") { description = "Restrict to a single property" }
                        query("ses") { description = "Filter by SES state: 'configured' or 'not_configured'" }
                        query("nuki") { description = "Filter by Nuki smart-lock state: 'configured' or 'not_configured'" }
                        query("unifi") { description = "Filter by Unifi smart-lock state: 'configured' or 'not_configured'" }
                    }
                    responses {
                        HttpStatusCode.OK {
                            description = "Paginated properties with the user that created each and its channel calendars"
                            schema = jsonSchema<AdminPropertiesPageResponse>()
                        }
                        HttpStatusCode.Unauthorized { description = "Not authenticated" }
                        HttpStatusCode.Forbidden { description = "Caller is not an admin" }
                    }
                }

                get("/admin/bookings") {
                    val filters = call.getBookingsFilters()
                    val pageable: Pageable = call.getPageableWithLimit()

                    val bookings = bookingsRepository.getBookings(filters, pageable)
                    val commsByBooking =
                        communicationsRepository.listByBookings(bookings.content.map { it.id })
                    val response =
                        AdminBookingsPageResponse(
                            items =
                                bookings.content.map {
                                    ApiBooking.from(it, sesCommunications = commsByBooking[it.id])
                                },
                            page = bookings.details.pageIndex,
                            size = bookings.details.elementsPerPage,
                            total = bookings.details.totalElements,
                        )

                    call.respond(response)
                }.describe {
                    tag("Admin")
                    summary = "List all bookings across accounts (admin)"
                    parameters {
                        query("page") { description = "Zero-based page index (default 0)" }
                        query("size") { description = "Page size (max 100)" }
                        query("from") { description = "Filter by stay overlap range start (YYYY-MM-DD)" }
                        query("to") { description = "Filter by stay overlap range end (YYYY-MM-DD)" }
                        query("property_id") { description = "Restrict to a single property" }
                        query("query") { description = "Free-text across guest name, channel_id, ses_lote" }
                        query("channel") { description = "Filter by booking channel (Airbnb, Booking, Vrbo, Google, Direct)" }
                    }
                    responses {
                        HttpStatusCode.OK {
                            description = "Paginated bookings"
                            schema = jsonSchema<AdminBookingsPageResponse>()
                        }
                        HttpStatusCode.Unauthorized { description = "Not authenticated" }
                        HttpStatusCode.Forbidden { description = "Caller is not an admin" }
                    }
                }

                get("/admin/bookings/{id}") {
                    val booking = call.getPathBooking(bookingsRepository)
                    val property = call.getProperty(propertiesRepository, booking.propertyId)

                    val accessCodes = bookingAccessCodesRepository.getByBookingId(booking.id)
                    val guests = guestsRepository.getAllGuestsByBooking(booking.id)
                    val apiGuests = guestHydrationService.hydrate(guests)
                    val cleaningTask = tasksRepository.getCleaningTaskByBookingId(booking.id)
                    val sesCommunications = communicationsRepository.listByBooking(booking.id)

                    val apiBooking =
                        ApiBooking.from(
                            booking,
                            property,
                            accessCodes,
                            apiGuests,
                            cleaningTask,
                            sesCommunications = sesCommunications,
                            role = "owner",
                        )
                    call.respond(apiBooking)
                }.describe {
                    tag("Admin")
                    summary = "Get a single booking with full detail (admin)"
                    parameters {
                        path("id") { description = "Booking id" }
                    }
                    responses {
                        HttpStatusCode.OK {
                            description = "The booking, including access codes, guests, cleaning task, and SES state"
                            schema = jsonSchema<ApiBooking>()
                        }
                        HttpStatusCode.Unauthorized { description = "Not authenticated" }
                        HttpStatusCode.Forbidden { description = "Caller is not an admin" }
                        HttpStatusCode.NotFound { description = "Booking not found" }
                    }
                }

                get("/admin/guests") {
                    val filters = call.getGuestsFilters()
                    val pageable: Pageable = call.getPageableWithLimit()

                    val guests = guestsRepository.getAllGuests(filters, pageable)
                    val response =
                        AdminGuestsPageResponse(
                            items = guests.content.map { AdminGuest.from(it) },
                            page = guests.details.pageIndex,
                            size = guests.details.elementsPerPage,
                            total = guests.details.totalElements,
                        )

                    call.respond(response)
                }.describe {
                    tag("Admin")
                    summary = "List all guests across accounts (admin)"
                    parameters {
                        query("page") { description = "Zero-based page index (default 0)" }
                        query("size") { description = "Page size (max 100)" }
                        query("property_id") { description = "Restrict to a single property" }
                        query("channel_id") { description = "Substring match on the booking's external channel reference" }
                        query("query") { description = "Free-text across guest first/last name and email" }
                    }
                    responses {
                        HttpStatusCode.OK {
                            description = "Paginated guests with property and owner context"
                            schema = jsonSchema<AdminGuestsPageResponse>()
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
