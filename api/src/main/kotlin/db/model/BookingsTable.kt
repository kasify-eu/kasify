package eu.kasify.db.model
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.timestampWithTimeZone
import org.jetbrains.exposed.v1.javatime.time
import org.postgresql.util.PGobject

object BookingsTable : Table(name = "bookings") {
    val id = uuid("id").autoGenerate()
    val calendarEventId = reference("calendar_event_id", CalendarEventsTable.id)
    val propertyId = reference("property_id", PropertiesTable.id)

    val channel =
        customEnumeration(
            name = "channel",
            sql = "booking_channel",
            fromDb = { value ->
                BookingChannel.entries.find { it.name.equals(value as String, ignoreCase = true) }
                    ?: throw IllegalArgumentException("Invalid BookingChannel: $value")
            },
            toDb = { PGEnum("booking_channel", it) },
        )

    val channelId = text("channel_id")

    // Value comes from the column DEFAULT; Exposed must omit it on insert.
    val checkinCode = text("checkin_code").databaseGenerated()

    val status =
        customEnumeration(
            name = "status",
            sql = "booking_status",
            fromDb = { value ->
                BookingStatus.entries.find { it.name.equals(value as String, ignoreCase = true) }
                    ?: throw IllegalArgumentException("Invalid BookingStatus: $value")
            },
            toDb = { PGEnum("booking_status", it) },
        )

    // Generic, channel-agnostic payment type. PGEnum can't be reused here because it lowercases the
    // enum name, which would mangle multi-word values (e.g. credit_card) — so map via dbValue.
    val paymentType =
        customEnumeration(
            name = "payment_type",
            sql = "payment_type",
            fromDb = { PaymentType.fromDb(it as String) },
            toDb = {
                PGobject().apply {
                    type = "payment_type"
                    value = it.dbValue
                }
            },
        )

    val notes = text("notes").nullable()
    val guestFirstName = text("guest_first_name").nullable()
    val guestLastName = text("guest_last_name").nullable()
    val guestEmail = text("guest_email").nullable()
    val guestPhone = text("guest_phone").nullable()
    val adultsCount = integer("adults_count").nullable()
    val childrenCount = integer("children_count").nullable()
    val infantsCount = integer("infants_count").nullable()

    val checkinCompletedAt = timestampWithTimeZone("checkin_completed_at").nullable()

    val specialRequests = text("special_requests").nullable()
    val arrivalTime = time("arrival_time").nullable()
    val departureTime = time("departure_time").nullable()

    // Owner's private per-stay guest review. would_host_again is tri-state:
    // null = not reviewed, true/false = explicit decision.
    val wouldHostAgain = bool("would_host_again").nullable()
    val guestReviewNotes = text("guest_review_notes").nullable()

    val lastSyncAt = timestampWithTimeZone("last_sync_at").nullable()
    val cancelledAt = timestampWithTimeZone("cancelled_at").nullable()
    val createdAt = timestampWithTimeZone("created_at")
    val updatedAt = timestampWithTimeZone("updated_at")
}
