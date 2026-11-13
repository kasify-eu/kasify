package eu.kasify.plugins.telegram

/**
 * Raised by [TelegramClient] on a Bot API failure. [retryable] distinguishes transient faults
 * (5xx, 429, network) — worth retrying — from permanent ones (the user blocked the bot, an invalid
 * chat id) that should not be retried.
 */
class TelegramException(
    message: String,
    val retryable: Boolean = true,
    cause: Throwable? = null,
) : Exception(message, cause)
