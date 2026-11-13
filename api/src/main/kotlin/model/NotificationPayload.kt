package eu.kasify.model

import eu.kasify.model.email.Locale
import kotlinx.serialization.Serializable

/**
 * Channel-agnostic rendering inputs for a notification, serialized into the `notifications.payload`
 * JSONB column. Stores the data needed to render the message (not the rendered text) so templates
 * can change without re-enqueueing, and so each channel can render the same inputs differently
 * (HTML email vs plain-text Telegram).
 *
 * [templateKey] selects the template family (e.g. `invitation`, `member_joined`) under
 * `resources/email/` and `resources/telegram/`; [placeholders] fill the `{{token}}` slots.
 * [tags] are optional channel-side categorization hints (e.g. Resend tags).
 */
@Serializable
data class NotificationPayload(
    val templateKey: String,
    val locale: Locale,
    val placeholders: Map<String, String>,
    val tags: Map<String, String> = emptyMap(),
)
