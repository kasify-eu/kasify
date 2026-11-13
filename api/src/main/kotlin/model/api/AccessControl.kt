package eu.kasify.model.api

/**
 * Returns the total guest count from a booking.
 */
fun eu.kasify.model.Booking.guestCount(): Int = (adultsCount ?: 0) + (childrenCount ?: 0) + (infantsCount ?: 0)
