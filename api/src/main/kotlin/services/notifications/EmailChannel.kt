package eu.kasify.services.notifications

import eu.kasify.config.EmailConfig
import eu.kasify.model.NotificationChannelType
import eu.kasify.model.NotificationPayload
import eu.kasify.model.email.EmailMessage
import eu.kasify.plugins.resend.ResendClient
import eu.kasify.services.email.EmailTemplateRenderer

/**
 * Email delivery via Resend. Renders the [NotificationPayload] with the existing
 * [EmailTemplateRenderer] (subject/html/text under `resources/email/`) and sends through
 * [ResendClient]. Transport failures propagate as [NotificationDeliveryException] (retryable) so the
 * dispatch worker retries with backoff.
 */
open class EmailChannel(
    private val resendClient: ResendClient,
    private val templateRenderer: EmailTemplateRenderer,
    private val config: EmailConfig,
) : NotificationChannel {
    override val channel = NotificationChannelType.Email

    override suspend fun send(
        recipient: NotificationRecipient,
        payload: NotificationPayload,
    ): String {
        val to =
            recipient.email?.takeIf { it.isNotBlank() }
                ?: throw NotificationDeliveryException(
                    "No email address for recipient",
                    retryable = false,
                )

        val rendered =
            try {
                templateRenderer.render(payload.templateKey, payload.locale, payload.placeholders)
            } catch (e: Exception) {
                // A missing/broken template is a deploy bug, not a transient fault — don't loop.
                throw NotificationDeliveryException(
                    "Failed to render email template '${payload.templateKey}': ${e.message}",
                    retryable = false,
                    cause = e,
                )
            }

        val message =
            EmailMessage(
                to = to,
                from = config.fromAddress,
                subject = rendered.subject,
                html = rendered.html,
                text = rendered.text,
                tags = payload.tags,
            )

        return try {
            resendClient.send(message)
        } catch (e: Exception) {
            throw NotificationDeliveryException("Resend delivery failed: ${e.message}", cause = e)
        }
    }
}
