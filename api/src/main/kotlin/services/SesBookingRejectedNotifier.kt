package eu.kasify.services

import eu.kasify.db.UsersRepository
import eu.kasify.model.Booking
import eu.kasify.model.Property
import eu.kasify.model.SesCommunicationType
import eu.kasify.services.notifications.NotificationService
import io.github.oshai.kotlinlogging.KotlinLogging
import io.sentry.Sentry

/**
 * Notifies the account owners that SES rejected one booking's submission because of the
 * communicated data (comm row `rejected` — see [SesFailureClassifier.SesFailureCause.BookingData]
 * and the poll job's estado-6 path). Shared by the synchronous submit path and the poll job.
 *
 * Unlike [SesCredentialsGuard]/[SesPropertyConfigGuard] there is no first-writer-wins flag: every
 * rejection is a distinct actionable event, and the rejected-alta retry gate already bounds the
 * frequency to one rejection per data change. [notifyOwners] never throws — a notification failure
 * must not distort the outcome of the SES call that triggered it — but a lost notification is
 * escalated to Sentry so an operator can inform the owner manually.
 */
open class SesBookingRejectedNotifier(
    private val usersRepository: UsersRepository = UsersRepository(),
    private val notificationService: NotificationService = SesCredentialsGuard.defaultNotificationService(),
) {
    private val logger = KotlinLogging.logger {}

    open suspend fun notifyOwners(
        booking: Booking,
        property: Property,
        type: SesCommunicationType,
        errorMessage: String?,
    ) {
        try {
            retryDb("notifyBookingRejected booking=${booking.id}", maxAttempts = 3) {
                val owners =
                    usersRepository.getUsersByIds(usersRepository.getUserIdsByAccountId(property.accountId))
                notificationService.enqueueSesBookingRejected(booking, property, type, errorMessage, owners)
            }
        } catch (e: Exception) {
            logger.error(e) {
                "Booking-rejected notification for booking ${booking.id} lost — " +
                    "the owners must be informed manually."
            }
            Sentry.captureException(e) { scope ->
                scope.setTag("kind", "ses_booking_rejected_notification_lost")
                scope.setTag("account_id", property.accountId.toString())
                scope.setTag("booking_id", booking.id.toString())
            }
        }
    }
}
