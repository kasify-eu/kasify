package eu.kasify.plugins.telegram

interface TelegramClient {
    /**
     * Sends [text] to the Telegram [chatId] and returns the Bot API message id.
     * Throws [TelegramException] on any failure (see its `retryable` flag).
     */
    suspend fun sendMessage(
        chatId: Long,
        text: String,
    ): String
}
