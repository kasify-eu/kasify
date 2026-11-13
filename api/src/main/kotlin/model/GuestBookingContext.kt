package eu.kasify.model

import eu.kasify.db.model.BookingChannel
import kotlin.uuid.Uuid

/**
 * Booking-level context surfaced on the guests list. Both [channel]/[channelId] and
 * [wouldHostAgain] live on the booking, not the guest row, so the guests endpoint loads
 * them separately for a page of guests. [propertyAccountId] lets the caller withhold the
 * owner-private [wouldHostAgain] from managers of shared properties.
 */
data class GuestBookingContext(
    val bookingId: Uuid,
    val channel: BookingChannel,
    val channelId: String,
    val wouldHostAgain: Boolean?,
    val propertyAccountId: Uuid,
)
