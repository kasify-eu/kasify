package eu.kasify.jobs

import eu.kasify.config.EmailConfig
import eu.kasify.config.TelegramConfig
import eu.kasify.db.NotificationsRepository
import eu.kasify.db.UserTelegramLinksRepository
import eu.kasify.db.UsersRepository
import eu.kasify.model.Notification
import eu.kasify.model.NotificationChannelType
import eu.kasify.plugins.resend.LoggingResendClient
import eu.kasify.plugins.resend.ResendClientImpl
import eu.kasify.plugins.telegram.LoggingTelegramClient
import eu.kasify.plugins.telegram.TelegramClientImpl
import eu.kasify.services.email.EmailTemplateRenderer
import eu.kasify.services.notifications.EmailChannel
import eu.kasify.services.notifications.NotificationChannel
import eu.kasify.services.notifications.NotificationDeliveryException
import eu.kasify.services.notifications.NotificationMetrics
import eu.kasify.services.notifications.RecipientResolver
import eu.kasify.services.notifications.TelegramChannel
import eu.kasify.services.notifications.TelegramMessageRenderer
import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.oshai.kotlinlogging.withLoggingContext
import io.sentry.Sentry
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.slf4j.MDCContext
import org.quartz.Job
import org.quartz.JobExecutionContext
import java.time.Duration
import java.time.OffsetDateTime
import kotlin.math.min
import kotlin.random.Random

private val logger = KotlinLogging.logger {}

/**
 * Drains the notifications outbox. Each tick reaps stale `processing` rows (crash recovery), claims a
 * batch of due `pending` rows (`FOR UPDATE SKIP LOCKED`), and delivers each via the matching
 * [NotificationChannel]. Delivery outcomes drive the lifecycle: success → `sent`; a retryable failure
 * → back to `pending` with exponential backoff (`next_attempt_at`); a non-retryable failure or
 * exhausted attempts → `dead` (+ Sentry). Operation-blind to the event type — invitation, member
 * joined, or any future event all take the same path.
 */
class NotificationDispatchJob(
    private val notificationsRepository: NotificationsRepository = NotificationsRepository(),
    private val recipientResolver: RecipientResolver = RecipientResolver(UsersRepository(), UserTelegramLinksRepository()),
    private val channels: Map<NotificationChannelType, NotificationChannel> = sharedChannels,
) : Job {
    override fun execute(context: JobExecutionContext?) {
        val fireId = context?.fireInstanceId ?: "unknown"
        // MDCContext propagates SLF4J MDC across coroutine suspensions (dbQuery thread hops).
        runBlocking(MDCContext()) {
            withLoggingContext("fire_id" to fireId) {
                runOnce()
            }
        }
    }

    internal suspend fun runOnce() {
        val reaped = notificationsRepository.reapStale(OffsetDateTime.now().minusMinutes(STALE_CLAIM_MINUTES))
        if (reaped > 0) {
            logger.warn { "NotificationDispatchJob: reaped $reaped stale processing notification(s)" }
        }

        val batch = notificationsRepository.claimBatch(BATCH_SIZE)
        if (batch.isEmpty()) {
            logger.debug { "NotificationDispatchJob: no notifications to dispatch" }
            return
        }
        logger.info { "NotificationDispatchJob: dispatching ${batch.size} notification(s)" }
        batch.forEach { dispatch(it) }
    }

    private suspend fun dispatch(notification: Notification) {
        val attempt = notification.attemptCount + 1
        withLoggingContext(
            "notification_id" to notification.id.toString(),
            "channel" to notification.channel.dbValue,
        ) {
            val channel = channels[notification.channel]
            if (channel == null) {
                // Channel disabled/unconfigured for this deployment (e.g. Telegram off). Don't loop.
                val msg = "No channel implementation for ${notification.channel.dbValue}"
                logger.error { "Dead-lettering notification ${notification.id}: $msg" }
                notificationsRepository.markDead(notification.id, attempt, msg)
                NotificationMetrics.dead(notification.channel.dbValue)
                return
            }

            try {
                val recipient = recipientResolver.resolve(notification)
                val providerId = channel.send(recipient, notification.payload)
                notificationsRepository.markSent(notification.id, attempt)
                NotificationMetrics.sent(notification.channel.dbValue)
                logger.info {
                    "Delivered notification ${notification.id} via ${notification.channel.dbValue} (providerId=$providerId)"
                }
            } catch (e: Exception) {
                handleFailure(notification, attempt, e)
            }
        }
    }

    private suspend fun handleFailure(
        notification: Notification,
        attempt: Int,
        e: Exception,
    ) {
        val retryable = (e as? NotificationDeliveryException)?.retryable ?: true
        val msg = (e.message ?: e::class.simpleName ?: "Unknown error")

        if (!retryable || attempt >= notification.maxAttempts) {
            logger.error(e) { "Notification ${notification.id} permanently failed after $attempt attempt(s): $msg" }
            notificationsRepository.markDead(notification.id, attempt, msg)
            NotificationMetrics.dead(notification.channel.dbValue)
            Sentry.captureException(e)
        } else {
            val nextAttemptAt = OffsetDateTime.now().plus(backoff(attempt))
            logger.warn(e) { "Notification ${notification.id} attempt $attempt failed, retrying at $nextAttemptAt: $msg" }
            notificationsRepository.markRetry(notification.id, attempt, msg, nextAttemptAt)
            NotificationMetrics.failed(notification.channel.dbValue)
        }
    }

    private companion object {
        const val BATCH_SIZE = 100
        const val STALE_CLAIM_MINUTES = 10L
        const val BASE_BACKOFF_SECONDS = 60L
        const val MAX_BACKOFF_SECONDS = 3600L

        /** Exponential backoff with ±20% jitter: 60s, 120s, 240s, … capped at 1h. */
        fun backoff(attempt: Int): Duration {
            val exp = BASE_BACKOFF_SECONDS shl (attempt - 1).coerceIn(0, 16)
            val capped = min(exp, MAX_BACKOFF_SECONDS)
            val jitter = (capped * 0.2 * Random.nextDouble()).toLong()
            return Duration.ofSeconds(capped + jitter)
        }

        /**
         * Channel map shared across all fires. Quartz constructs a fresh job instance every tick, so
         * building channels per-instance would create (and leak) a new HTTP client each minute. Config
         * is env-derived and stable for the process lifetime, so a single JVM-lifetime map is correct;
         * tests inject their own map via the constructor.
         */
        val sharedChannels: Map<NotificationChannelType, NotificationChannel> by lazy { buildChannels() }

        /**
         * Builds the channel map from the environment (mirrors the Koin factories). The Telegram
         * channel is only registered when Telegram is enabled — otherwise a stray telegram row
         * dead-letters rather than being silently swallowed by a logging stub.
         */
        private fun buildChannels(): Map<NotificationChannelType, NotificationChannel> {
            val emailConfig = EmailConfig.fromEnv()
            val resendClient =
                if (emailConfig.enabled && emailConfig.resendApiKey.isNotBlank()) {
                    ResendClientImpl(emailConfig.resendApiKey)
                } else {
                    LoggingResendClient()
                }

            val telegramConfig = TelegramConfig.fromEnv()

            return buildMap {
                put(
                    NotificationChannelType.Email,
                    EmailChannel(resendClient, EmailTemplateRenderer(), emailConfig),
                )
                if (telegramConfig.enabled) {
                    val telegramClient =
                        if (telegramConfig.botToken.isNotBlank()) {
                            TelegramClientImpl(telegramConfig.botToken)
                        } else {
                            LoggingTelegramClient()
                        }
                    put(
                        NotificationChannelType.Telegram,
                        TelegramChannel(telegramClient, TelegramMessageRenderer()),
                    )
                }
            }
        }
    }
}
