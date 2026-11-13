package eu.kasify.db

import eu.kasify.db.model.UserTelegramLinksTable
import eu.kasify.model.TelegramLink
import eu.kasify.model.TelegramLinkStatus
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import java.time.OffsetDateTime
import kotlin.uuid.Uuid

open class UserTelegramLinksRepository {
    /**
     * Begins (or restarts) linking for [userId]: sets status `pending` and stores a fresh one-time
     * [token] with [expiresAt], invalidating any previous token. Upserts on the unique `user_id`.
     */
    open suspend fun startLink(
        userId: Uuid,
        token: String,
        expiresAt: OffsetDateTime,
    ): Unit =
        dbQuery {
            val now = OffsetDateTime.now()
            val existingId = findId(userId)
            if (existingId == null) {
                UserTelegramLinksTable.insert {
                    it[UserTelegramLinksTable.userId] = userId
                    it[status] = TelegramLinkStatus.Pending
                    it[linkToken] = token
                    it[linkTokenExpiresAt] = expiresAt
                    it[createdAt] = now
                    it[updatedAt] = now
                }
            } else {
                UserTelegramLinksTable.update({ UserTelegramLinksTable.id eq existingId }) {
                    it[status] = TelegramLinkStatus.Pending
                    it[linkToken] = token
                    it[linkTokenExpiresAt] = expiresAt
                    it[updatedAt] = now
                }
            }
        }

    open suspend fun findByToken(token: String): TelegramLink? =
        dbQuery {
            UserTelegramLinksTable
                .selectAll()
                .where { UserTelegramLinksTable.linkToken eq token }
                .map { TelegramLink.from(it) }
                .firstOrNull()
        }

    /** Completes linking: stores the [chatId]/[username], clears the one-time token. */
    open suspend fun markLinked(
        userId: Uuid,
        chatId: Long,
        username: String?,
    ): Unit =
        dbQuery {
            val now = OffsetDateTime.now()
            UserTelegramLinksTable.update({ UserTelegramLinksTable.userId eq userId }) {
                it[status] = TelegramLinkStatus.Linked
                it[UserTelegramLinksTable.chatId] = chatId
                it[telegramUsername] = username
                it[linkedAt] = now
                it[linkToken] = null
                it[linkTokenExpiresAt] = null
                it[updatedAt] = now
            }
        }

    open suspend fun getByUserId(userId: Uuid): TelegramLink? =
        dbQuery {
            UserTelegramLinksTable
                .selectAll()
                .where { UserTelegramLinksTable.userId eq userId }
                .map { TelegramLink.from(it) }
                .firstOrNull()
        }

    /** The chat id to deliver to, or null when the user has not completed linking. */
    open suspend fun findChatId(userId: Uuid): Long? =
        dbQuery {
            UserTelegramLinksTable
                .selectAll()
                .where {
                    (UserTelegramLinksTable.userId eq userId) and
                        (UserTelegramLinksTable.status eq TelegramLinkStatus.Linked)
                }.map { it[UserTelegramLinksTable.chatId] }
                .firstOrNull()
        }

    /** Revokes the link and clears the chat id. Returns true if a row was updated. */
    open suspend fun unlink(userId: Uuid): Boolean =
        dbQuery {
            val now = OffsetDateTime.now()
            UserTelegramLinksTable.update({ UserTelegramLinksTable.userId eq userId }) {
                it[status] = TelegramLinkStatus.Revoked
                it[chatId] = null
                it[linkToken] = null
                it[linkTokenExpiresAt] = null
                it[updatedAt] = now
            } > 0
        }

    // Runs inside the caller's dbQuery transaction; not suspend so it shares it.
    private fun findId(userId: Uuid): Uuid? =
        UserTelegramLinksTable
            .selectAll()
            .where { UserTelegramLinksTable.userId eq userId }
            .map { it[UserTelegramLinksTable.id] }
            .firstOrNull()
}
