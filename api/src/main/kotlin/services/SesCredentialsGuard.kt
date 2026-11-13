package eu.kasify.services

import eu.kasify.config.EmailConfig
import eu.kasify.config.TelegramConfig
import eu.kasify.db.IntegrationsRepository
import eu.kasify.db.NotificationsRepository
import eu.kasify.db.UserTelegramLinksRepository
import eu.kasify.db.UsersRepository
import eu.kasify.model.SesHospedajesIntegration
import eu.kasify.services.notifications.ChannelResolver
import eu.kasify.services.notifications.NotificationService
import io.github.oshai.kotlinlogging.KotlinLogging
import io.sentry.Sentry

/**
 * Handles the SES credential-error lifecycle on the integration row. A credential-family Layer-1
 * código (see [CREDENTIAL_ERROR_CODES]) means the account's SES portal configuration is wrong —
 * not the booking data — so instead of rejecting the communication we flag the *integration* and
 * pause all auto-submissions/polls for the account (the discovery queries and the poll job skip
 * flagged integrations).
 *
 * [onCredentialError] is first-writer-wins: only the call that makes the null→error transition
 * notifies the account owners (and Sentry), so an outage produces exactly one email regardless of
 * how many bookings fail. [onSesSuccess] self-heals the flag on any successful SES response —
 * covering the case where the owner fixed the credentials directly in the SES portal (or via a
 * manual admin Submit) without re-saving them in Kasify.
 *
 * Both methods never throw — a bookkeeping failure must not distort the outcome of the SES call
 * that triggered it — but they are NOT silent: every swallowed failure is escalated to Sentry,
 * because a failed flag write means the pause never engaged (discovery keeps hammering SES with
 * bad credentials and no email fires) and a lost notification means the owner is never told why
 * their submissions stopped.
 */
open class SesCredentialsGuard(
    private val integrationsRepository: IntegrationsRepository,
    private val usersRepository: UsersRepository = UsersRepository(),
    private val notificationService: NotificationService = defaultNotificationService(),
) {
    private val logger = KotlinLogging.logger {}

    open suspend fun onCredentialError(
        integration: SesHospedajesIntegration,
        codigo: Int,
        descripcion: String?,
    ) {
        val integrationId = integration.id ?: return
        val transitioned =
            try {
                integrationsRepository.markSesCredentialsError(integrationId, codigo, descripcion)
            } catch (e: Exception) {
                // The pause never engaged: every tick keeps re-submitting with bad credentials and
                // no owner email fires. Without this capture the outage would be invisible (the
                // per-booking Sentry event of the old rejected path no longer exists).
                logger.error(e) { "Failed to record SES credential error for integration $integrationId" }
                Sentry.captureException(e) { scope ->
                    scope.setTag("kind", "ses_credentials_flag_write_failed")
                    scope.setTag("account_id", integration.accountId.toString())
                    scope.setTag("ses_error_code", codigo.toString())
                }
                return
            }
        if (!transitioned) return

        logger.error {
            "SES rejected credentials for integration $integrationId (account ${integration.accountId}): " +
                "code=$codigo desc=$descripcion. Auto-submissions paused until the credentials are updated."
        }
        Sentry.captureMessage(
            "SES credentials error for account ${integration.accountId}: code=$codigo desc=$descripcion",
        )
        notifyOwners(integration, codigo, descripcion)
    }

    /**
     * Resolve the account owners and enqueue the one-time notification. The transition that
     * triggers this fires exactly once per outage, so a failure here would lose the notification
     * permanently — retry a few times ([retryDb]) and, if it still fails, escalate to Sentry so
     * an operator can inform the owner manually. The flag itself is deliberately NOT rolled back:
     * a paused account with a missed email is recoverable, a spamming account is not.
     */
    private suspend fun notifyOwners(
        integration: SesHospedajesIntegration,
        codigo: Int,
        descripcion: String?,
    ) {
        try {
            retryDb("notifyOwners account=${integration.accountId}", maxAttempts = 3) {
                val owners =
                    usersRepository.getUsersByIds(usersRepository.getUserIdsByAccountId(integration.accountId))
                notificationService.enqueueSesCredentialsError(integration, codigo, descripcion, owners)
            }
        } catch (e: Exception) {
            logger.error(e) {
                "Credential-error notification for account ${integration.accountId} lost — " +
                    "the owners must be informed manually."
            }
            Sentry.captureException(e) { scope ->
                scope.setTag("kind", "ses_credentials_notification_lost")
                scope.setTag("account_id", integration.accountId.toString())
                scope.setTag("ses_error_code", codigo.toString())
            }
        }
    }

    open suspend fun onSesSuccess(integration: SesHospedajesIntegration) {
        val integrationId = integration.id ?: return
        // Always hit the DB: the caller's integration snapshot may predate a concurrent flag write
        // (e.g. the hourly tick flags the account while a manual Submit is in flight), and gating
        // on the stale snapshot would leave the account paused right after SES proved the
        // credentials work. The clear is a cheap conditional UPDATE that no-ops when unflagged.
        try {
            if (integrationsRepository.clearSesCredentialsError(integrationId)) {
                logger.info {
                    "SES call succeeded for integration $integrationId (account ${integration.accountId}); " +
                        "credential-error flag cleared, auto-submissions resume"
                }
            }
        } catch (e: Exception) {
            // The account stays paused despite working credentials — escalate so it doesn't linger.
            logger.error(e) { "Failed to clear SES credential error for integration $integrationId" }
            Sentry.captureException(e) { scope ->
                scope.setTag("kind", "ses_credentials_flag_clear_failed")
                scope.setTag("account_id", integration.accountId.toString())
            }
        }
    }

    companion object {
        /** The credential-family códigos — canonical set lives in [SesFailureClassifier]. */
        val CREDENTIAL_ERROR_CODES = SesFailureClassifier.CREDENTIAL_ERROR_CODES

        fun isCredentialError(codigo: Int): Boolean = SesFailureClassifier.isCredentialError(codigo)

        /** Production default wiring; jobs and Koin can inject their own instances. */
        fun defaultNotificationService(): NotificationService =
            NotificationService(
                notificationsRepository = NotificationsRepository(),
                channelResolver = ChannelResolver(UserTelegramLinksRepository(), TelegramConfig.fromEnv()),
                config = EmailConfig.fromEnv(),
            )
    }
}
