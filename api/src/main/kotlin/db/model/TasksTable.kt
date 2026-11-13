package eu.kasify.db.model
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.timestampWithTimeZone

object TasksTable : Table(name = "tasks") {
    val id = uuid("id").autoGenerate()
    val calendarEventId = reference("calendar_event_id", CalendarEventsTable.id)
    val propertyId = reference("property_id", PropertiesTable.id)
    val bookingId = reference("booking_id", BookingsTable.id).nullable()

    val taskType =
        customEnumeration(
            name = "task_type",
            sql = "property_service",
            fromDb = { value ->
                PropertyService.entries.find { it.name.equals(value as String, ignoreCase = true) }
                    ?: throw IllegalArgumentException("Invalid PropertyService: $value")
            },
            toDb = { PGEnum("property_service", it) },
        )

    val status =
        customEnumeration(
            name = "status",
            sql = "task_status",
            fromDb = { value ->
                TaskStatus.entries.find { it.name.equals(value as String, ignoreCase = true) }
                    ?: throw IllegalArgumentException("Invalid TaskStatus: $value")
            },
            toDb = { PGEnum("task_status", it) },
        )

    val assignedTo = reference("assigned_to", UsersTable.id)
    val notes = text("notes").nullable()
    val jobReport = text("job_report").nullable()
    val publicToken = varchar("public_token", length = 64).nullable().uniqueIndex()

    val createdAt = timestampWithTimeZone("created_at")
    val startedAt = timestampWithTimeZone("started_at").nullable()
    val completedAt = timestampWithTimeZone("completed_at").nullable()
    val cancelledAt = timestampWithTimeZone("cancelled_at").nullable()

    override val primaryKey = PrimaryKey(id)
}
