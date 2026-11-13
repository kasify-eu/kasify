package eu.kasify.model.api.requests

import eu.kasify.db.model.BookingChannel
import eu.kasify.db.model.BookingStatus
import eu.kasify.db.model.PaymentType
import eu.kasify.serialization.JavaLocalDateSerializer
import eu.kasify.serialization.JavaLocalTimeSerializer
import io.ktor.server.plugins.requestvalidation.ValidationResult
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit

@Serializable
data class BookingData(
    @Serializable(with = JavaLocalDateSerializer::class)
    val from: LocalDate? = null,
    @Serializable(with = JavaLocalDateSerializer::class)
    val to: LocalDate? = null,
    val channel: BookingChannel? = null,
    @SerialName("channel_id")
    val channelId: String? = null, // external booking id reference,
    val status: BookingStatus? = null,
    @SerialName("payment_type")
    val paymentType: PaymentType? = null,
    val notes: String? = null,
    @SerialName("guest_first_name")
    val guestFirstName: String? = null,
    @SerialName("guest_last_name")
    val guestLastName: String? = null,
    @SerialName("guest_email")
    val guestEmail: String? = null,
    @SerialName("guest_phone")
    val guestPhone: String? = null,
    @SerialName("adults_count")
    val adultsCount: Int? = null,
    @SerialName("children_count")
    val childrenCount: Int? = null,
    @SerialName("infants_count")
    val infantsCount: Int? = null,
    @SerialName("special_requests")
    val specialRequests: String? = null,
    @SerialName("would_host_again")
    val wouldHostAgain: Boolean? = null,
    @SerialName("guest_review_notes")
    val guestReviewNotes: String? = null,
    @Serializable(with = JavaLocalTimeSerializer::class)
    @SerialName("arrival_time")
    val arrivalTime: LocalTime? = null,
    @Serializable(with = JavaLocalTimeSerializer::class)
    @SerialName("departure_time")
    val departureTime: LocalTime? = null,
    @Serializable(with = JavaLocalTimeSerializer::class)
    @SerialName("checkin_time")
    val checkinTime: LocalTime? = null,
    @Serializable(with = JavaLocalTimeSerializer::class)
    @SerialName("checkout_time")
    val checkoutTime: LocalTime? = null,
)

// Validation constants
const val MAX_BOOKING_DURATION_DAYS = 365L
const val MAX_GUEST_NAME_LENGTH = 100
const val MAX_GUEST_PHONE_LENGTH = 20
const val MAX_CHANNEL_ID_LENGTH = 255
const val MAX_NOTES_LENGTH = 1000
const val MAX_SPECIAL_REQUESTS_LENGTH = 1000
const val MAX_GUESTS_PER_BOOKING = 50
const val MIN_ADULTS_COUNT = 0
const val EMAIL_REGEX = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"

/**
 * Validates BookingData fields
 * Enforces that both dates must be present and valid
 */
fun BookingData.validate(): ValidationResult {
    val errors = mutableListOf<String>()

    // Check if dates are provided
    if (this.from != null || this.to != null) {
        when {
            this.from == null -> {
                errors.add("Check-in date (from) is required")
            }
            this.to == null -> {
                errors.add("Check-out date (to) is required")
            }
            else -> {
                // Both dates present, validate them
                errors.addAll(validateDates(this.from, this.to))
            }
        }
    }

    // Validate all other fields
    errors.addAll(validateCommonFields())

    if (errors.isNotEmpty()) {
        return ValidationResult.Invalid(errors)
    }

    return ValidationResult.Valid
}

/**
 * Validates date ranges
 */
private fun validateDates(
    from: LocalDate,
    to: LocalDate,
): List<String> {
    val errors = mutableListOf<String>()
    val today = LocalDate.now()

    // Check-out must be after check-in
    if (!to.isAfter(from)) {
        errors.add("Check-out date must be after check-in date")
    }

    // Booking duration must be reasonable
    val duration = ChronoUnit.DAYS.between(from, to)
    if (duration > MAX_BOOKING_DURATION_DAYS) {
        errors.add("Booking duration cannot exceed $MAX_BOOKING_DURATION_DAYS days")
    }

    return errors
}

/**
 * Validates common fields present in both create and update operations
 */
private fun BookingData.validateCommonFields(): List<String> {
    val errors = mutableListOf<String>()

    // Validate guest email format if provided
    if (!this.guestEmail.isNullOrBlank()) {
        if (!this.guestEmail.matches(Regex(EMAIL_REGEX))) {
            errors.add("Guest email format is invalid")
        }
    }

    // Validate guest name lengths
    if (this.guestFirstName != null && this.guestFirstName.length > MAX_GUEST_NAME_LENGTH) {
        errors.add("Guest first name must not exceed $MAX_GUEST_NAME_LENGTH characters")
    }

    if (this.guestLastName != null && this.guestLastName.length > MAX_GUEST_NAME_LENGTH) {
        errors.add("Guest last name must not exceed $MAX_GUEST_NAME_LENGTH characters")
    }

    // Validate guest phone length
    if (this.guestPhone != null && this.guestPhone.length > MAX_GUEST_PHONE_LENGTH) {
        errors.add("Guest phone must not exceed $MAX_GUEST_PHONE_LENGTH characters")
    }

    // Validate guest counts
    if (this.adultsCount != null) {
        if (this.adultsCount < MIN_ADULTS_COUNT) {
            errors.add("Adults count cannot be negative")
        }
        if (this.adultsCount > MAX_GUESTS_PER_BOOKING) {
            errors.add("Adults count cannot exceed $MAX_GUESTS_PER_BOOKING")
        }
    }

    if (this.childrenCount != null) {
        if (this.childrenCount < 0) {
            errors.add("Children count cannot be negative")
        }
        if (this.childrenCount > MAX_GUESTS_PER_BOOKING) {
            errors.add("Children count cannot exceed $MAX_GUESTS_PER_BOOKING")
        }
    }

    if (this.infantsCount != null) {
        if (this.infantsCount < 0) {
            errors.add("Infants count cannot be negative")
        }
        if (this.infantsCount > MAX_GUESTS_PER_BOOKING) {
            errors.add("Infants count cannot exceed $MAX_GUESTS_PER_BOOKING")
        }
    }

    // Validate total guests
    val totalGuests = (this.adultsCount ?: 0) + (this.childrenCount ?: 0) + (this.infantsCount ?: 0)
    if (totalGuests > MAX_GUESTS_PER_BOOKING) {
        errors.add("Total guest count cannot exceed $MAX_GUESTS_PER_BOOKING")
    }

    // Validate notes length
    if (this.notes != null && this.notes.length > MAX_NOTES_LENGTH) {
        errors.add("Notes must not exceed $MAX_NOTES_LENGTH characters")
    }

    // Validate special requests length
    if (this.specialRequests != null && this.specialRequests.length > MAX_SPECIAL_REQUESTS_LENGTH) {
        errors.add("Special requests must not exceed $MAX_SPECIAL_REQUESTS_LENGTH characters")
    }

    // Validate guest review notes length
    if (this.guestReviewNotes != null && this.guestReviewNotes.length > MAX_NOTES_LENGTH) {
        errors.add("Guest review notes must not exceed $MAX_NOTES_LENGTH characters")
    }

    // Validate channel ID length
    if (this.channelId != null && this.channelId.length > MAX_CHANNEL_ID_LENGTH) {
        errors.add("Channel ID must not exceed $MAX_CHANNEL_ID_LENGTH characters")
    }

    return errors
}
