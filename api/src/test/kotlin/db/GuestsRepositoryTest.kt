package eu.kasify.db

import eu.kasify.TEST_ACCOUNT_1_PROPERTY_1
import eu.kasify.TEST_ACCOUNT_1_USER_1
import eu.kasify.db.model.BookingChannel
import eu.kasify.model.api.requests.GuestsFilters
import eu.kasify.routes.BaseRouteDBTests
import io.perracodex.exposed.pagination.Pageable
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.sql.Timestamp
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class GuestsRepositoryTest : BaseRouteDBTests() {
    private val repository = GuestsRepository()

    private val propertyId = TEST_ACCOUNT_1_PROPERTY_1.id
    private val eventId = Uuid.parse("E2000000-0000-0000-0000-0000000000a1")
    private val bookingId = Uuid.parse("42000000-0000-0000-0000-0000000000a1")

    // A fixed past watermark we pin before each guest mutation; touchBooking must move updated_at
    // forward (to NOW()) so the SES discovery gate treats the booking as "data changed since rejection".
    private val pinnedUpdatedAt = "2020-01-01 00:00:00+00"

    override fun setupData() {
        testDBConnection.executeQuery(
            "INSERT INTO public.calendar_events (id, property_id, from_datetime, to_datetime, type, created_at, updated_at) " +
                "VALUES('$eventId', '$propertyId', '2026-05-20 15:00:00+00', '2026-05-25 11:00:00+00', 'booking', now(), now())",
        )
        testDBConnection.executeQuery(
            "INSERT INTO public.bookings (id, calendar_event_id, property_id, channel, channel_id, status, created_at, updated_at) " +
                "VALUES('$bookingId', '$eventId', '$propertyId', 'direct', 'ref1', 'confirmed', now(), now())",
        )
    }

    private fun pinBookingUpdatedAt() =
        testDBConnection.executeQuery(
            "UPDATE bookings SET updated_at = '$pinnedUpdatedAt' WHERE id = '$bookingId'",
        )

    private fun bookingUpdatedAt(): Timestamp =
        testDBConnection.createStatement().use { st ->
            st.executeQuery("SELECT updated_at FROM bookings WHERE id = '$bookingId'").use { rs ->
                assertTrue(rs.next(), "booking row should exist")
                rs.getTimestamp("updated_at")
            }
        }

    private fun assertBumpedPastPin() {
        val updatedAt = bookingUpdatedAt()
        assertTrue(
            updatedAt.after(Timestamp.valueOf("2020-01-01 00:00:00")),
            "booking updated_at should be bumped forward by the guest mutation, was $updatedAt",
        )
    }

    private suspend fun createGuest(): Uuid {
        val guest =
            repository.createGuest(
                bookingId = bookingId,
                propertyId = propertyId,
                firstName = "Juan",
                lastName = "Garcia",
            )
        assertNotNull(guest)
        return guest.id
    }

    @Test
    fun `createGuest bumps the parent booking updated_at`() =
        runBlocking {
            pinBookingUpdatedAt()
            createGuest()
            assertBumpedPastPin()
        }

    @Test
    fun `updateGuest bumps the parent booking updated_at`() =
        runBlocking {
            val guestId = createGuest()
            pinBookingUpdatedAt()
            repository.updateGuest(guestId, firstName = "Pedro")
            assertBumpedPastPin()
        }

    @Test
    fun `replaceGuest bumps the parent booking updated_at`() =
        runBlocking {
            val guestId = createGuest()
            pinBookingUpdatedAt()
            repository.replaceGuest(
                guestId = guestId,
                firstName = "Pedro",
                lastName = "Lopez",
                secondLastName = null,
                nationalityCountryId = null,
                dateOfBirth = null,
                sex = null,
                residenceCountryId = null,
                residenceProvinceId = null,
                residenceCityId = null,
                residenceCity = null,
                residenceAddress = null,
                residencePostalCode = null,
                documentType = null,
                documentNumber = null,
                documentSupportNumber = null,
                email = null,
                phone = null,
                phonePrefix = null,
                relationshipCode = null,
            )
            assertBumpedPastPin()
        }

    @Test
    fun `deleteGuest bumps the parent booking updated_at`() =
        runBlocking {
            val guestId = createGuest()
            pinBookingUpdatedAt()
            repository.deleteGuest(guestId)
            assertBumpedPastPin()
        }

    @Test
    fun `deleteGuestsByBooking bumps the parent booking updated_at`() =
        runBlocking {
            createGuest()
            pinBookingUpdatedAt()
            repository.deleteGuestsByBooking(bookingId)
            assertBumpedPastPin()
        }

    @Test
    fun `getAllGuests filters by channelId substring on the booking reference`() =
        runBlocking {
            // The fixture booking has channel_id 'ref1'.
            createGuest()
            val pageable = Pageable(page = 0, position = null, size = 100)

            val matching =
                repository.getAllGuests(GuestsFilters(channelId = "ref"), pageable)
            assertTrue(
                matching.content.any { it.bookingId == bookingId },
                "guest on the 'ref1' booking should match a 'ref' channelId filter",
            )

            val nonMatching =
                repository.getAllGuests(GuestsFilters(channelId = "no-such-ref"), pageable)
            assertTrue(
                nonMatching.content.none { it.bookingId == bookingId },
                "guest should be excluded by an unrelated channelId filter",
            )
        }

    @Test
    fun `getGuestsByAccount filters by channelId substring on the booking reference`() =
        runBlocking {
            // The fixture booking has channel_id 'ref1'.
            val guestId = createGuest()
            val pageable = Pageable(page = 0, position = null, size = 100)

            val matching =
                repository.getGuestsByAccount(
                    accountId = TEST_ACCOUNT_1_USER_1.accountId,
                    userId = TEST_ACCOUNT_1_USER_1.id,
                    filters = GuestsFilters(channelId = "ref"),
                    pageable = pageable,
                )
            assertTrue(
                matching.content.any { it.id == guestId },
                "guest on the 'ref1' booking should match a 'ref' channelId filter",
            )

            val nonMatching =
                repository.getGuestsByAccount(
                    accountId = TEST_ACCOUNT_1_USER_1.accountId,
                    userId = TEST_ACCOUNT_1_USER_1.id,
                    filters = GuestsFilters(channelId = "no-such-ref"),
                    pageable = pageable,
                )
            assertTrue(
                nonMatching.content.none { it.id == guestId },
                "guest should be excluded by an unrelated channelId filter",
            )
        }

    @Test
    fun `getGuestBookingContext returns channel, reference and owning account for each booking`() =
        runBlocking {
            // The fixture booking is 'direct' / 'ref1' on TEST_ACCOUNT_1_PROPERTY_1.
            val context = repository.getGuestBookingContext(listOf(bookingId))

            val entry = context[bookingId]
            assertNotNull(entry, "context should include the fixture booking")
            assertEquals(BookingChannel.Direct, entry.channel)
            assertEquals("ref1", entry.channelId)
            assertEquals(null, entry.wouldHostAgain)
            assertEquals(TEST_ACCOUNT_1_USER_1.accountId, entry.propertyAccountId)
        }

    @Test
    fun `getGuestBookingContext returns an empty map for no booking ids`() =
        runBlocking {
            assertTrue(repository.getGuestBookingContext(emptyList()).isEmpty())
        }
}
