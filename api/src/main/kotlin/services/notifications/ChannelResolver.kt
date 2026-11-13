package eu.kasify.services.notifications

import eu.kasify.config.TelegramConfig
import eu.kasify.db.UserTelegramLinksRepository
import eu.kasify.model.NotificationChannelType
import kotlin.uuid.Uuid

/**
 * Decides which channels a notification fans out to at **enqueue** time (one outbox row per channel).
 * Email is always included; Telegram is added only when the channel is enabled for the deployment and
 * the user has completed linking. A raw-email recipient (no user account, e.g. an invitee) is
 * email-only.
 */
open class ChannelResolver(
    private val telegramLinksRepository: UserTelegramLinksRepository,
    private val telegramConfig: TelegramConfig,
) {
    open suspend fun channelsForUser(userId: Uuid): List<NotificationChannelType> =
        buildList {
            add(NotificationChannelType.Email)
            if (telegramConfig.enabled && telegramLinksRepository.findChatId(userId) != null) {
                add(NotificationChannelType.Telegram)
            }
        }

    open fun channelsForEmailOnly(): List<NotificationChannelType> = listOf(NotificationChannelType.Email)
}
