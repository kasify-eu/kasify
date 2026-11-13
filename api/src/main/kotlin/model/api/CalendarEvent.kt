package eu.kasify.model.api

import eu.kasify.db.model.BookingsTable
import eu.kasify.db.model.CalendarEventsTable
import eu.kasify.db.model.EventType
import eu.kasify.db.model.PropertiesTable
import eu.kasify.db.model.TasksTable
import eu.kasify.db.model.UsersTable
import eu.kasify.db.toKotlinLocalDate
import eu.kasify.serialization.JavaLocalDateTimeSerializer
import io.perracodex.exposed.pagination.MapModel
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.ResultRow
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.uuid.Uuid

@Serializable
class CalendarEvent(
    val id: Uuid? = null,
    val propertyId: Uuid,
    @Serializable(with = JavaLocalDateTimeSerializer::class)
    val from: LocalDateTime,
    @Serializable(with = JavaLocalDateTimeSerializer::class)
    val to: LocalDateTime,
    val type: EventType,
    val createdAt: LocalDate,
    val updatedAt: LocalDate,
    var booking: BookingInfo? = null,
    var task: TaskInfo? = null,
) {
    companion object : MapModel<CalendarEvent> {
        // Default fallback zone matches the rest of the calendar code (window
        // anchor in Europe/Madrid). Used only when the result row doesn't carry
        // PropertiesTable.tzone — every relevant query joins it now.
        private val FALLBACK_ZONE: ZoneId = ZoneId.of("Europe/Madrid")

        override fun from(row: ResultRow): CalendarEvent {
            val propertyZoneId =
                if (row.hasValue(PropertiesTable.tzone)) {
                    ZoneId.of(row[PropertiesTable.tzone])
                } else {
                    FALLBACK_ZONE
                }

            val fromDateTime = row[CalendarEventsTable.from]
            val toDateTime = row[CalendarEventsTable.to]

            val calendarEvent =
                CalendarEvent(
                    id = row[CalendarEventsTable.id],
                    propertyId = row[CalendarEventsTable.propertyId],
                    from = fromDateTime.atZoneSameInstant(propertyZoneId).toLocalDateTime(),
                    to = toDateTime.atZoneSameInstant(propertyZoneId).toLocalDateTime(),
                    type = row[CalendarEventsTable.role],
                    createdAt = row[CalendarEventsTable.createdAt].toLocalDate().toKotlinLocalDate(),
                    updatedAt = row[CalendarEventsTable.updatedAt].toLocalDate().toKotlinLocalDate(),
                )

            // Populate booking info if available
            val channelId = row.getOrNull(BookingsTable.channelId)
            if (channelId != null) {
                calendarEvent.booking =
                    BookingInfo(
                        id = row[BookingsTable.id],
                        channelId = channelId,
                        guestFirstName = row[BookingsTable.guestFirstName]?.takeIf { it.isNotBlank() },
                        guestLastName = row[BookingsTable.guestLastName]?.takeIf { it.isNotBlank() },
                        adultsCount = row[BookingsTable.adultsCount]?.takeIf { it > 0 },
                        childrenCount = row[BookingsTable.childrenCount]?.takeIf { it > 0 },
                        infantsCount = row[BookingsTable.infantsCount]?.takeIf { it > 0 },
                        channel = row[BookingsTable.channel],
                    )
            }

            // Populate task info if available
            val taskId = row.getOrNull(TasksTable.id)
            if (taskId != null) {
                val assignedTo = row.getOrNull(TasksTable.assignedTo)
                val assignedToName =
                    if (assignedTo != null && row.hasValue(UsersTable.name)) {
                        "${row[UsersTable.name]} ${row.getOrNull(UsersTable.lastName) ?: ""}"
                    } else {
                        null
                    }

                calendarEvent.task =
                    TaskInfo(
                        id = taskId,
                        taskType = row[TasksTable.taskType],
                        status = row[TasksTable.status],
                        assignedTo = assignedTo,
                        assignedToName = assignedToName,
                    )
            }

            return calendarEvent
        }
    }
}
