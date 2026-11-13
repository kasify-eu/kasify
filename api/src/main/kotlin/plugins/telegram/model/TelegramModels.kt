package eu.kasify.plugins.telegram.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SendMessageRequest(
    @SerialName("chat_id") val chatId: Long,
    val text: String,
    @SerialName("parse_mode") val parseMode: String? = null,
)

@Serializable
data class SendMessageResponse(
    val ok: Boolean,
    val result: TelegramMessage? = null,
    val description: String? = null,
)

/** A Telegram update delivered to the webhook. We only care about plain `/start <token>` messages. */
@Serializable
data class TelegramUpdate(
    @SerialName("update_id") val updateId: Long,
    val message: TelegramMessage? = null,
)

@Serializable
data class TelegramMessage(
    @SerialName("message_id") val messageId: Long,
    val from: TelegramUser? = null,
    val chat: TelegramChat,
    val text: String? = null,
)

@Serializable
data class TelegramChat(
    val id: Long,
    val username: String? = null,
    @SerialName("first_name") val firstName: String? = null,
)

@Serializable
data class TelegramUser(
    val id: Long,
    @SerialName("is_bot") val isBot: Boolean = false,
    val username: String? = null,
)
