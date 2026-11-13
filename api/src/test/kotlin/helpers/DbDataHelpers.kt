package eu.kasify.helpers

import java.sql.Connection
import java.sql.Timestamp
import java.time.LocalDateTime
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid

fun Connection.grantCrewAccess(
    userId: Uuid,
    propertyId: Uuid,
    service: String? = null,
    accessLevel: String = "contractor",
) {
    val sql =
        "INSERT INTO public.property_access " +
            "(id, user_id, property_id, service, access_level, created_at) " +
            "VALUES (gen_random_uuid(), ?, ?, ?::property_service, ?::property_access_level, now())"

    this.prepareStatement(sql).use { stmt ->
        stmt.setObject(1, userId.toJavaUuid())
        stmt.setObject(2, propertyId.toJavaUuid())
        stmt.setString(3, service)
        stmt.setString(4, accessLevel)
        stmt.execute()
    }
}

fun Connection.insertTask(
    taskId: Uuid = Uuid.random(),
    propertyId: Uuid,
    bookingId: Uuid? = null,
    taskType: String = "cleaning",
    status: String = "pending",
    assignedTo: Uuid? = null,
    scheduledDate: LocalDateTime,
    scheduledEndTime: LocalDateTime? = null,
    notes: String? = null,
): Uuid {
    val calendarEventId = Uuid.random()
    val end = scheduledEndTime ?: scheduledDate.plusHours(2)

    val sqlEvent =
        "INSERT INTO public.calendar_events " +
            "(id, property_id, from_datetime, to_datetime, \"type\", created_at, updated_at) " +
            "VALUES (?, ?, ?, ?, 'task'::event_type, now(), now())"

    this.prepareStatement(sqlEvent).use { stmt ->
        stmt.setObject(1, calendarEventId.toJavaUuid())
        stmt.setObject(2, propertyId.toJavaUuid())
        stmt.setTimestamp(3, Timestamp.valueOf(scheduledDate))
        stmt.setTimestamp(4, Timestamp.valueOf(end))
        stmt.execute()
    }

    val sql =
        "INSERT INTO public.tasks " +
            "(id, calendar_event_id, property_id, booking_id, task_type, status, " +
            "assigned_to, notes, public_token, created_at) " +
            "VALUES (?, ?, ?, ?, ?::property_service, ?::task_status, ?, ?, ?, now())"

    this.prepareStatement(sql).use { stmt ->
        stmt.setObject(1, taskId.toJavaUuid())
        stmt.setObject(2, calendarEventId.toJavaUuid())
        stmt.setObject(3, propertyId.toJavaUuid())
        stmt.setObject(4, bookingId?.toJavaUuid())
        stmt.setString(5, taskType)
        stmt.setString(6, status)
        stmt.setObject(7, assignedTo?.toJavaUuid())
        stmt.setString(8, notes)
        stmt.setString(9, Uuid.random().toString().replace("-", ""))
        stmt.execute()
    }

    return taskId
}

fun Connection.insertBooking(
    bookingId: Uuid = Uuid.random(),
    propertyId: Uuid,
    from: LocalDateTime,
    to: LocalDateTime,
    guestFirstName: String? = null,
    guestLastName: String? = null,
    guestEmail: String? = null,
    guestPhone: String? = null,
    notes: String? = "My notes",
    channelId: String = "airbnbId123",
    checkinCode: String = Uuid.random().toString().replace("-", ""),
    adultsCount: Int = 1,
    childrenCount: Int = 0,
    infantsCount: Int = 0,
    specialRequests: String? = null,
) {
    val calendarEventId = Uuid.random()
    val now = LocalDateTime.now()

    val sqlEvent =
        "INSERT INTO public.calendar_events " +
            "(id, property_id, from_datetime, to_datetime, \"type\", created_at, updated_at) " +
            "VALUES (?, ?, ?, ?, 'booking', ?, ?)"

    this.prepareStatement(sqlEvent).use { stmt ->
        stmt.setObject(1, calendarEventId.toJavaUuid())
        stmt.setObject(2, propertyId.toJavaUuid())
        stmt.setTimestamp(3, Timestamp.valueOf(from))
        stmt.setTimestamp(4, Timestamp.valueOf(to))
        stmt.setTimestamp(5, Timestamp.valueOf(now))
        stmt.setTimestamp(6, Timestamp.valueOf(now))
        stmt.execute()
    }

    val sql =
        "INSERT INTO public.bookings " +
            "(id, calendar_event_id, property_id, channel, channel_id, checkin_code, status, notes, " +
            "guest_first_name, guest_last_name, guest_email, guest_phone, " +
            "adults_count, children_count, infants_count, special_requests, " +
            "created_at, updated_at) " +
            "VALUES (?, ?, ?, 'airbnb', ?, ?, 'confirmed', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"

    this.prepareStatement(sql).use { stmt ->
        stmt.setObject(1, bookingId.toJavaUuid())
        stmt.setObject(2, calendarEventId.toJavaUuid())
        stmt.setObject(3, propertyId.toJavaUuid())
        stmt.setString(4, channelId)
        stmt.setString(5, checkinCode)
        stmt.setString(6, notes)
        stmt.setString(7, guestFirstName)
        stmt.setString(8, guestLastName)
        stmt.setString(9, guestEmail)
        stmt.setString(10, guestPhone)
        stmt.setInt(11, adultsCount)
        stmt.setInt(12, childrenCount)
        stmt.setInt(13, infantsCount)
        stmt.setString(14, specialRequests)
        stmt.setTimestamp(15, Timestamp.valueOf(now))
        stmt.setTimestamp(16, Timestamp.valueOf(now))
        stmt.execute()
    }
}
