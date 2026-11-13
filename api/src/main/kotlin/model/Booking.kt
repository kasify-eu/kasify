package eu.kasify.model
import eu.kasify.db.model.BookingChannel
import eu.kasify.db.model.BookingStatus
import eu.kasify.db.model.BookingsTable
import eu.kasify.db.model.CalendarEventsTable
import eu.kasify.db.model.PaymentType
import eu.kasify.db.model.PropertiesTable
import eu.kasify.db.toJavaZoneId
import eu.kasify.model.api.requests.BookingData
import io.perracodex.exposed.pagination.MapModel
import kotlinx.datetime.TimeZone
import org.jetbrains.exposed.v1.core.ResultRow
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import kotlin.uuid.Uuid

class Booking(
    val id: Uuid,
    val propertyId: Uuid,
    val propertyName: String?,
    val propertyCity: String? = null,
    val from: LocalDate,
    val to: LocalDate,
    val channel: BookingChannel,
    val channelId: String, // external booking id reference,
    val checkinCode: String, // public check-in lookup code (DB-generated)
    val status: BookingStatus,
    val paymentType: PaymentType = PaymentType.Platform,
    val notes: String? = null,
    val guestFirstName: String? = null,
    val guestLastName: String? = null,
    val guestEmail: String? = null,
    val guestPhone: String? = null,
    val adultsCount: Int? = null,
    val childrenCount: Int? = null,
    val infantsCount: Int? = null,
    val checkinCompletedAt: OffsetDateTime? = null,
    val lastSyncAt: OffsetDateTime? = null,
    val specialRequests: String? = null,
    // Owner's private per-stay guest review (null = not reviewed).
    val wouldHostAgain: Boolean? = null,
    val guestReviewNotes: String? = null,
    // Guest-estimated times (raw column values; null when the guest hasn't provided an estimate).
    val arrivalTime: LocalTime? = null,
    val departureTime: LocalTime? = null,
    // Actual stay times derived from the linked calendar event in the property's timezone.
    val checkinTime: LocalTime,
    val checkoutTime: LocalTime,
    val createdAt: LocalDate,
) {
    companion object : MapModel<Booking> {
        override fun from(row: ResultRow): Booking = fromResultRow(row, row)

        fun fromResultRow(
            eventRow: ResultRow,
            row: ResultRow,
            zone: TimeZone? = null,
        ): Booking {
            val propertyZoneId = zone?.toJavaZoneId() ?: ZoneId.of(row[PropertiesTable.tzone])

            val booking =
                Booking(
                    id = row[BookingsTable.id],
                    from = eventRow[CalendarEventsTable.from].atZoneSameInstant(propertyZoneId).toLocalDate(),
                    to = eventRow[CalendarEventsTable.to].atZoneSameInstant(propertyZoneId).toLocalDate(),
                    channel = row[BookingsTable.channel],
                    channelId = row[BookingsTable.channelId],
                    checkinCode = row[BookingsTable.checkinCode],
                    status = row[BookingsTable.status],
                    paymentType = row[BookingsTable.paymentType],
                    notes = row[BookingsTable.notes],
                    guestFirstName = row[BookingsTable.guestFirstName],
                    guestLastName = row[BookingsTable.guestLastName],
                    guestEmail = row[BookingsTable.guestEmail],
                    guestPhone = row[BookingsTable.guestPhone],
                    adultsCount = row[BookingsTable.adultsCount],
                    childrenCount = row[BookingsTable.childrenCount],
                    infantsCount = row[BookingsTable.infantsCount],
                    checkinCompletedAt = row[BookingsTable.checkinCompletedAt],
                    lastSyncAt = row[BookingsTable.lastSyncAt],
                    specialRequests = row[BookingsTable.specialRequests],
                    wouldHostAgain = row[BookingsTable.wouldHostAgain],
                    guestReviewNotes = row[BookingsTable.guestReviewNotes],
                    arrivalTime = row[BookingsTable.arrivalTime],
                    departureTime = row[BookingsTable.departureTime],
                    checkinTime = eventRow[CalendarEventsTable.from].atZoneSameInstant(propertyZoneId).toLocalTime(),
                    checkoutTime = eventRow[CalendarEventsTable.to].atZoneSameInstant(propertyZoneId).toLocalTime(),
                    createdAt = eventRow[CalendarEventsTable.createdAt].toLocalDate(),
                    propertyId = row[BookingsTable.propertyId],
                    propertyName = if (row.hasValue(PropertiesTable.name)) row[PropertiesTable.name] else null,
                    propertyCity = if (row.hasValue(PropertiesTable.city)) row[PropertiesTable.city] else null,
                )
            return booking
        }
    }

    fun isEquals(data: BookingData): Boolean {
        // The owner review fields (wouldHostAgain, guestReviewNotes) are deliberately excluded:
        // they never come from channel imports, so comparing them would make every synced
        // booking with a review look changed on each iCal import tick.
        // compare dates
        if (!this.from.isEqual(data.from) || !this.to.isEqual(data.to)) return false
        if (this.notes != data.notes) return false
        if (this.channel != data.channel) return false
        if (this.channelId != data.channelId) return false
        if (this.status != data.status) return false
        if (this.guestFirstName != data.guestFirstName) return false
        if (this.guestLastName != data.guestLastName) return false
        if (this.guestEmail != data.guestEmail) return false
        if (this.guestPhone != data.guestPhone) return false
        if (this.adultsCount != data.adultsCount) return false
        if (this.childrenCount != data.childrenCount) return false
        if (this.infantsCount != data.infantsCount) return false
        if (this.specialRequests != data.specialRequests) return false
        if (this.arrivalTime != data.arrivalTime) return false
        if (this.departureTime != data.departureTime) return false

        return true
    }
}
