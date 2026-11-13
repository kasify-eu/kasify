package eu.kasify.model
import eu.kasify.db.model.CalendarEventsTable
import eu.kasify.db.model.PropertiesTable
import eu.kasify.db.model.PropertyService
import eu.kasify.db.model.TaskStatus
import eu.kasify.db.model.TasksTable
import eu.kasify.db.model.UsersTable
import eu.kasify.db.toJavaZoneId
import eu.kasify.serialization.JavaLocalDateTimeSerializer
import io.perracodex.exposed.pagination.MapModel
import kotlinx.datetime.TimeZone
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.ResultRow
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.uuid.Uuid

@Serializable
data class Task(
    val id: Uuid,
    val calendarEventId: Uuid,
    val propertyId: Uuid,
    val propertyName: String?,
    val bookingId: Uuid?,
    val taskType: PropertyService,
    val status: TaskStatus,
    val assignedTo: Uuid,
    val assignedToName: String,
    val notes: String?,
    val jobReport: String?,
    val publicToken: String?,
    @Serializable(with = JavaLocalDateTimeSerializer::class)
    val from: LocalDateTime,
    @Serializable(with = JavaLocalDateTimeSerializer::class)
    val to: LocalDateTime,
    @Serializable(with = JavaLocalDateTimeSerializer::class)
    val createdAt: LocalDateTime,
    @Serializable(with = JavaLocalDateTimeSerializer::class)
    val startedAt: LocalDateTime?,
    @Serializable(with = JavaLocalDateTimeSerializer::class)
    val completedAt: LocalDateTime?,
    @Serializable(with = JavaLocalDateTimeSerializer::class)
    val cancelledAt: LocalDateTime?,
) {
    companion object : MapModel<Task> {
        override fun from(row: ResultRow): Task = fromResultRow(row, row)

        fun fromResultRow(
            eventRow: ResultRow,
            row: ResultRow,
            zone: TimeZone? = null,
        ): Task {
            val propertyZoneId =
                zone?.toJavaZoneId()
                    ?: if (row.hasValue(PropertiesTable.tzone)) {
                        ZoneId.of(row[PropertiesTable.tzone])
                    } else {
                        ZoneId.systemDefault()
                    }

            val fromDateTime = eventRow[CalendarEventsTable.from]
            val toDateTime = eventRow[CalendarEventsTable.to]

            return Task(
                id = row[TasksTable.id],
                calendarEventId = row[TasksTable.calendarEventId],
                propertyId = row[TasksTable.propertyId],
                propertyName = if (row.hasValue(PropertiesTable.name)) row[PropertiesTable.name] else null,
                bookingId = row[TasksTable.bookingId],
                taskType = row[TasksTable.taskType],
                status = row[TasksTable.status],
                assignedTo = row[TasksTable.assignedTo],
                assignedToName =
                    if (row.hasValue(UsersTable.name)) {
                        "${row[UsersTable.name]} ${row.getOrNull(UsersTable.lastName) ?: ""}".trim()
                    } else {
                        ""
                    },
                notes = row[TasksTable.notes],
                jobReport = row[TasksTable.jobReport],
                publicToken = row[TasksTable.publicToken],
                from = fromDateTime.atZoneSameInstant(propertyZoneId).toLocalDateTime(),
                to = toDateTime.atZoneSameInstant(propertyZoneId).toLocalDateTime(),
                createdAt = row[TasksTable.createdAt].toLocalDateTime(),
                startedAt = row[TasksTable.startedAt]?.toLocalDateTime(),
                completedAt = row[TasksTable.completedAt]?.toLocalDateTime(),
                cancelledAt = row[TasksTable.cancelledAt]?.toLocalDateTime(),
            )
        }
    }
}
