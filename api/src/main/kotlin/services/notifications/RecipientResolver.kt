package eu.kasify.services.notifications

import eu.kasify.db.UserTelegramLinksRepository
import eu.kasify.db.UsersRepository
import eu.kasify.model.Notification

/**
 * Resolves the concrete delivery addresses for a notification at send time: the email address
 * (the row's `email_override`, falling back to the user's account email) and the Telegram chat id
 * (only when the user has a completed link). Resolving at send time — rather than freezing addresses
 * at enqueue time — means a later email change or Telegram link is picked up on retry.
 */
open class RecipientResolver(
    private val usersRepository: UsersRepository,
    private val telegramLinksRepository: UserTelegramLinksRepository,
) {
    open suspend fun resolve(notification: Notification): NotificationRecipient {
        val email =
            notification.emailOverride
                ?: notification.userId?.let { usersRepository.getUserById(it)?.email }
        val chatId = notification.userId?.let { telegramLinksRepository.findChatId(it) }
        return NotificationRecipient(email = email, telegramChatId = chatId)
    }
}
