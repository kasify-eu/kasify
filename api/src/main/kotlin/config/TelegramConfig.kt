package eu.kasify.config

/**
 * Telegram bot configuration. Telegram is an opt-in complement to email: when [enabled] is false the
 * app uses a logging no-op client and never offers linking. When enabled, all three secrets are
 * required — [botToken] to call the Bot API, [botUsername] to build `t.me/<bot>?start=...` deep
 * links, and [webhookSecret] to authenticate the inbound webhook (Telegram echoes it in the
 * `X-Telegram-Bot-Api-Secret-Token` header).
 */
data class TelegramConfig(
    val enabled: Boolean,
    val botToken: String,
    val botUsername: String,
    val webhookSecret: String,
) {
    companion object {
        fun fromEnv(): TelegramConfig {
            val enabled = System.getenv("TELEGRAM_ENABLED")?.lowercase() == "true"
            val botToken = System.getenv("TELEGRAM_BOT_TOKEN").orEmpty()
            val botUsername = System.getenv("TELEGRAM_BOT_USERNAME").orEmpty().removePrefix("@")
            val webhookSecret = System.getenv("TELEGRAM_WEBHOOK_SECRET").orEmpty()

            if (enabled) {
                val missing =
                    buildList {
                        if (botToken.isBlank()) add("TELEGRAM_BOT_TOKEN")
                        if (botUsername.isBlank()) add("TELEGRAM_BOT_USERNAME")
                        if (webhookSecret.isBlank()) add("TELEGRAM_WEBHOOK_SECRET")
                    }
                if (missing.isNotEmpty()) {
                    throw EnvironmentValidationException(
                        "TELEGRAM_ENABLED=true but the following are missing or blank: ${missing.joinToString()}. " +
                            "Set them, or unset TELEGRAM_ENABLED to disable Telegram notifications.",
                    )
                }
            }

            return TelegramConfig(
                enabled = enabled,
                botToken = botToken,
                botUsername = botUsername,
                webhookSecret = webhookSecret,
            )
        }
    }
}
