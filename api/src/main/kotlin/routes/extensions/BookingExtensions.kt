package eu.kasify.routes.extensions

import eu.kasify.db.BookingsRepository
import eu.kasify.model.Booking
import eu.kasify.routes.exceptions.NotFoundException
import io.ktor.server.application.ApplicationCall

suspend fun ApplicationCall.getPathBooking(bookingsRepository: BookingsRepository): Booking {
    val bookingId = getBookingId()

    val booking = bookingsRepository.getBooking(bookingId) ?: throw NotFoundException("Booking not found")

    return booking
}
