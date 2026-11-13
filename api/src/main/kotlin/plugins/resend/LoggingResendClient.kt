package eu.kasify.plugins.resend

import eu.kasify.model.email.EmailMessage
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.UUID

/**
 * No-op [ResendClient] for local development and tests. Instead of making a network call,
 * the rendered email is written to the log and a synthetic message id is returned.
 */
class LoggingResendClient : ResendClient {
    private val logger = KotlinLogging.logger {}

    override suspend fun send(message: EmailMessage): String {
        val id = "logged-${UUID.randomUUID()}"
        logger.info {
            buildString {
                appendLine("---- LoggingResendClient (no email sent) ----")
                appendLine("id:      $id")
                appendLine("from:    ${message.from}")
                appendLine("to:      ${message.to}")
                appendLine("subject: ${message.subject}")
                if (message.tags.isNotEmpty()) appendLine("tags:    ${message.tags}")
                appendLine("text:")
                appendLine(message.text)
                appendLine("html:")
                appendLine(message.html)
                append("----------------------------------------------")
            }
        }
        return id
    }
}
