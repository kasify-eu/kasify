package eu.kasify.db

import eu.kasify.db.model.NotificationsTable
import eu.kasify.model.Notification
import eu.kasify.model.NotificationChannelType
import eu.kasify.model.NotificationPayload
import eu.kasify.model.NotificationStatus
import eu.kasify.model.NotificationType
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.core.vendors.ForUpdateOption
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import java.time.OffsetDateTime
import kotlin.uuid.Uuid

/** A notification ready to be enqueued. One row is written per (logical notification x channel). */
data class NewNotification(
    val groupId: Uuid,
    val userId: Uuid?,
    val emailOverride: String?,
    val eventType: NotificationType,
    val channel: NotificationChannelType,
    val payload: NotificationPayload,
    val maxAttempts: Int = 5,
)

open class NotificationsRepository {
    /** Inserts the outbox rows (status `pending`, due immediately). Returns the number inserted. */
    open suspend fun enqueue(rows: List<NewNotification>): Int {
        if (rows.isEmpty()) return 0
        return dbQuery {
            val now = OffsetDateTime.now()
            NotificationsTable
                .batchInsert(rows) { row ->
                    this[NotificationsTable.groupId] = row.groupId
                    this[NotificationsTable.userId] = row.userId
                    this[NotificationsTable.emailOverride] = row.emailOverride
                    this[NotificationsTable.eventType] = row.eventType.dbValue
                    this[NotificationsTable.channel] = row.channel
                    this[NotificationsTable.payload] = row.payload
                    this[NotificationsTable.status] = NotificationStatus.Pending
                    this[NotificationsTable.attemptCount] = 0
                    this[NotificationsTable.maxAttempts] = row.maxAttempts
                    this[NotificationsTable.nextAttemptAt] = now
                    this[NotificationsTable.createdAt] = now
                    this[NotificationsTable.updatedAt] = now
                }.size
        }
    }

    /**
     * Atomically claims up to [limit] due rows (`pending` and `next_attempt_at <= now`), flipping them
     * to `processing`. Uses `FOR UPDATE SKIP LOCKED` so concurrent workers never claim the same row;
     * rows locked by another tick are skipped rather than waited on.
     */
    open suspend fun claimBatch(limit: Int): List<Notification> =
        dbQuery {
            val now = OffsetDateTime.now()
            val claimed =
                NotificationsTable
                    .selectAll()
                    .where {
                        (NotificationsTable.status eq NotificationStatus.Pending) and
                            (NotificationsTable.nextAttemptAt lessEq now)
                    }.orderBy(NotificationsTable.nextAttemptAt to SortOrder.ASC)
                    .limit(limit)
                    .forUpdate(ForUpdateOption.PostgreSQL.ForUpdate(ForUpdateOption.PostgreSQL.MODE.SKIP_LOCKED))
                    .map { Notification.from(it) }

            if (claimed.isNotEmpty()) {
                val ids = claimed.map { it.id }
                NotificationsTable.update({ NotificationsTable.id inList ids }) {
                    it[status] = NotificationStatus.Processing
                    it[claimedAt] = now
                    it[updatedAt] = now
                }
            }
            claimed
        }

    /** Marks a row delivered (terminal). */
    open suspend fun markSent(
        id: Uuid,
        attemptCount: Int,
    ) {
        dbQuery {
            val now = OffsetDateTime.now()
            NotificationsTable.update({ NotificationsTable.id eq id }) {
                it[status] = NotificationStatus.Sent
                it[NotificationsTable.attemptCount] = attemptCount
                it[claimedAt] = null
                it[lastError] = null
                it[updatedAt] = now
            }
        }
    }

    /** Returns the row to `pending` for a later retry at [nextAttemptAt] (exponential backoff). */
    open suspend fun markRetry(
        id: Uuid,
        attemptCount: Int,
        error: String,
        nextAttemptAt: OffsetDateTime,
    ) {
        dbQuery {
            val now = OffsetDateTime.now()
            NotificationsTable.update({ NotificationsTable.id eq id }) {
                it[status] = NotificationStatus.Pending
                it[NotificationsTable.attemptCount] = attemptCount
                it[lastError] = error.take(1000)
                it[NotificationsTable.nextAttemptAt] = nextAttemptAt
                it[claimedAt] = null
                it[updatedAt] = now
            }
        }
    }

    /** Dead-letters a row (terminal): retries exhausted or a non-retryable provider error. */
    open suspend fun markDead(
        id: Uuid,
        attemptCount: Int,
        error: String,
    ) {
        dbQuery {
            val now = OffsetDateTime.now()
            NotificationsTable.update({ NotificationsTable.id eq id }) {
                it[status] = NotificationStatus.Dead
                it[NotificationsTable.attemptCount] = attemptCount
                it[lastError] = error.take(1000)
                it[claimedAt] = null
                it[updatedAt] = now
            }
        }
    }

    /**
     * Crash recovery: resets rows stuck in `processing` (a worker claimed them then died before
     * recording an outcome) back to `pending` once their claim is older than [olderThan]. Returns
     * the number reset.
     */
    open suspend fun reapStale(olderThan: OffsetDateTime): Int =
        dbQuery {
            val now = OffsetDateTime.now()
            NotificationsTable.update({
                (NotificationsTable.status eq NotificationStatus.Processing) and
                    (NotificationsTable.claimedAt less olderThan)
            }) {
                it[status] = NotificationStatus.Pending
                it[claimedAt] = null
                it[updatedAt] = now
            }
        }

    open suspend fun getById(id: Uuid): Notification? =
        dbQuery {
            NotificationsTable
                .selectAll()
                .where { NotificationsTable.id eq id }
                .map { Notification.from(it) }
                .firstOrNull()
        }

    suspend fun listByGroup(groupId: Uuid): List<Notification> =
        dbQuery {
            NotificationsTable
                .selectAll()
                .where { NotificationsTable.groupId eq groupId }
                .map { Notification.from(it) }
        }
}
