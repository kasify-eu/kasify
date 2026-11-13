package eu.kasify.db.model

import eu.kasify.model.TelegramLinkStatus
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.timestampWithTimeZone

object UserTelegramLinksTable : Table(name = "user_telegram_links") {
    val id = uuid("id").autoGenerate()
    val userId = reference("user_id", UsersTable.id)

    val status =
        customEnumeration(
            name = "status",
            sql = "telegram_link_status",
            fromDb = { value -> TelegramLinkStatus.fromDb(value as String) },
            toDb = { PGEnum("telegram_link_status", it) },
        )

    val linkToken = text("link_token").nullable()
    val linkTokenExpiresAt = timestampWithTimeZone("link_token_expires_at").nullable()
    val chatId = long("chat_id").nullable()
    val telegramUsername = text("telegram_username").nullable()
    val linkedAt = timestampWithTimeZone("linked_at").nullable()
    val createdAt = timestampWithTimeZone("created_at")
    val updatedAt = timestampWithTimeZone("updated_at")
}
