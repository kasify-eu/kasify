package eu.kasify.model

import eu.kasify.db.model.NotificationsTable
import org.jetbrains.exposed.v1.core.ResultRow
import java.time.OffsetDateTime
import kotlin.uuid.Uuid

/** Delivery channel for a notification. [dbValue] matches the `notification_channel` PG enum. */
enum class NotificationChannelType(
    val dbValue: String,
) {
    Email("email"),
    Telegram("telegram"),
    ;

    companion object {
        fun fromDb(value: String): NotificationChannelType =
            entries.firstOrNull { it.dbValue == value }
                ?: throw IllegalArgumentException("Unknown notification channel: $value")
    }
}

/** Outbox lifecycle state. [dbValue] matches the `notification_status` PG enum. */
enum class NotificationStatus(
    val dbValue: String,
) {
    Pending("pending"),
    Processing("processing"),
    Sent("sent"),
    Failed("failed"),
    Dead("dead"),
    ;

    companion object {
        fun fromDb(value: String): NotificationStatus =
            entries.firstOrNull { it.dbValue == value }
                ?: throw IllegalArgumentException("Unknown notification status: $value")
    }
}

/**
 * One row of the notifications outbox: a single delivery of [eventType] to [channel] for a recipient
 * that is either a registered user ([userId]) or a raw [emailOverride] address.
 */
data class Notification(
    val id: Uuid,
    val groupId: Uuid,
    val userId: Uuid?,
    val emailOverride: String?,
    val eventType: NotificationType,
    val channel: NotificationChannelType,
    val payload: NotificationPayload,
    val status: NotificationStatus,
    val attemptCount: Int,
    val maxAttempts: Int,
    val nextAttemptAt: OffsetDateTime,
    val claimedAt: OffsetDateTime?,
    val lastError: String?,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
) {
    companion object {
        fun from(row: ResultRow): Notification =
            Notification(
                id = row[NotificationsTable.id],
                groupId = row[NotificationsTable.groupId],
                userId = row[NotificationsTable.userId],
                emailOverride = row[NotificationsTable.emailOverride],
                eventType = NotificationType.fromDb(row[NotificationsTable.eventType]),
                channel = row[NotificationsTable.channel],
                payload = row[NotificationsTable.payload],
                status = row[NotificationsTable.status],
                attemptCount = row[NotificationsTable.attemptCount],
                maxAttempts = row[NotificationsTable.maxAttempts],
                nextAttemptAt = row[NotificationsTable.nextAttemptAt],
                claimedAt = row[NotificationsTable.claimedAt],
                lastError = row[NotificationsTable.lastError],
                createdAt = row[NotificationsTable.createdAt],
                updatedAt = row[NotificationsTable.updatedAt],
            )
    }
}
