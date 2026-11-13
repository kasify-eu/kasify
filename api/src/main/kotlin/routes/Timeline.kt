package eu.kasify.routes

import eu.kasify.authenticatedAndRateLimited
import eu.kasify.db.CalendarEventsRepository
import eu.kasify.db.PropertyAccessRepository
import eu.kasify.db.model.PropertyAccessLevel
import eu.kasify.db.model.PropertyService
import eu.kasify.model.PropertyAccess
import eu.kasify.model.TimelineEvent
import eu.kasify.model.api.SecuritySerializer
import eu.kasify.model.api.responses.ActivityType
import eu.kasify.model.api.responses.TimelineActivity
import eu.kasify.model.api.responses.TimelineTask
import eu.kasify.routes.extensions.getPageableWithLimit
import eu.kasify.security.getKasifyUser
import eu.kasify.services.AccessInfo
import eu.kasify.versionedRouting
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.openapi.describe
import io.ktor.utils.io.ExperimentalKtorApi
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.koin.ktor.ext.inject
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.uuid.Uuid

@OptIn(ExperimentalKtorApi::class)
fun Application.configureTimelineRouting() {
    val calendarEventsRepository by inject<CalendarEventsRepository>()
    val propertyAccessRepository by inject<PropertyAccessRepository>()

    versionedRouting {
        authenticatedAndRateLimited {
            get("/timeline") {
                val user = call.getKasifyUser()
                val today = LocalDate.now()

                // Query 1: Pre-fetch crew access levels
                val crewAccess = propertyAccessRepository.findByUserId(user.userId)
                val fullPropertyIds =
                    crewAccess
                        .filter { it.accessLevel == PropertyAccessLevel.Manager }
                        .map { it.propertyId }
                val limitedPropertyIds =
                    crewAccess
                        .filter { it.accessLevel == PropertyAccessLevel.Staff }
                        .map { it.propertyId }

                // Query 2: Get upcoming timeline events
                val events =
                    calendarEventsRepository.getUpcomingTimelineEvents(
                        accountId = user.accountId,
                        userId = user.userId,
                        fullPropertyIds = fullPropertyIds,
                        limitedPropertyIds = limitedPropertyIds,
                        limit = 20,
                    )

                // Build access map for SecuritySerializer PII filtering; events on properties
                // the caller neither owns nor holds a crew grant on are dropped.
                val accessByProperty = resolveTimelineAccess(user.accountId, crewAccess, events)

                // Generate timeline activities grouped by date
                val timelineDays = mutableMapOf<LocalDate, MutableList<TimelineActivity>>()

                events.filter { it.propertyId in accessByProperty }.forEach { event ->
                    when {
                        event.booking != null -> {
                            val checkInDate = event.fromDate
                            val checkOutDate = event.toDate
                            val apiBooking =
                                eu.kasify.model.api.Booking
                                    .from(event.booking)

                            if (!checkInDate.isBefore(today)) {
                                timelineDays.getOrPut(checkInDate) { mutableListOf() }.add(
                                    createBookingActivity(apiBooking, ActivityType.check_in, checkInDate, event.propertyId),
                                )
                            }

                            if (!checkOutDate.isBefore(today)) {
                                timelineDays.getOrPut(checkOutDate) { mutableListOf() }.add(
                                    createBookingActivity(apiBooking, ActivityType.check_out, checkOutDate, event.propertyId),
                                )
                            }

                            if (checkInDate.isBefore(today) && checkOutDate.isAfter(today)) {
                                timelineDays.getOrPut(today) { mutableListOf() }.add(
                                    createBookingActivity(
                                        apiBooking,
                                        ActivityType.current_guest,
                                        null,
                                        event.propertyId,
                                        checkOutDate,
                                    ),
                                )
                            }
                        }
                        event.taskId != null -> {
                            val activityType =
                                when (event.taskType) {
                                    PropertyService.Cleaning -> ActivityType.cleaning
                                    PropertyService.Maintenance -> ActivityType.maintenance
                                    else -> return@forEach
                                }
                            timelineDays.getOrPut(event.fromDate) { mutableListOf() }.add(
                                createTaskActivity(event, activityType),
                            )
                        }
                    }
                }

                // Sort, serialize with per-property PII filtering
                val timeline =
                    timelineDays.entries
                        .sortedBy { it.key }
                        .map { (date, activities) ->
                            val sortedActivities = activities.sortedBy { it.time }
                            val serializedActivities =
                                SecuritySerializer.serializeWithPropertyAccess(
                                    items = sortedActivities,
                                    getPropertyId = { Uuid.parse(it.propertyId) },
                                    accessByProperty = accessByProperty,
                                )
                            buildJsonObject {
                                put("date", date.toString())
                                put("date_offset", ChronoUnit.DAYS.between(today, date).toInt())
                                put("activities", serializedActivities)
                            }
                        }

                call.respond(buildJsonObject { put("timeline", JsonArray(timeline)) })
            }.describe {
                tag("Timeline")
                summary = "Get upcoming timeline"
                description = "Returns the next ~20 upcoming events across all properties the caller can access, grouped by date with activity items (check-ins, check-outs, current-guest notes, cleaning/maintenance tasks). PII on bookings is filtered per property role."
                responses {
                    HttpStatusCode.OK { description = "Upcoming timeline days with activities" }
                    HttpStatusCode.Unauthorized { description = "The request is not authenticated" }
                }
            }

            get("/activities") {
                val user = call.getKasifyUser()
                val pageable = call.getPageableWithLimit()
                val propertyIdParam = call.parameters["property_id"]?.let { Uuid.parse(it) }

                // Pre-fetch crew access levels
                val crewAccess = propertyAccessRepository.findByUserId(user.userId)
                val fullPropertyIds =
                    crewAccess
                        .filter { it.accessLevel == PropertyAccessLevel.Manager }
                        .map { it.propertyId }
                val limitedPropertyIds =
                    crewAccess
                        .filter { it.accessLevel == PropertyAccessLevel.Staff }
                        .map { it.propertyId }

                val (events, total) =
                    calendarEventsRepository.getPaginatedTimelineEvents(
                        accountId = user.accountId,
                        userId = user.userId,
                        fullPropertyIds = fullPropertyIds,
                        limitedPropertyIds = limitedPropertyIds,
                        pageable = pageable,
                        propertyId = propertyIdParam,
                    )

                // Build access map for SecuritySerializer PII filtering; events on properties
                // the caller neither owns nor holds a crew grant on are dropped.
                val accessByProperty = resolveTimelineAccess(user.accountId, crewAccess, events)

                // One activity per booking, one per task
                val activities = mutableListOf<TimelineActivity>()

                events.filter { it.propertyId in accessByProperty }.forEach { event ->
                    when {
                        event.booking != null -> {
                            val apiBooking =
                                eu.kasify.model.api.Booking
                                    .from(event.booking)
                            activities.add(
                                createBookingActivity(apiBooking, ActivityType.booking, event.fromDate, event.propertyId, event.toDate)
                                    .copy(date = event.fromDate.toString()),
                            )
                        }
                        event.taskId != null -> {
                            val activityType =
                                when (event.taskType) {
                                    PropertyService.Cleaning -> ActivityType.cleaning
                                    PropertyService.Maintenance -> ActivityType.maintenance
                                    else -> return@forEach
                                }
                            activities.add(
                                createTaskActivity(event, activityType).copy(date = event.fromDate.toString()),
                            )
                        }
                    }
                }

                val serializedActivities =
                    SecuritySerializer.serializeWithPropertyAccess(
                        items = activities,
                        getPropertyId = { Uuid.parse(it.propertyId) },
                        accessByProperty = accessByProperty,
                    )

                val page = pageable?.page ?: 0
                val size = pageable?.size ?: 20

                call.respond(
                    buildJsonObject {
                        put("activities", serializedActivities)
                        put("page", page)
                        put("size", size)
                        put("total", total)
                    },
                )
            }.describe {
                tag("Timeline")
                summary = "List activities"
                description = "Flat, paginated list of timeline activities (bookings and tasks) across accessible properties, optionally filtered to a single property. PII on bookings is filtered per property role."
                parameters {
                    query("property_id") { description = "Restrict results to a single property" }
                    query("page") { description = "Zero-based page index (default 0)" }
                    query("size") { description = "Page size (max 100, default 20)" }
                }
                responses {
                    HttpStatusCode.OK { description = "Paginated activities with `page`, `size`, `total`" }
                    HttpStatusCode.Unauthorized { description = "The request is not authenticated" }
                }
            }
        }
    }
}

/**
 * Resolve the caller's access for each property in [events]. A crew grant takes precedence,
 * then ownership through the caller's account. A property with neither gets no entry, so its
 * events must be dropped instead of being serialized with owner-level visibility.
 */
private fun resolveTimelineAccess(
    accountId: Uuid,
    crewAccess: List<PropertyAccess>,
    events: List<TimelineEvent>,
): Map<Uuid, AccessInfo> {
    val crewAccessByProperty = crewAccess.associateBy { it.propertyId }
    return events
        .distinctBy { it.propertyId }
        .mapNotNull { event ->
            val grant = crewAccessByProperty[event.propertyId]
            val access =
                when {
                    grant != null ->
                        AccessInfo(
                            hasAccess = true,
                            role = grant.accessLevel.dbValue,
                            isAccountMember = false,
                            accessLevel = grant.accessLevel,
                        )

                    event.accountId == accountId ->
                        AccessInfo(hasAccess = true, role = "owner", isAccountMember = true)

                    else -> null
                }
            access?.let { event.propertyId to it }
        }.toMap()
}

private fun createBookingActivity(
    booking: eu.kasify.model.api.Booking,
    type: ActivityType,
    activityDate: LocalDate?,
    propertyId: Uuid,
    endDate: LocalDate? = null,
): TimelineActivity =
    TimelineActivity(
        id = "${booking.id}-${type.name}",
        type = type,
        time =
            activityDate?.let {
                when (type) {
                    ActivityType.check_in -> booking.checkinTime?.format(DateTimeFormatter.ofPattern("HH:mm"))
                    ActivityType.check_out -> booking.checkoutTime?.format(DateTimeFormatter.ofPattern("HH:mm"))
                    else -> null
                }
            },
        endDate = endDate?.toString(),
        propertyId = propertyId.toString(),
        booking = booking,
    )

private fun createTaskActivity(
    event: TimelineEvent,
    type: ActivityType,
): TimelineActivity =
    TimelineActivity(
        id = "${event.taskId}-${type.name}",
        type = type,
        time = event.scheduledStartTime?.format(DateTimeFormatter.ofPattern("HH:mm")),
        endTime = event.scheduledEndTime?.format(DateTimeFormatter.ofPattern("HH:mm")),
        propertyId = event.propertyId.toString(),
        task =
            TimelineTask(
                id = event.taskId.toString(),
                propertyName = event.propertyName,
                status = event.taskStatus?.name ?: "pending",
                assignedToName = event.assignedToName,
                notes = event.taskNotes,
            ),
    )
