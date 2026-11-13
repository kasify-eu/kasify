package eu.kasify.model

import eu.kasify.db.model.UserTelegramLinksTable
import org.jetbrains.exposed.v1.core.ResultRow
import java.time.OffsetDateTime
import kotlin.uuid.Uuid

/** Lifecycle of a user's Telegram link. [dbValue] matches the `telegram_link_status` PG enum. */
enum class TelegramLinkStatus(
    val dbValue: String,
) {
    Pending("pending"),
    Linked("linked"),
    Revoked("revoked"),
    ;

    companion object {
        fun fromDb(value: String): TelegramLinkStatus =
            entries.firstOrNull { it.dbValue == value }
                ?: throw IllegalArgumentException("Unknown telegram link status: $value")
    }
}

/** A user's Telegram link record (one per user). */
data class TelegramLink(
    val id: Uuid,
    val userId: Uuid,
    val status: TelegramLinkStatus,
    val linkToken: String?,
    val linkTokenExpiresAt: OffsetDateTime?,
    val chatId: Long?,
    val telegramUsername: String?,
    val linkedAt: OffsetDateTime?,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
) {
    companion object {
        fun from(row: ResultRow): TelegramLink =
            TelegramLink(
                id = row[UserTelegramLinksTable.id],
                userId = row[UserTelegramLinksTable.userId],
                status = row[UserTelegramLinksTable.status],
                linkToken = row[UserTelegramLinksTable.linkToken],
                linkTokenExpiresAt = row[UserTelegramLinksTable.linkTokenExpiresAt],
                chatId = row[UserTelegramLinksTable.chatId],
                telegramUsername = row[UserTelegramLinksTable.telegramUsername],
                linkedAt = row[UserTelegramLinksTable.linkedAt],
                createdAt = row[UserTelegramLinksTable.createdAt],
                updatedAt = row[UserTelegramLinksTable.updatedAt],
            )
    }
}
