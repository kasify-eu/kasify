package eu.kasify.services

import eu.kasify.db.PropertiesRepository
import eu.kasify.db.UsersRepository
import eu.kasify.model.Property
import eu.kasify.services.notifications.NotificationService
import io.github.oshai.kotlinlogging.KotlinLogging
import io.sentry.Sentry

/**
 * Handles the SES property-config error lifecycle on the property row — the property-scoped analog
 * of [SesCredentialsGuard]. A [SesFailureCause.PropertyConfig] error (SES: "No existe un
 * establecimiento para ese código") means the *property's* SES configuration is wrong — not the
 * booking data and not the account's credentials — so instead of rejecting the communication we
 * flag the property and pause alta/anulación discovery **for that property only** (shared SQL gate
 * in `db/SesCredentialsErrorGate.kt`). Polls are deliberately NOT paused: in-flight lotes must
 * reach a terminal estado so no stale lote can re-flag the property after the code is fixed.
 *
 * [onConfigError] is first-writer-wins: only the call that makes the null→error transition
 * notifies the account owners (and Sentry), so a wrong code produces exactly one email regardless
 * of how many of the property's bookings fail. [onSesSuccess] self-heals the flag when a later SES
 * estado for the property succeeds; re-saving the establishment code also clears it (optimistic,
 * in `PropertiesRepository` — a still-wrong code re-flags and re-notifies on the next tick).
 *
 * The two guards stay separate classes on purpose: their scope entities, error payloads (numeric
 * código vs free text) and notifications all differ, so a shared abstraction would only obscure
 * the identical *lifecycle* they follow (flag → notify once → never throw → escalate bookkeeping
 * failures to Sentry → conditional clear).
 */
open class SesPropertyConfigGuard(
    private val propertiesRepository: PropertiesRepository,
    private val usersRepository: UsersRepository = UsersRepository(),
    private val notificationService: NotificationService = SesCredentialsGuard.defaultNotificationService(),
) {
    private val logger = KotlinLogging.logger {}

    open suspend fun onConfigError(
        property: Property,
        errorMessage: String?,
    ) {
        val transitioned =
            try {
                propertiesRepository.markSesConfigError(property.id, errorMessage)
            } catch (e: Exception) {
                // The pause never engaged: every tick keeps re-submitting the property's bookings
                // with a bad establishment code and no owner email fires. Escalate so the outage
                // is visible.
                logger.error(e) { "Failed to record SES config error for property ${property.id}" }
                Sentry.captureException(e) { scope ->
                    scope.setTag("kind", "ses_property_config_flag_write_failed")
                    scope.setTag("account_id", property.accountId.toString())
                    scope.setTag("property_id", property.id.toString())
                }
                return
            }
        if (!transitioned) return

        logger.error {
            "SES rejected the establishment code of property ${property.id} (account ${property.accountId}): " +
                "$errorMessage. The property's auto-submissions are paused until the code is fixed."
        }
        Sentry.captureMessage(
            "SES establishment-code error for property ${property.id} (account ${property.accountId}): $errorMessage",
        )
        notifyOwners(property, errorMessage)
    }

    /**
     * Resolve the account owners and enqueue the one-time notification. Fires exactly once per
     * outage, so a failure here would lose the notification permanently — retry a few times
     * ([retryDb]) and, if it still fails, escalate to Sentry so an operator can inform the owner
     * manually. The flag itself is deliberately NOT rolled back: a paused property with a missed
     * email is recoverable, a spamming property is not.
     */
    private suspend fun notifyOwners(
        property: Property,
        errorMessage: String?,
    ) {
        try {
            retryDb("notifyOwners property=${property.id}", maxAttempts = 3) {
                val owners =
                    usersRepository.getUsersByIds(usersRepository.getUserIdsByAccountId(property.accountId))
                notificationService.enqueueSesPropertyConfigError(property, errorMessage, owners)
            }
        } catch (e: Exception) {
            logger.error(e) {
                "Property-config error notification for property ${property.id} lost — " +
                    "the owners must be informed manually."
            }
            Sentry.captureException(e) { scope ->
                scope.setTag("kind", "ses_property_config_notification_lost")
                scope.setTag("account_id", property.accountId.toString())
                scope.setTag("property_id", property.id.toString())
            }
        }
    }

    open suspend fun onSesSuccess(property: Property) {
        // Always hit the DB: the caller's property snapshot may predate a concurrent flag write,
        // and gating on the stale snapshot would leave the property paused right after SES proved
        // the code works. The clear is a cheap conditional UPDATE that no-ops when unflagged.
        try {
            if (propertiesRepository.clearSesConfigError(property.id)) {
                logger.info {
                    "SES estado succeeded for property ${property.id} (account ${property.accountId}); " +
                        "config-error flag cleared, the property's auto-submissions resume"
                }
            }
        } catch (e: Exception) {
            // The property stays paused despite a working config — escalate so it doesn't linger.
            logger.error(e) { "Failed to clear SES config error for property ${property.id}" }
            Sentry.captureException(e) { scope ->
                scope.setTag("kind", "ses_property_config_flag_clear_failed")
                scope.setTag("account_id", property.accountId.toString())
                scope.setTag("property_id", property.id.toString())
            }
        }
    }
}
