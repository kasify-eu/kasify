package eu.kasify.plugins.telegram

import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.UUID

/**
 * No-op [TelegramClient] for local development and tests (and whenever `TELEGRAM_ENABLED` is false).
 * Logs the message instead of calling the Bot API and returns a synthetic message id.
 */
class LoggingTelegramClient : TelegramClient {
    private val logger = KotlinLogging.logger {}

    override suspend fun sendMessage(
        chatId: Long,
        text: String,
    ): String {
        val id = "logged-${UUID.randomUUID()}"
        logger.info {
            buildString {
                appendLine("---- LoggingTelegramClient (no message sent) ----")
                appendLine("id:      $id")
                appendLine("chat_id: $chatId")
                appendLine("text:")
                appendLine(text)
                append("--------------------------------------------------")
            }
        }
        return id
    }
}
