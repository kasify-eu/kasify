package eu.kasify.config

data class EmailConfig(
    val enabled: Boolean,
    val fromAddress: String,
    val resendApiKey: String,
    val dashboardBaseUrl: String,
) {
    companion object {
        private const val DEFAULT_FROM_ADDRESS = "Kasify <noreply@localhost>"
        private const val DEFAULT_DASHBOARD_BASE_URL = "http://localhost:5173"

        fun fromEnv(): EmailConfig {
            val enabled = System.getenv("EMAIL_ENABLED")?.lowercase() == "true"
            val apiKey = System.getenv("RESEND_API_KEY").orEmpty()
            val fromAddress = System.getenv("EMAIL_FROM_ADDRESS")?.takeIf { it.isNotBlank() } ?: DEFAULT_FROM_ADDRESS
            val dashboardBaseUrl = System.getenv("DASHBOARD_BASE_URL")?.takeIf { it.isNotBlank() } ?: DEFAULT_DASHBOARD_BASE_URL

            if (enabled && apiKey.isBlank()) {
                throw EnvironmentValidationException(
                    "EMAIL_ENABLED=true but RESEND_API_KEY is missing or blank. " +
                        "Set RESEND_API_KEY in the environment, or unset EMAIL_ENABLED to fall back to log-only mode.",
                )
            }

            return EmailConfig(
                enabled = enabled,
                fromAddress = fromAddress,
                resendApiKey = apiKey,
                dashboardBaseUrl = dashboardBaseUrl,
            )
        }
    }
}
