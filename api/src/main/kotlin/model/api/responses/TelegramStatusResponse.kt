package eu.kasify.model.api.responses

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The current Telegram link state for the authenticated user. [status] is one of `none` (never
 * started), `pending` (link started, awaiting confirmation in Telegram), or `linked`.
 */
@Serializable
data class TelegramStatusResponse(
    val status: String,
    @SerialName("telegram_username")
    val telegramUsername: String? = null,
    @SerialName("linked_at")
    val linkedAt: String? = null,
)
