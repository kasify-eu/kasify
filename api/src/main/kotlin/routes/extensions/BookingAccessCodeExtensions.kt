package eu.kasify.routes.extensions

import eu.kasify.db.BookingAccessCodesRepository
import eu.kasify.model.BookingAccessCode
import eu.kasify.routes.exceptions.NotFoundException
import io.ktor.server.application.ApplicationCall

suspend fun ApplicationCall.getPathAccessCodeBooking(bookingAccessCodesRepository: BookingAccessCodesRepository): BookingAccessCode {
    val bookingAccessCodeId = getBookingAccessCodeId()

    val bookingAccessCode = bookingAccessCodesRepository.getById(bookingAccessCodeId) ?: throw NotFoundException("Booking not found")

    return bookingAccessCode
}
