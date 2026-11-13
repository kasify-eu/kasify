package eu.kasify.model.api.responses

import eu.kasify.model.Property
import eu.kasify.model.api.CheckinGuest
import eu.kasify.model.api.PropertyInfo
import eu.kasify.serialization.JavaLocalDateSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.LocalDate
import eu.kasify.model.Booking as DomainBooking

@Serializable
data class CheckinResponse(
    val property: PropertyInfo,
    @Serializable(with = JavaLocalDateSerializer::class)
    val from: LocalDate,
    @Serializable(with = JavaLocalDateSerializer::class)
    val to: LocalDate,
    @SerialName("guests_number")
    val guestsNumber: Int,
    @SerialName("booking_code")
    val bookingCode: String,
    val guests: List<CheckinGuest>,
    @SerialName("checkin_completed")
    val checkinCompleted: Boolean,
    @SerialName("checkin_closed")
    val checkinClosed: Boolean,
    // True when the booking reports any children/infants, or when any already
    // registered guest's DOB makes them a minor. Tells the form whether to ask
    // for "parentesco" (relationship) on adult registrations.
    @SerialName("has_minors")
    val hasMinors: Boolean,
) {
    companion object {
        fun from(
            booking: DomainBooking,
            property: Property,
            guests: List<CheckinGuest>,
            checkinCompleted: Boolean,
            checkinClosed: Boolean,
            hasMinors: Boolean,
        ): CheckinResponse =
            CheckinResponse(
                property =
                    PropertyInfo(
                        name = property.name,
                        address = property.address,
                        city = property.city,
                        zipcode = property.zipcode,
                        coverImageUrl = property.coverImageUrl,
                    ),
                from = booking.from,
                to = booking.to,
                guestsNumber =
                    (booking.adultsCount ?: 0) +
                        (booking.childrenCount ?: 0) +
                        (booking.infantsCount ?: 0),
                // The public snapshot echoes the check-in code (the welcome page
                // builds the guest-form URL from it), never the OTA channel id.
                bookingCode = booking.checkinCode,
                guests = guests,
                checkinCompleted = checkinCompleted,
                checkinClosed = checkinClosed,
                hasMinors = hasMinors,
            )
    }
}
