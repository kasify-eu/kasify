package eu.kasify.db.model
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.timestampWithTimeZone

@Serializable
@Suppress("EnumEntryName", "ktlint:standard:enum-entry-name-case")
enum class EventType {
    booking,
    task,
    block,
}

object CalendarEventsTable : Table(name = "calendar_events") {
    val id = uuid("id").autoGenerate()
    val propertyId = reference("property_id", PropertiesTable.id)
    val from = timestampWithTimeZone("from_datetime")
    val to = timestampWithTimeZone("to_datetime")

    val role =
        customEnumeration(
            name = "type",
            sql = "event_type",
            fromDb = { value -> EventType.valueOf(value as String) },
            toDb = { PGEnum("event_type", it) },
        )

    val createdAt = timestampWithTimeZone("created_at")
    val updatedAt = timestampWithTimeZone("updated_at")

    override val primaryKey = PrimaryKey(id)
}
