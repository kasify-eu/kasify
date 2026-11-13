package eu.kasify.db.model
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.timestampWithTimeZone

object PropertyChannelIntegrationsTable : Table(name = "property_channel_integrations") {
    val id = uuid("id").autoGenerate()
    val propertyId = reference("property_id", PropertiesTable.id)

    val channel =
        customEnumeration(
            name = "channel",
            sql = "booking_channel",
            fromDb = { value ->
                BookingChannel.entries.find { it.name.equals(value as String, ignoreCase = true) }
                    ?: throw IllegalArgumentException("Invalid BookingChannel: $value")
            },
            toDb = { PGEnum("booking_channel", it) },
        )

    val url = text("url")

    val syncEnabled = bool("sync_enabled").default(true)
    val syncIntervalMinutes = integer("sync_interval_minutes").default(60)
    val lastSyncAt = timestampWithTimeZone("last_sync_at").nullable()
    val nextSyncAt = timestampWithTimeZone("next_sync_at").nullable()

    val lastError = text("last_error").nullable()
    val errorCount = integer("error_count").default(0)

    val createdAt = timestampWithTimeZone("created_at")
    val updatedAt = timestampWithTimeZone("updated_at")

    override val primaryKey = PrimaryKey(id)
}
