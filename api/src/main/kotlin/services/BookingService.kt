package eu.kasify.services

import eu.kasify.db.BookingsRepository
import eu.kasify.db.CreateTaskRequest
import eu.kasify.db.GuestsRepository
import eu.kasify.db.PropertiesRepository
import eu.kasify.db.SesCommunicationsRepository
import eu.kasify.db.TasksRepository
import eu.kasify.db.model.PropertyService
import eu.kasify.model.Booking
import eu.kasify.model.api.requests.BookingData
import eu.kasify.routes.exceptions.ConflictException
import io.github.oshai.kotlinlogging.KotlinLogging
import java.time.LocalTime
import kotlin.uuid.Uuid

private val logger = KotlinLogging.logger {}

class BookingService(
    private val bookingsRepository: BookingsRepository,
    private val tasksRepository: TasksRepository,
    private val propertiesRepository: PropertiesRepository,
    private val guestsRepository: GuestsRepository,
    private val communicationsRepository: SesCommunicationsRepository = SesCommunicationsRepository(),
) {
    /**
     * Create a booking and optionally auto-create an associated cleaning task.
     * The cleaning task is scheduled based on property settings:
     * - Starts at checkout time (default 11:00)
     * - Duration from property.cleaningDurationMinutes (default 120 minutes)
     * - Auto-assigned to property.defaultCleanerId
     */
    suspend fun createBookingWithCleaningTask(
        bookingData: BookingData,
        propertyId: Uuid,
        autoCreateCleaningTask: Boolean = true,
    ): Booking {
        // Create the booking first
        val booking = bookingsRepository.createBooking(bookingData, propertyId)

        // Auto-create cleaning task if enabled
        if (autoCreateCleaningTask && bookingData.to != null) {
            try {
                createCleaningTaskForBooking(booking, propertyId)
            } catch (e: Exception) {
                // Log error but don't fail the booking creation
                logger.error(e) { "Failed to create cleaning task for booking ${booking.id}" }
            }
        }

        return booking
    }

    /**
     * Create a cleaning task for an existing booking.
     */
    private suspend fun createCleaningTaskForBooking(
        booking: Booking,
        propertyId: Uuid,
    ) {
        val property = propertiesRepository.getProperty(propertyId)
        if (property == null) {
            logger.warn { "Property $propertyId not found when creating cleaning task for booking ${booking.id}" }
            return
        }

        // Schedule cleaning at the property's configured checkout time (carried on the booking's calendar event).
        val checkoutDate = booking.to
        val checkoutTime = booking.checkoutTime

        // Calculate end time, capping at 23:59 if it would overflow to next day
        val durationMinutes = property.cleaningDurationMinutes.toLong()
        val calculatedEndTime = checkoutTime.plusMinutes(durationMinutes)
        val endTime =
            if (calculatedEndTime.isBefore(checkoutTime)) {
                // Overflow happened, cap at end of day
                LocalTime.of(23, 59)
            } else {
                calculatedEndTime
            }

        val taskRequest =
            CreateTaskRequest(
                propertyId = propertyId,
                bookingId = booking.id,
                taskType = PropertyService.Cleaning,
                from = checkoutDate.atTime(checkoutTime),
                to = checkoutDate.atTime(endTime),
                assignedTo = property.defaultCleanerId,
                notes = "",
            )

        val task = tasksRepository.create(taskRequest)
        logger.info { "Created cleaning task ${task.id} for booking ${booking.id} at property ${property.name}" }
    }

    /**
     * Update a booking with guard rails:
     * - Reject guest-count changes once the booking has been submitted to SES.
     * - After a guest-count change, recompute checkin_completed_at to reflect
     *   the new expected vs. registered comparison (registered guests come
     *   from the guests table, expected from adults+children+infants).
     */
    suspend fun updateBooking(
        id: Uuid,
        data: BookingData,
    ): Booking? {
        val existing = bookingsRepository.getBooking(id) ?: return null

        val guestCountChanged =
            (data.adultsCount != null && data.adultsCount != existing.adultsCount) ||
                (data.childrenCount != null && data.childrenCount != existing.childrenCount) ||
                (data.infantsCount != null && data.infantsCount != existing.infantsCount)

        if (guestCountChanged && communicationsRepository.isPvAltaFiled(existing.id)) {
            throw ConflictException(
                "Guest counts cannot be modified after the SES report has been submitted",
                code = "booking.ses_already_submitted",
            )
        }

        val updated = bookingsRepository.updateBooking(id, data) ?: return null

        if (guestCountChanged) {
            refreshCheckinCompletion(updated)
            return bookingsRepository.getBooking(id)
        }
        return updated
    }

    private suspend fun refreshCheckinCompletion(booking: Booking) {
        val expected =
            (booking.adultsCount ?: 0) +
                (booking.childrenCount ?: 0) +
                (booking.infantsCount ?: 0)
        val registered = guestsRepository.getAllGuestsByBooking(booking.id).size
        val shouldBeCompleted = expected > 0 && registered >= expected

        when {
            shouldBeCompleted && booking.checkinCompletedAt == null ->
                bookingsRepository.markCheckinCompletedAt(booking.id)
            !shouldBeCompleted && booking.checkinCompletedAt != null ->
                bookingsRepository.clearCheckinCompletedAt(booking.id)
        }
    }
}
