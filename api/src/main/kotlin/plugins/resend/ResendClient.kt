package eu.kasify.plugins.resend

import eu.kasify.model.email.EmailMessage

interface ResendClient {
    /**
     * Sends a transactional email and returns the provider's message id.
     * Implementations may throw [ResendException] on any transport or API failure.
     */
    suspend fun send(message: EmailMessage): String
}
