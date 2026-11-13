package eu.kasify.model.api.responses

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Returned when a user starts linking Telegram. The dashboard opens [deepLink] (a
 * `https://t.me/<bot>?start=<token>` URL); [expiresAt] is the ISO-8601 instant after which the
 * one-time token is no longer valid.
 */
@Serializable
data class TelegramLinkResponse(
    @SerialName("deep_link")
    val deepLink: String,
    @SerialName("expires_at")
    val expiresAt: String,
)
