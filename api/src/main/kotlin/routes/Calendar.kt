package eu.kasify.routes

import eu.kasify.authenticatedAndRateLimited
import eu.kasify.db.CalendarEventsRepository
import eu.kasify.db.PropertiesRepository
import eu.kasify.db.TasksRepository
import eu.kasify.db.model.PropertyAccessLevel
import eu.kasify.model.api.SecuritySerializer
import eu.kasify.model.api.responses.CalendarResponse
import eu.kasify.model.api.responses.PropertyCalendarEventsResponse
import eu.kasify.routes.exceptions.BadParamRequestException
import eu.kasify.routes.extensions.getPathProperty
import eu.kasify.security.getKasifyUser
import eu.kasify.services.AuthorizationService
import eu.kasify.versionedRouting
import io.ktor.http.HttpStatusCode
import io.ktor.openapi.jsonSchema
import io.ktor.server.application.Application
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.openapi.describe
import io.ktor.utils.io.ExperimentalKtorApi
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toJavaZoneId
import kotlinx.datetime.toKotlinLocalDate
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.koin.ktor.ext.inject
import kotlin.uuid.Uuid

private const val MAX_CALENDAR_RANGE_DAYS = 90L
private val CALENDAR_DEFAULT_ZONE = TimeZone.of("Europe/Madrid")

@OptIn(ExperimentalKtorApi::class)
fun Application.configureCalendarRouting() {
    val calendarService by inject<CalendarEventsRepository>()
    val tasksRepository by inject<TasksRepository>()
    val authorizationService by inject<AuthorizationService>()
    val propertiesService by inject<PropertiesRepository>()

    versionedRouting {
        authenticatedAndRateLimited {
            get("/properties/{id}/calendar") {
                val user = call.getKasifyUser()
                val property = call.getPathProperty(propertiesService)

                val access = authorizationService.getAccess(user, property)
                if (!access.hasAccess) {
                    throw eu.kasify.routes.exceptions
                        .ForbiddenException("Access to property denied.")
                }

                val (from, to) = call.resolveCalendarWindow()

                val propertyId = property.id
                val rawEvents =
                    calendarService
                        .getCalendarEventsForProperties(
                            propertyIds = listOf(propertyId),
                            from = from,
                            to = to,
                        )[propertyId]
                        .orEmpty()

                val filteredEvents =
                    if (access.accessLevel == PropertyAccessLevel.Contractor) {
                        val userTaskCalendarEventIds =
                            tasksRepository
                                .listByAssignee(user.userId)
                                .content
                                .map { it.calendarEventId }
                                .toSet()
                        rawEvents.filter { it.id in userTaskCalendarEventIds }
                    } else {
                        rawEvents
                    }

                val serializedEvents =
                    redactForeignTasks(
                        events = SecuritySerializer.serialize(filteredEvents, access),
                        accessLevel = access.accessLevel,
                        isAccountMember = access.isAccountMember,
                        currentUserId = user.userId,
                    )

                call.respond(
                    buildJsonObject {
                        put("events", serializedEvents)
                        put("from", JsonPrimitive(from.toString()))
                        put("to", JsonPrimitive(to.toString()))
                    },
                )
            }.describe {
                tag("Calendar")
                summary = "List property calendar events"
                description = "Returns bookings, cleaning tasks, and other calendar events for the property within the [from, to] window (defaults to today through one month from today). Contractors see only events tied to tasks assigned to them."
                parameters {
                    path("id") { description = "Property id" }
                    query("from") {
                        description = "Window start date, inclusive (YYYY-MM-DD). Optional, defaults to today."
                    }
                    query("to") {
                        description = "Window end date, inclusive (YYYY-MM-DD). Optional, defaults to one month after `from`. Window must not exceed 366 days."
                    }
                }
                responses {
                    HttpStatusCode.OK {
                        description = "Calendar events for the property within the window"
                        schema = jsonSchema<PropertyCalendarEventsResponse>()
                    }
                    HttpStatusCode.BadRequest {
                        description = "Invalid `from`/`to` or window > 366 days. Returns `calendar.invalid_range`."
                    }
                    HttpStatusCode.Forbidden { description = "Caller lacks access to the property" }
                }
            }

            get("/calendar") {
                val user = call.getKasifyUser()
                val (from, to) = call.resolveCalendarWindow()

                val properties = propertiesService.getProperties(user, null).content
                val propertyIds = properties.map { it.id }

                val accessByProperty = authorizationService.getAccessForProperties(user, propertyIds)

                val eventsByProperty =
                    calendarService.getCalendarEventsForProperties(
                        propertyIds = propertyIds,
                        from = from,
                        to = to,
                    )

                val hasContractorProperties =
                    accessByProperty.values.any { it.accessLevel == PropertyAccessLevel.Contractor }
                val userTaskCalendarEventIds =
                    if (hasContractorProperties) {
                        tasksRepository
                            .listByAssignee(user.userId)
                            .content
                            .map { it.calendarEventId }
                            .toSet()
                    } else {
                        emptySet()
                    }

                val rowsJson =
                    buildJsonArray {
                        for (property in properties) {
                            val pid = property.id
                            val rawEvents = eventsByProperty[pid].orEmpty()
                            val accessLevel = accessByProperty[pid]?.accessLevel
                            val filtered =
                                if (accessLevel == PropertyAccessLevel.Contractor) {
                                    rawEvents.filter { it.id in userTaskCalendarEventIds }
                                } else {
                                    rawEvents
                                }
                            val access = accessByProperty[pid]
                            val serialized =
                                redactForeignTasks(
                                    events =
                                        SecuritySerializer.serializeWithPropertyAccess(
                                            items = filtered,
                                            getPropertyId = { it.propertyId },
                                            accessByProperty = accessByProperty,
                                        ),
                                    accessLevel = access?.accessLevel,
                                    isAccountMember = access?.isAccountMember ?: false,
                                    currentUserId = user.userId,
                                )
                            add(
                                buildJsonObject {
                                    put("id", JsonPrimitive(pid.toString()))
                                    put("name", JsonPrimitive(property.name))
                                    put("events", serialized)
                                },
                            )
                        }
                    }

                call.respond(
                    buildJsonObject {
                        put("properties", rowsJson)
                        put("from", JsonPrimitive(from.toString()))
                        put("to", JsonPrimitive(to.toString()))
                    },
                )
            }.describe {
                tag("Calendar")
                summary = "List calendar events grouped by property"
                description = "Returns the caller's accessible properties, with each property's bookings and tasks for the requested date window (defaults to today through one month from today). Contractor properties only include events tied to the caller's assigned tasks."
                parameters {
                    query("from") {
                        description = "Window start date, inclusive (YYYY-MM-DD). Optional, defaults to today."
                    }
                    query("to") {
                        description = "Window end date, inclusive (YYYY-MM-DD). Optional, defaults to one month after `from`. Window must not exceed 366 days."
                    }
                }
                responses {
                    HttpStatusCode.OK {
                        description = "Properties accessible to the caller, each with its events for the window"
                        schema = jsonSchema<CalendarResponse>()
                    }
                    HttpStatusCode.BadRequest {
                        description = "Invalid `from`/`to` or window > 366 days. Returns `calendar.invalid_range`."
                    }
                    HttpStatusCode.Unauthorized { description = "The request is not authenticated" }
                }
            }
        }
    }
}

/**
 * For Staff/Contractor crew, replace the nested `task` object with `{ "id": ... }`
 * on any event whose task is not assigned to the current user. Owners (account
 * members) and Managers see the full task object.
 */
private fun redactForeignTasks(
    events: JsonArray,
    accessLevel: PropertyAccessLevel?,
    isAccountMember: Boolean,
    currentUserId: Uuid,
): JsonArray {
    if (isAccountMember || accessLevel == PropertyAccessLevel.Manager) return events

    val currentUserIdStr = currentUserId.toString()
    return JsonArray(
        events.map { element ->
            if (element !is JsonObject) return@map element
            val task = element["task"] as? JsonObject ?: return@map element
            val assignedTo = task["assigned_to"]?.jsonPrimitive?.content
            if (assignedTo == currentUserIdStr) return@map element

            val taskId = task["id"] ?: return@map element
            JsonObject(element.toMutableMap().apply { put("task", JsonObject(mapOf("id" to taskId))) })
        },
    )
}

private fun io.ktor.server.application.ApplicationCall.parseOptionalDateParam(name: String): LocalDate? {
    val raw = request.queryParameters[name]?.takeIf { it.isNotBlank() } ?: return null
    return try {
        LocalDate.parse(raw)
    } catch (_: IllegalArgumentException) {
        throw BadParamRequestException(
            "`$name` must be a date in YYYY-MM-DD format.",
            code = "calendar.invalid_range",
        )
    }
}

/**
 * Resolves the calendar `from`/`to` window from query params, applying defaults
 * (today through one month from today) when either side is missing, and validating
 * the resulting range. Returns the inclusive [from, to] pair.
 */
private fun io.ktor.server.application.ApplicationCall.resolveCalendarWindow(): Pair<LocalDate, LocalDate> {
    val today =
        java.time.LocalDate
            .now(CALENDAR_DEFAULT_ZONE.toJavaZoneId())
            .toKotlinLocalDate()
    val from = parseOptionalDateParam("from") ?: today
    val to = parseOptionalDateParam("to") ?: from.plus(DatePeriod(months = 1))

    if (to < from) {
        throw BadParamRequestException(
            "`to` must be on or after `from`.",
            code = "calendar.invalid_range",
        )
    }
    val rangeDays = from.toJavaLocalDate().until(to.toJavaLocalDate(), java.time.temporal.ChronoUnit.DAYS) + 1
    if (rangeDays > MAX_CALENDAR_RANGE_DAYS) {
        throw BadParamRequestException(
            "Calendar window must not exceed $MAX_CALENDAR_RANGE_DAYS days.",
            code = "calendar.invalid_range",
        )
    }
    return from to to
}
