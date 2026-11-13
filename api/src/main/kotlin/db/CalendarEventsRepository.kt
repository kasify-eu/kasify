package eu.kasify.db
import eu.kasify.db.model.BookingStatus
import eu.kasify.db.model.BookingsTable
import eu.kasify.db.model.CalendarEventsTable
import eu.kasify.db.model.EventType
import eu.kasify.db.model.PropertiesTable
import eu.kasify.db.model.PropertyAccessTable
import eu.kasify.db.model.TaskStatus
import eu.kasify.db.model.TasksTable
import eu.kasify.db.model.UsersTable
import eu.kasify.model.Booking
import eu.kasify.model.TimelineEvent
import eu.kasify.model.api.CalendarEvent
import eu.kasify.security.KasifyUserPrincipal
import io.perracodex.exposed.pagination.Page
import io.perracodex.exposed.pagination.Pageable
import io.perracodex.exposed.pagination.paginate
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toJavaLocalDate
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.inSubQuery
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import kotlin.time.Clock
import kotlin.uuid.Uuid

class CalendarEventsRepository {
    /**
     * Get calendar events for a specific property.
     * Includes booking and task information via LEFT JOINs.
     */
    suspend fun getCalendarEvents(
        user: KasifyUserPrincipal,
        propertyId: Uuid,
        pageable: Pageable?,
    ): Page<CalendarEvent> =
        dbQuery {
            CalendarEventsTable
                .join(PropertiesTable, JoinType.INNER, CalendarEventsTable.propertyId, PropertiesTable.id)
                .join(BookingsTable, JoinType.LEFT, CalendarEventsTable.id, BookingsTable.calendarEventId)
                .join(TasksTable, JoinType.LEFT, CalendarEventsTable.id, TasksTable.calendarEventId)
                .join(UsersTable, JoinType.LEFT, TasksTable.assignedTo, UsersTable.id)
                .selectAll()
                .where {
                    (CalendarEventsTable.propertyId eq propertyId) and
                        // Exclude cancelled tasks
                        (TasksTable.cancelledAt.isNull() or TasksTable.id.isNull())
                }.paginate(
                    pageable = pageable,
                    map = CalendarEvent.Companion,
                )
        }

    /**
     * Get today's events for the user.
     * Returns events from:
     * - Properties owned by the user's account
     * - Properties the user has crew access to
     * Includes booking and task information via LEFT JOINs.
     */
    suspend fun getTodayEvents(
        user: KasifyUserPrincipal,
        pageable: Pageable?,
    ): Page<CalendarEvent> =
        dbQuery {
            val madridZone = TimeZone.Companion.of("Europe/Madrid")
            val now =
                Clock.System
                    .now()
                    .toJavaInstant()
                    .atZone(madridZone.toJavaZoneId())
            val startOfDay = now.toLocalDate().atStartOfDay(madridZone.toJavaZoneId()).toOffsetDateTime()
            val endOfDay = startOfDay.plusDays(1).minusSeconds(1)

            // Subquery to get property IDs the user has crew access to
            val crewPropertyIds =
                PropertyAccessTable
                    .select(PropertyAccessTable.propertyId)
                    .where { PropertyAccessTable.userId eq user.userId }

            CalendarEventsTable
                .join(PropertiesTable, JoinType.INNER, CalendarEventsTable.propertyId, PropertiesTable.id)
                .join(BookingsTable, JoinType.LEFT, CalendarEventsTable.id, BookingsTable.calendarEventId)
                .join(TasksTable, JoinType.LEFT, CalendarEventsTable.id, TasksTable.calendarEventId)
                .join(UsersTable, JoinType.LEFT, TasksTable.assignedTo, UsersTable.id)
                .selectAll()
                .where {
                    (PropertiesTable.deletedAt.isNull()) and
                        // User has access (owner or crew)
                        (
                            (PropertiesTable.accountId eq user.accountId) or
                                (PropertiesTable.id inSubQuery crewPropertyIds)
                        ) and
                        // Event is today (check-in or check-out)
                        (
                            (CalendarEventsTable.from lessEq endOfDay) and (CalendarEventsTable.from greaterEq startOfDay) or
                                (CalendarEventsTable.to lessEq endOfDay) and (CalendarEventsTable.to greaterEq startOfDay)
                        ) and
                        // Exclude cancelled tasks
                        (TasksTable.cancelledAt.isNull() or TasksTable.id.isNull())
                }.paginate(
                    pageable = pageable,
                    map = CalendarEvent.Companion,
                )
        }

    /**
     * Get upcoming events for the user.
     * Returns events from:
     * - Properties owned by the user's account
     * - Properties the user has crew access to
     * Includes booking and task information via LEFT JOINs.
     */
    suspend fun getNextCalendarEvents(
        user: KasifyUserPrincipal,
        pageable: Pageable?,
    ): Page<CalendarEvent> =
        dbQuery {
            val madridZone = TimeZone.Companion.of("Europe/Madrid")
            val now =
                Clock.System
                    .now()
                    .toJavaInstant()
                    .atZone(madridZone.toJavaZoneId())
                    .toOffsetDateTime()

            // Subquery to get property IDs the user has crew access to
            val crewPropertyIds =
                PropertyAccessTable
                    .select(PropertyAccessTable.propertyId)
                    .where { PropertyAccessTable.userId eq user.userId }

            CalendarEventsTable
                .join(PropertiesTable, JoinType.INNER, CalendarEventsTable.propertyId, PropertiesTable.id)
                .join(BookingsTable, JoinType.LEFT, CalendarEventsTable.id, BookingsTable.calendarEventId)
                .join(TasksTable, JoinType.LEFT, CalendarEventsTable.id, TasksTable.calendarEventId)
                .join(UsersTable, JoinType.LEFT, TasksTable.assignedTo, UsersTable.id)
                .selectAll()
                .where {
                    (PropertiesTable.deletedAt.isNull()) and
                        // User has access (owner or crew)
                        (
                            (PropertiesTable.accountId eq user.accountId) or
                                (PropertiesTable.id inSubQuery crewPropertyIds)
                        ) and
                        // Event ends in the future
                        (CalendarEventsTable.to greaterEq now) and
                        // Exclude cancelled tasks
                        (TasksTable.cancelledAt.isNull() or TasksTable.id.isNull())
                }.paginate(
                    pageable = pageable,
                    map = CalendarEvent.Companion,
                )
        }

    /**
     * Get calendar events for a set of properties within a date window.
     * Returns events whose [from, to] interval overlaps the supplied window
     * (window is treated as inclusive in the property's local-day sense, anchored
     * on Europe/Madrid to match the rest of the calendar code).
     *
     * Cancelled tasks are excluded. Events with no booking and no task still appear.
     *
     * Caller is responsible for ensuring [propertyIds] only contains properties the
     * user has access to — this method does not re-check authorization.
     */
    suspend fun getCalendarEventsForProperties(
        propertyIds: List<Uuid>,
        from: LocalDate,
        to: LocalDate,
    ): Map<Uuid, List<CalendarEvent>> {
        if (propertyIds.isEmpty()) return emptyMap()

        return dbQuery {
            val madridZone = TimeZone.of("Europe/Madrid")
            val zoneId = madridZone.toJavaZoneId()
            val windowStart =
                from
                    .toJavaLocalDate()
                    .atStartOfDay(zoneId)
                    .toOffsetDateTime()
            val windowEnd =
                to
                    .toJavaLocalDate()
                    .plusDays(1)
                    .atStartOfDay(zoneId)
                    .minusNanos(1)
                    .toOffsetDateTime()

            val rows =
                CalendarEventsTable
                    .join(PropertiesTable, JoinType.INNER, CalendarEventsTable.propertyId, PropertiesTable.id)
                    .join(BookingsTable, JoinType.LEFT, CalendarEventsTable.id, BookingsTable.calendarEventId)
                    .join(TasksTable, JoinType.LEFT, CalendarEventsTable.id, TasksTable.calendarEventId)
                    .join(UsersTable, JoinType.LEFT, TasksTable.assignedTo, UsersTable.id)
                    .selectAll()
                    .where {
                        (CalendarEventsTable.propertyId inList propertyIds) and
                            // Overlap: event.from <= windowEnd AND event.to >= windowStart
                            (CalendarEventsTable.from lessEq windowEnd) and
                            (CalendarEventsTable.to greaterEq windowStart) and
                            // Exclude cancelled tasks (but keep rows that have no task at all)
                            (TasksTable.cancelledAt.isNull() or TasksTable.id.isNull())
                    }.orderBy(CalendarEventsTable.from to SortOrder.ASC)
                    .map { CalendarEvent.from(it) }

            // Build a map keyed by property id, defaulting to empty so callers can
            // attach an event list to every property even when it has no events.
            val grouped = rows.groupBy { it.propertyId }
            propertyIds.associateWith { grouped[it].orEmpty() }
        }
    }

    /**
     * Get paginated timeline events for the user with access control.
     * Same access control logic as getUpcomingTimelineEvents but paginated.
     */
    suspend fun getPaginatedTimelineEvents(
        accountId: Uuid,
        userId: Uuid,
        fullPropertyIds: List<Uuid>,
        limitedPropertyIds: List<Uuid>,
        pageable: Pageable?,
        propertyId: Uuid? = null,
    ): Pair<List<TimelineEvent>, Int> =
        dbQuery {
            val madridZone = TimeZone.of("Europe/Madrid")
            val now =
                Clock.System
                    .now()
                    .toJavaInstant()
                    .atZone(madridZone.toJavaZoneId())
                    .toOffsetDateTime()

            val fullIds = fullPropertyIds.map { it }
            val limitedIds = limitedPropertyIds.map { it }

            // Subquery to get property IDs the user currently has crew access to
            val crewPropertyIds =
                PropertyAccessTable
                    .select(PropertyAccessTable.propertyId)
                    .where { PropertyAccessTable.userId eq userId }

            val baseQuery =
                CalendarEventsTable
                    .join(PropertiesTable, JoinType.INNER, CalendarEventsTable.propertyId, PropertiesTable.id)
                    .join(BookingsTable, JoinType.LEFT, CalendarEventsTable.id, BookingsTable.calendarEventId)
                    .join(TasksTable, JoinType.LEFT, CalendarEventsTable.id, TasksTable.calendarEventId)
                    .join(UsersTable, JoinType.LEFT, TasksTable.assignedTo, UsersTable.id)
                    .selectAll()
                    .where {
                        (PropertiesTable.deletedAt.isNull()) and
                            (CalendarEventsTable.to greaterEq now) and
                            (CalendarEventsTable.role neq EventType.block) and
                            (TasksTable.cancelledAt.isNull() or TasksTable.id.isNull()) and
                            ((TasksTable.status neq TaskStatus.completed) or TasksTable.id.isNull()) and
                            ((BookingsTable.status eq BookingStatus.Confirmed) or BookingsTable.id.isNull()) and
                            (if (propertyId != null) CalendarEventsTable.propertyId eq propertyId else Op.TRUE) and
                            (
                                (PropertiesTable.accountId eq accountId) or
                                    (if (fullIds.isNotEmpty()) PropertiesTable.id inList fullIds else Op.FALSE) or
                                    (
                                        (if (limitedIds.isNotEmpty()) PropertiesTable.id inList limitedIds else Op.FALSE) and
                                            BookingsTable.id.isNotNull()
                                    ) or
                                    // Assigned tasks only while the user still holds a grant on the property
                                    (
                                        (TasksTable.assignedTo eq userId) and
                                            (PropertiesTable.id inSubQuery crewPropertyIds)
                                    )
                            )
                    }

            val total = baseQuery.count().toInt()

            val page = pageable?.page ?: 0
            val size = pageable?.size ?: 20

            val events =
                baseQuery
                    .orderBy(CalendarEventsTable.from to SortOrder.ASC)
                    .limit(size)
                    .offset((page * size).toLong())
                    .map { row ->
                        val propertyZoneId = java.time.ZoneId.of(row[PropertiesTable.tzone])
                        val fromDateTime = row[CalendarEventsTable.from]
                        val toDateTime = row[CalendarEventsTable.to]

                        val bookingId = row.getOrNull(BookingsTable.id)
                        val taskId = row.getOrNull(TasksTable.id)

                        val booking =
                            if (bookingId != null) {
                                Booking.fromResultRow(row, row)
                            } else {
                                null
                            }

                        val assignedTo = row.getOrNull(TasksTable.assignedTo)
                        val assignedToName =
                            if (taskId != null && assignedTo != null && row.hasValue(UsersTable.name)) {
                                "${row[UsersTable.name]} ${row.getOrNull(UsersTable.lastName) ?: ""}".trim()
                            } else {
                                null
                            }

                        TimelineEvent(
                            type = row[CalendarEventsTable.role],
                            propertyId = row[CalendarEventsTable.propertyId],
                            accountId = row[PropertiesTable.accountId],
                            propertyName = row[PropertiesTable.name],
                            fromDate = fromDateTime.toLocalDate(),
                            toDate = toDateTime.toLocalDate(),
                            booking = booking,
                            taskId = taskId,
                            taskType = if (taskId != null) row[TasksTable.taskType] else null,
                            taskStatus = if (taskId != null) row[TasksTable.status] else null,
                            assignedToName = assignedToName,
                            taskNotes = if (taskId != null) row[TasksTable.notes] else null,
                            scheduledStartTime = fromDateTime.atZoneSameInstant(propertyZoneId).toLocalTime(),
                            scheduledEndTime = toDateTime.atZoneSameInstant(propertyZoneId).toLocalTime(),
                        )
                    }

            Pair(events, total)
        }

    /**
     * Get upcoming timeline events for the user with access control.
     * Returns bookings and tasks from:
     * - Properties owned by the user's account (all events)
     * - Properties with Full crew access (all events)
     * - Properties with Limited crew access (bookings only)
     * - Tasks assigned to the user on properties they currently hold a crew grant on
     */
    suspend fun getUpcomingTimelineEvents(
        accountId: Uuid,
        userId: Uuid,
        fullPropertyIds: List<Uuid>,
        limitedPropertyIds: List<Uuid>,
        limit: Int = 20,
    ): List<TimelineEvent> =
        dbQuery {
            val madridZone = TimeZone.of("Europe/Madrid")
            val now =
                Clock.System
                    .now()
                    .toJavaInstant()
                    .atZone(madridZone.toJavaZoneId())
                    .toOffsetDateTime()

            val fullIds = fullPropertyIds.map { it }
            val limitedIds = limitedPropertyIds.map { it }

            // Subquery to get property IDs the user currently has crew access to
            val crewPropertyIds =
                PropertyAccessTable
                    .select(PropertyAccessTable.propertyId)
                    .where { PropertyAccessTable.userId eq userId }

            CalendarEventsTable
                .join(PropertiesTable, JoinType.INNER, CalendarEventsTable.propertyId, PropertiesTable.id)
                .join(BookingsTable, JoinType.LEFT, CalendarEventsTable.id, BookingsTable.calendarEventId)
                .join(TasksTable, JoinType.LEFT, CalendarEventsTable.id, TasksTable.calendarEventId)
                .join(UsersTable, JoinType.LEFT, TasksTable.assignedTo, UsersTable.id)
                .selectAll()
                .where {
                    (PropertiesTable.deletedAt.isNull()) and
                        // Event hasn't ended
                        (CalendarEventsTable.to greaterEq now) and
                        // Exclude blocks
                        (CalendarEventsTable.role neq EventType.block) and
                        // Exclude cancelled tasks
                        (TasksTable.cancelledAt.isNull() or TasksTable.id.isNull()) and
                        // Exclude completed tasks
                        ((TasksTable.status neq TaskStatus.completed) or TasksTable.id.isNull()) and
                        // Only confirmed bookings (or no booking)
                        ((BookingsTable.status eq BookingStatus.Confirmed) or BookingsTable.id.isNull()) and
                        // Access control
                        (
                            // Owner: all events for account's properties
                            (PropertiesTable.accountId eq accountId) or
                                // Full crew: all events
                                (if (fullIds.isNotEmpty()) PropertiesTable.id inList fullIds else Op.FALSE) or
                                // Limited crew: bookings only
                                (
                                    (if (limitedIds.isNotEmpty()) PropertiesTable.id inList limitedIds else Op.FALSE) and
                                        BookingsTable.id.isNotNull()
                                ) or
                                // Any crew: assigned tasks, only while the user still holds a grant on the property
                                (
                                    (TasksTable.assignedTo eq userId) and
                                        (PropertiesTable.id inSubQuery crewPropertyIds)
                                )
                        )
                }.orderBy(CalendarEventsTable.from to SortOrder.ASC)
                .limit(limit)
                .map { row ->
                    val propertyZoneId = java.time.ZoneId.of(row[PropertiesTable.tzone])
                    val fromDateTime = row[CalendarEventsTable.from]
                    val toDateTime = row[CalendarEventsTable.to]

                    val bookingId = row.getOrNull(BookingsTable.id)
                    val taskId = row.getOrNull(TasksTable.id)

                    val booking =
                        if (bookingId != null) {
                            Booking.fromResultRow(row, row)
                        } else {
                            null
                        }

                    val assignedTo = row.getOrNull(TasksTable.assignedTo)
                    val assignedToName =
                        if (taskId != null && assignedTo != null && row.hasValue(UsersTable.name)) {
                            "${row[UsersTable.name]} ${row.getOrNull(UsersTable.lastName) ?: ""}".trim()
                        } else {
                            null
                        }

                    TimelineEvent(
                        type = row[CalendarEventsTable.role],
                        propertyId = row[CalendarEventsTable.propertyId],
                        accountId = row[PropertiesTable.accountId],
                        propertyName = row[PropertiesTable.name],
                        fromDate = fromDateTime.toLocalDate(),
                        toDate = toDateTime.toLocalDate(),
                        booking = booking,
                        taskId = taskId,
                        taskType = if (taskId != null) row[TasksTable.taskType] else null,
                        taskStatus = if (taskId != null) row[TasksTable.status] else null,
                        assignedToName = assignedToName,
                        taskNotes = if (taskId != null) row[TasksTable.notes] else null,
                        scheduledStartTime = fromDateTime.atZoneSameInstant(propertyZoneId).toLocalTime(),
                        scheduledEndTime = toDateTime.atZoneSameInstant(propertyZoneId).toLocalTime(),
                    )
                }
        }
}
