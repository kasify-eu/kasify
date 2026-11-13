package eu.kasify.services.notifications

import eu.kasify.model.NotificationChannelType
import eu.kasify.model.NotificationPayload
import eu.kasify.plugins.telegram.TelegramClient
import eu.kasify.plugins.telegram.TelegramException

/**
 * Telegram delivery. Renders the payload as plain text via [TelegramMessageRenderer] and sends it to
 * the recipient's linked chat through [TelegramClient]. Maps [TelegramException] onto
 * [NotificationDeliveryException], preserving its retryable flag so the dispatch worker retries
 * transient faults and dead-letters permanent ones (bot blocked, invalid chat).
 */
open class TelegramChannel(
    private val client: TelegramClient,
    private val renderer: TelegramMessageRenderer,
) : NotificationChannel {
    override val channel = NotificationChannelType.Telegram

    override suspend fun send(
        recipient: NotificationRecipient,
        payload: NotificationPayload,
    ): String {
        val chatId =
            recipient.telegramChatId
                ?: throw NotificationDeliveryException("No Telegram chat id for recipient", retryable = false)

        val text =
            try {
                renderer.render(payload.templateKey, payload.locale, payload.placeholders)
            } catch (e: Exception) {
                throw NotificationDeliveryException(
                    "Failed to render Telegram template '${payload.templateKey}': ${e.message}",
                    retryable = false,
                    cause = e,
                )
            }

        return try {
            client.sendMessage(chatId, text)
        } catch (e: TelegramException) {
            throw NotificationDeliveryException(
                e.message ?: "Telegram delivery failed",
                retryable = e.retryable,
                cause = e,
            )
        }
    }
}
