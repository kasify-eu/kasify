package eu.kasify.db.model

import eu.kasify.model.NotificationChannelType
import eu.kasify.model.NotificationPayload
import eu.kasify.model.NotificationStatus
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.timestampWithTimeZone
import org.jetbrains.exposed.v1.json.jsonb

private val notificationJson = Json { ignoreUnknownKeys = true }

object NotificationsTable : Table(name = "notifications") {
    val id = uuid("id").autoGenerate()
    val groupId = uuid("group_id")
    val userId = reference("user_id", UsersTable.id).nullable()
    val emailOverride = text("email_override").nullable()
    val eventType = text("event_type")

    val channel =
        customEnumeration(
            name = "channel",
            sql = "notification_channel",
            fromDb = { value -> NotificationChannelType.fromDb(value as String) },
            toDb = { PGEnum("notification_channel", it) },
        )

    val payload =
        jsonb<NotificationPayload>(
            name = "payload",
            serialize = { notificationJson.encodeToString(NotificationPayload.serializer(), it) },
            deserialize = { notificationJson.decodeFromString(NotificationPayload.serializer(), it) },
        )

    val status =
        customEnumeration(
            name = "status",
            sql = "notification_status",
            fromDb = { value -> NotificationStatus.fromDb(value as String) },
            toDb = { PGEnum("notification_status", it) },
        )

    val attemptCount = integer("attempt_count")
    val maxAttempts = integer("max_attempts")
    val nextAttemptAt = timestampWithTimeZone("next_attempt_at")
    val claimedAt = timestampWithTimeZone("claimed_at").nullable()
    val lastError = text("last_error").nullable()
    val createdAt = timestampWithTimeZone("created_at")
    val updatedAt = timestampWithTimeZone("updated_at")
}
