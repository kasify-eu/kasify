package eu.kasify.model.api

import eu.kasify.db.model.BookingChannel
import eu.kasify.db.model.BookingStatus
import eu.kasify.db.model.PaymentType
import eu.kasify.model.BookingAccessCode
import eu.kasify.serialization.JavaLocalDateSerializer
import eu.kasify.serialization.JavaLocalTimeSerializer
import eu.kasify.serialization.JavaOffsetDateTimeSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import kotlin.uuid.Uuid

@Serializable
class Booking(
    val id: Uuid,
    val property: Property,
    @Serializable(with = JavaLocalDateSerializer::class)
    val from: LocalDate,
    @Serializable(with = JavaLocalDateSerializer::class)
    val to: LocalDate,
    @VisibleTo(Visibility.MANAGER)
    val channel: BookingChannel,
    @VisibleTo(Visibility.MANAGER)
    @SerialName("channel_id")
    val channelId: String, // external booking id reference,
    @VisibleTo(Visibility.MANAGER)
    @SerialName("checkin_code")
    val checkinCode: String,
    val status: BookingStatus,
    @VisibleTo(Visibility.MANAGER)
    @SerialName("payment_type")
    val paymentType: PaymentType = PaymentType.Platform,
    @VisibleTo(Visibility.MANAGER)
    val notes: String? = null,
    @SerialName("guest_first_name")
    val guestFirstName: String? = null,
    @VisibleTo(Visibility.MANAGER)
    @SerialName("guest_last_name")
    val guestLastName: String? = null,
    @VisibleTo(Visibility.MANAGER)
    @SerialName("guest_email")
    val guestEmail: String? = null,
    @VisibleTo(Visibility.MANAGER)
    @SerialName("guest_phone")
    val guestPhone: String? = null,
    @SerialName("adults_count")
    val adultsCount: Int? = null,
    @SerialName("children_count")
    val childrenCount: Int? = null,
    @SerialName("infants_count")
    val infantsCount: Int? = null,
    @VisibleTo(Visibility.CONTRACTOR)
    @SerialName("special_requests")
    val specialRequests: String? = null,
    // Owner's private per-stay guest review — strictly owner-only, hidden even from managers.
    @VisibleTo(Visibility.OWNER)
    @SerialName("would_host_again")
    val wouldHostAgain: Boolean? = null,
    @VisibleTo(Visibility.OWNER)
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
    @VisibleTo(Visibility.MANAGER)
    @Serializable(with = JavaLocalDateSerializer::class)
    @SerialName("created_at")
    val createdAt: LocalDate,
    @VisibleTo(Visibility.MANAGER)
    @Serializable(with = JavaOffsetDateTimeSerializer::class)
    @SerialName("checkin_completed_at")
    val checkinCompletedAt: OffsetDateTime? = null,
    @VisibleTo(Visibility.MANAGER)
    @Serializable(with = JavaOffsetDateTimeSerializer::class)
    @SerialName("last_sync_at")
    val lastSyncAt: OffsetDateTime? = null,
    // SES communication state (PV/RH × alta/anulación) — the single source of truth for a booking's
    // SES Hospedajes status. Derived per-type by clients from these rows.
    @VisibleTo(Visibility.MANAGER)
    @SerialName("ses_communications")
    val sesCommunications: List<SesCommunicationDto> = emptyList(),
    @VisibleTo(Visibility.MANAGER)
    @SerialName("access_codes")
    val accessCodes: List<AccessCode> = listOf(),
    @VisibleTo(Visibility.MANAGER)
    @SerialName("guests")
    val guests: List<Guest>? = null,
    @VisibleTo(Visibility.STAFF)
    @SerialName("cleaning_task")
    val cleaningTask: Task? = null,
) {
    companion object {
        fun from(
            booking: eu.kasify.model.Booking,
            property: eu.kasify.model.Property? = null,
            accessCodes: List<BookingAccessCode>? = null,
            guests: List<Guest>? = null,
            cleaningTask: eu.kasify.model.Task? = null,
            sesCommunications: List<eu.kasify.model.SesCommunication>? = null,
            role: String? = null,
        ): Booking =
            Booking(
                id = booking.id,
                property =
                    property?.let { Property.from(it, role = role) }
                        ?: Property(
                            id = booking.propertyId,
                            name = booking.propertyName ?: "",
                            city = booking.propertyCity,
                            role = role,
                        ),
                from = booking.from,
                to = booking.to,
                channel = booking.channel,
                channelId = booking.channelId,
                checkinCode = booking.checkinCode,
                status = booking.status,
                paymentType = booking.paymentType,
                notes = booking.notes,
                guestFirstName = booking.guestFirstName,
                guestLastName = booking.guestLastName,
                guestEmail = booking.guestEmail,
                guestPhone = booking.guestPhone,
                adultsCount = booking.adultsCount,
                childrenCount = booking.childrenCount,
                infantsCount = booking.infantsCount,
                specialRequests = booking.specialRequests,
                wouldHostAgain = booking.wouldHostAgain,
                guestReviewNotes = booking.guestReviewNotes,
                arrivalTime = booking.arrivalTime,
                departureTime = booking.departureTime,
                checkinTime = booking.checkinTime,
                checkoutTime = booking.checkoutTime,
                createdAt = booking.createdAt,
                checkinCompletedAt = booking.checkinCompletedAt,
                lastSyncAt = booking.lastSyncAt,
                sesCommunications = sesCommunications?.map { SesCommunicationDto.from(it) } ?: emptyList(),
                accessCodes =
                    accessCodes?.map {
                        AccessCode(
                            id = it.id,
                            accessCode = it.accessCode,
                            smartlockId = it.smartlockId,
                            smartlockName = it.smartlock?.name ?: "Unknown",
                        )
                    } ?: listOf(),
                guests = guests,
                cleaningTask = cleaningTask?.let { Task.from(it) },
            )
    }
}
