package eu.kasify.db

import eu.kasify.TEST_ACCOUNT_1_PROPERTY_1
import eu.kasify.model.api.requests.BookingsFilters
import eu.kasify.routes.BaseRouteDBTests
import io.perracodex.exposed.pagination.Pageable
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.junit.Test
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/**
 * Reproduces the /admin/bookings date-range report: a booking spanning
 * Jul 7 2026 → Jan 7 2027 must be returned when filtering with
 * from=2026-07-08&to=2027-01-31 (stay-overlap semantics), while a booking
 * spanning Jul 7 2027 → Jan 7 2028 must appear with from-only but drop out
 * once `to` is set.
 */
class BookingsDateOverlapFilterTest : BaseRouteDBTests() {
    private val repository = BookingsRepository()

    private fun insertBooking(
        bookingId: Uuid,
        checkin: String,
        checkout: String,
    ) {
        val eventId = Uuid.random()
        val propertyId = TEST_ACCOUNT_1_PROPERTY_1.id
        testDBConnection.executeQuery(
            "INSERT INTO public.calendar_events (id, property_id, from_datetime, to_datetime, type, created_at, updated_at) " +
                "VALUES('$eventId', '$propertyId', '$checkin', '$checkout', 'booking', now(), now())",
        )
        testDBConnection.executeQuery(
            "INSERT INTO public.bookings " +
                "(id, calendar_event_id, property_id, channel, channel_id, status, created_at, updated_at) " +
                "VALUES ('$bookingId', '$eventId', '$propertyId', 'direct', 'ref-$bookingId', 'confirmed', now(), now())",
        )
    }

    @Test
    fun `booking overlapping the range is returned, booking after the range is not`() =
        runBlocking {
            val inRange = Uuid.random() // Jul 7 2026 → Jan 7 2027: overlaps [2026-07-08, 2027-01-31]
            val afterRange = Uuid.random() // Jul 7 2027 → Jan 7 2028: starts after the range ends
            insertBooking(inRange, "2026-07-07T14:00:00Z", "2027-01-07T10:00:00Z")
            insertBooking(afterRange, "2027-07-07T14:00:00Z", "2028-01-07T10:00:00Z")

            val pageable = Pageable(page = 0, position = null, size = 100)
            val propertyId = TEST_ACCOUNT_1_PROPERTY_1.id

            // from-only: both bookings check out after 2026-07-08 → both returned
            val fromOnly =
                repository
                    .getBookings(
                        BookingsFilters(from = LocalDate.parse("2026-07-08"), propertyId = propertyId),
                        pageable,
                    ).content
                    .map { it.id }
            assertTrue(inRange in fromOnly, "from-only: booking Jul 2026→Jan 2027 missing")
            assertTrue(afterRange in fromOnly, "from-only: booking Jul 2027→Jan 2028 missing")

            // from+to: the 2026→2027 booking overlaps the range and must stay;
            // the 2027→2028 one starts after `to` and must drop out
            val fromAndTo =
                repository
                    .getBookings(
                        BookingsFilters(
                            from = LocalDate.parse("2026-07-08"),
                            to = LocalDate.parse("2027-01-31"),
                            propertyId = propertyId,
                        ),
                        pageable,
                    ).content
                    .map { it.id }
            assertTrue(inRange in fromAndTo, "from+to: booking Jul 2026→Jan 2027 was wrongly filtered out")
            assertTrue(afterRange !in fromAndTo, "from+to: booking Jul 2027→Jan 2028 should be excluded")
        }
}
