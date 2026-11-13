package eu.kasify.services.notifications

import eu.kasify.model.NotificationChannelType
import eu.kasify.model.NotificationPayload

/** Where a notification can be delivered for a given recipient. Null when the channel is unavailable. */
data class NotificationRecipient(
    val email: String?,
    val telegramChatId: Long?,
)

/**
 * A single delivery transport (email, Telegram, ...). Implementations render [payload] for their
 * medium and deliver it to [recipient], returning a provider message id. They must **throw** on any
 * delivery failure so the dispatch worker can retry; a [NotificationDeliveryException] with
 * `retryable = false` signals a terminal failure (e.g. the user blocked the bot) that should be
 * dead-lettered immediately instead of retried.
 */
interface NotificationChannel {
    val channel: NotificationChannelType

    suspend fun send(
        recipient: NotificationRecipient,
        payload: NotificationPayload,
    ): String
}

/**
 * Raised by a [NotificationChannel] when delivery fails. [retryable] = false marks a permanent
 * failure (bad recipient, bot blocked) that the worker dead-letters without further attempts.
 */
class NotificationDeliveryException(
    message: String,
    val retryable: Boolean = true,
    cause: Throwable? = null,
) : Exception(message, cause)
