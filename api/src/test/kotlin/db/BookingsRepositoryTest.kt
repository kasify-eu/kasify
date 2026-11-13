package eu.kasify.db

import eu.kasify.TEST_ACCOUNT_1_PROPERTY_1
import eu.kasify.extensions.TEST_ENCRYPTION_KEY
import eu.kasify.model.SesCommunicationOperation
import eu.kasify.model.SesCommunicationType
import eu.kasify.model.api.requests.BookingData
import eu.kasify.routes.BaseRouteDBTests
import eu.kasify.security.EncryptionService
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class BookingsRepositoryTest : BaseRouteDBTests() {
    private val repository = BookingsRepository()
    private val sesRepository = SesCommunicationsRepository()

    // Pin both sides of the data-change watermark so the comparison is deterministic and never races
    // the wall clock: bookings.updated_at vs the alta's ses_communication.last_attempt_at.
    private fun pinWatermark(
        bookingId: Uuid,
        bookingUpdatedAt: String,
        lastAttemptAt: String,
    ) {
        testDBConnection.executeQuery(
            "UPDATE bookings SET updated_at = '$bookingUpdatedAt' WHERE id = '$bookingId'",
        )
        testDBConnection.executeQuery(
            "UPDATE ses_communication SET last_attempt_at = '$lastAttemptAt' WHERE booking_id = '$bookingId'",
        )
    }

    // Fixture SES integration the test property is assigned to (strict model: discovery requires
    // an explicit property → integration assignment on top of the establishment code).
    private val sesIntegrationId = Uuid.parse("50000000-0000-0000-0000-0000000000b1")

    override fun setupData() {
        // The SES discovery queries require the property to carry an establishment code AND be
        // explicitly assigned to an enabled SES integration.
        testDBConnection.insertSesIntegration(TEST_ACCOUNT_1_PROPERTY_1.accountId, integrationId = sesIntegrationId)
        testDBConnection.assignPropertyToSesIntegration(TEST_ACCOUNT_1_PROPERTY_1.id, sesIntegrationId)
    }

    /** Inserts a booking + calendar event with the given check-in datetime. */
    private fun insertSesEligibleBooking(
        bookingId: Uuid,
        checkinAt: OffsetDateTime,
        checkinCompletedAt: OffsetDateTime? = OffsetDateTime.now(ZoneOffset.UTC).minusDays(1),
        status: String = "confirmed",
        cancelledAt: OffsetDateTime? = null,
        propertyId: Uuid = TEST_ACCOUNT_1_PROPERTY_1.id,
    ) {
        val eventId = Uuid.random()
        val checkoutAt = checkinAt.plusDays(3)
        testDBConnection.executeQuery(
            "INSERT INTO public.calendar_events (id, property_id, from_datetime, to_datetime, type, created_at, updated_at) " +
                "VALUES('$eventId', '$propertyId', '$checkinAt', '$checkoutAt', 'booking', now(), now())",
        )
        val checkinCompletedSql = checkinCompletedAt?.let { "'$it'" } ?: "NULL"
        val cancelledAtSql = cancelledAt?.let { "'$it'" } ?: "NULL"
        testDBConnection.executeQuery(
            "INSERT INTO public.bookings " +
                "(id, calendar_event_id, property_id, channel, channel_id, status, " +
                "checkin_completed_at, cancelled_at, created_at, updated_at) " +
                "VALUES ('$bookingId', '$eventId', '$propertyId', 'direct', " +
                "'ref-$bookingId', '$status', $checkinCompletedSql, $cancelledAtSql, now(), now())",
        )
    }

    @Test
    fun `createBooking generates a checkin code`() =
        runBlocking {
            val booking =
                repository.createBooking(
                    BookingData(
                        from = LocalDate.now().plusDays(30),
                        to = LocalDate.now().plusDays(33),
                    ),
                    TEST_ACCOUNT_1_PROPERTY_1.id,
                )

            // DB default: upper(replace(gen_random_uuid()::text, '-', '')) -> 32 hex chars.
            assertEquals(32, booking.checkinCode.length)
            assertTrue(booking.checkinCode.all { it.isDigit() || it in 'A'..'F' })
        }

    // --- getBookingsPendingPvAlta window (active stay .. check-out + 1 day) ---

    @Test
    fun `PV alta picks up an active booking`() =
        runBlocking {
            val id = Uuid.parse("40000000-0000-0000-0000-0000000c0001")
            // Checked in 7h ago, checkout ~3 days from now → booking active.
            insertSesEligibleBooking(id, checkinAt = OffsetDateTime.now(ZoneOffset.UTC).minusHours(7))

            val pending = repository.getBookingsPendingPvAlta()

            assertEquals(1, pending.size)
            assertEquals(id, pending.first().id)
        }

    @Test
    fun `PV alta picks up a booking that checked out within the last day`() =
        runBlocking {
            val id = Uuid.parse("40000000-0000-0000-0000-0000000c0002")
            // checkin ~3 days + 2h ago → checkout ~2h ago (within check-out + 1 day).
            insertSesEligibleBooking(id, checkinAt = OffsetDateTime.now(ZoneOffset.UTC).minusDays(3).minusHours(2))

            val pending = repository.getBookingsPendingPvAlta()

            assertEquals(1, pending.size)
            assertEquals(id, pending.first().id)
        }

    @Test
    fun `PV alta does not pick up a booking that ended more than a day ago`() =
        runBlocking {
            // checkin 5 days ago → checkout 2 days ago → past the PV window.
            insertSesEligibleBooking(
                Uuid.parse("40000000-0000-0000-0000-0000000c0003"),
                checkinAt = OffsetDateTime.now(ZoneOffset.UTC).minusDays(5),
            )

            assertTrue(repository.getBookingsPendingPvAlta().isEmpty())
        }

    // --- getBookingsPendingRhAlta window (.. day of check-in, inclusive) ---

    @Test
    fun `RH alta picks up a future booking`() =
        runBlocking {
            val id = Uuid.parse("40000000-0000-0000-0000-0000000d0001")
            insertSesEligibleBooking(
                id,
                checkinAt = OffsetDateTime.now(ZoneOffset.UTC).plusDays(2),
                checkinCompletedAt = null,
            )

            val pending = repository.getBookingsPendingRhAlta()

            assertEquals(1, pending.size)
            assertEquals(id, pending.first().id)
        }

    @Test
    fun `RH alta picks up a booking whose start day is today even if the hour passed`() =
        runBlocking {
            val id = Uuid.parse("40000000-0000-0000-0000-0000000d0002")
            // Start day is today (inclusive), even if the check-in hour already passed.
            insertSesEligibleBooking(
                id,
                checkinAt = OffsetDateTime.now(ZoneOffset.UTC).minusHours(2),
                checkinCompletedAt = null,
            )

            val pending = repository.getBookingsPendingRhAlta()

            assertEquals(1, pending.size)
            assertEquals(id, pending.first().id)
        }

    @Test
    fun `RH alta does not pick up a booking that already started`() =
        runBlocking {
            insertSesEligibleBooking(
                Uuid.parse("40000000-0000-0000-0000-0000000d0003"),
                checkinAt = OffsetDateTime.now(ZoneOffset.UTC).minusDays(1),
                checkinCompletedAt = null,
            )

            assertTrue(repository.getBookingsPendingRhAlta().isEmpty())
        }

    // --- data-change gate: a rejected alta is not auto-retried until the data changes ---

    @Test
    fun `PV alta does not re-pick a rejected booking whose data is unchanged`() =
        runBlocking {
            val id = Uuid.parse("40000000-0000-0000-0000-0000000e0001")
            insertSesEligibleBooking(id, checkinAt = OffsetDateTime.now(ZoneOffset.UTC).minusHours(7))
            sesRepository.upsertRejected(id, SesCommunicationType.PV, SesCommunicationOperation.Alta, "validation error")
            // Last attempt is after the booking's last change → data unchanged since the rejection.
            pinWatermark(id, bookingUpdatedAt = "2026-06-01 10:00:00+00", lastAttemptAt = "2026-06-01 12:00:00+00")

            assertTrue(repository.getBookingsPendingPvAlta().isEmpty())
        }

    @Test
    fun `PV alta re-picks a rejected booking once its data changes`() =
        runBlocking {
            val id = Uuid.parse("40000000-0000-0000-0000-0000000e0002")
            insertSesEligibleBooking(id, checkinAt = OffsetDateTime.now(ZoneOffset.UTC).minusHours(7))
            sesRepository.upsertRejected(id, SesCommunicationType.PV, SesCommunicationOperation.Alta, "validation error")
            // Booking edited (e.g. a guest fixed) after the rejection's last attempt → eligible again.
            pinWatermark(id, bookingUpdatedAt = "2026-06-01 14:00:00+00", lastAttemptAt = "2026-06-01 12:00:00+00")

            val pending = repository.getBookingsPendingPvAlta()
            assertEquals(1, pending.size)
            assertEquals(id, pending.first().id)
        }

    @Test
    fun `PV alta still retries a failed booking regardless of the data-change watermark`() =
        runBlocking {
            val id = Uuid.parse("40000000-0000-0000-0000-0000000e0003")
            insertSesEligibleBooking(id, checkinAt = OffsetDateTime.now(ZoneOffset.UTC).minusHours(7))
            // A transient transport failure (not a SES rejection) must keep retrying every tick.
            sesRepository.upsertFailed(id, SesCommunicationType.PV, SesCommunicationOperation.Alta, "ConnectException")
            pinWatermark(id, bookingUpdatedAt = "2026-06-01 10:00:00+00", lastAttemptAt = "2026-06-01 12:00:00+00")

            val pending = repository.getBookingsPendingPvAlta()
            assertEquals(1, pending.size)
            assertEquals(id, pending.first().id)
        }

    @Test
    fun `RH alta does not re-pick a rejected booking whose data is unchanged`() =
        runBlocking {
            val id = Uuid.parse("40000000-0000-0000-0000-0000000e0004")
            insertSesEligibleBooking(
                id,
                checkinAt = OffsetDateTime.now(ZoneOffset.UTC).plusDays(2),
                checkinCompletedAt = null,
            )
            sesRepository.upsertRejected(id, SesCommunicationType.RH, SesCommunicationOperation.Alta, "validation error")
            pinWatermark(id, bookingUpdatedAt = "2026-06-01 10:00:00+00", lastAttemptAt = "2026-06-01 12:00:00+00")

            assertTrue(repository.getBookingsPendingRhAlta().isEmpty())
        }

    // --- failed altas bypass the discovery windows (outage recovery) ---

    @Test
    fun `PV alta re-picks a failed booking whose window expired during an outage`() =
        runBlocking {
            val id = Uuid.parse("40000000-0000-0000-0000-0000000f0003")
            // checkin 5 days ago → checkout 2 days ago → outside the PV window (see the
            // 'ended more than a day ago' test). A failed attempt means the filing is still owed,
            // so the booking must stay eligible after the window closes.
            insertSesEligibleBooking(id, checkinAt = OffsetDateTime.now(ZoneOffset.UTC).minusDays(5))
            sesRepository.upsertFailed(id, SesCommunicationType.PV, SesCommunicationOperation.Alta, "SES credentials error: code=10107")

            val pending = repository.getBookingsPendingPvAlta()
            assertEquals(listOf(id), pending.map { it.id })
        }

    @Test
    fun `RH alta re-picks a failed booking after the check-in day has passed`() =
        runBlocking {
            val id = Uuid.parse("40000000-0000-0000-0000-0000000f0004")
            // check-in 2 days ago → past the RH window; the failed attempt keeps it eligible.
            insertSesEligibleBooking(
                id,
                checkinAt = OffsetDateTime.now(ZoneOffset.UTC).minusDays(2),
                checkinCompletedAt = null,
            )
            sesRepository.upsertFailed(id, SesCommunicationType.RH, SesCommunicationOperation.Alta, "SES credentials error: code=10107")

            val pending = repository.getBookingsPendingRhAlta()
            assertEquals(listOf(id), pending.map { it.id })
        }

    // --- strict SES participation gate (assignment + enabled + unflagged integration) ---

    @Test
    fun `discovery skips a configured property with no assigned integration`() =
        runBlocking {
            val id = Uuid.parse("40000000-0000-0000-0000-0000000f0001")
            insertSesEligibleBooking(id, checkinAt = OffsetDateTime.now(ZoneOffset.UTC).minusHours(7))
            // Establishment code alone is not enough: without the explicit assignment the
            // property never enters discovery (strict model, no account-level fallback).
            testDBConnection.executeQuery(
                "UPDATE properties SET ses_integration_id = NULL WHERE id = '${TEST_ACCOUNT_1_PROPERTY_1.id}'",
            )

            assertTrue(repository.getBookingsPendingPvAlta().isEmpty())
            assertTrue(repository.getBookingsPendingRhAlta().isEmpty())
        }

    @Test
    fun `a credential error pauses only the flagged integration's properties`() =
        runBlocking {
            // Property 1 (setupData) is assigned to the fixture integration; property 2 belongs to
            // the same account but is assigned to its own, healthy integration.
            val property2 =
                testDBConnection.insertProperty(
                    Uuid.parse("50000000-0000-0000-0000-0000000000c1"),
                    TEST_ACCOUNT_1_PROPERTY_1.accountId,
                    "Sibling property",
                )
            val siblingIntegrationId = testDBConnection.insertSesIntegration(TEST_ACCOUNT_1_PROPERTY_1.accountId)
            testDBConnection.assignPropertyToSesIntegration(property2.id, siblingIntegrationId, establishmentCode = "EST-002")

            val pv1 = Uuid.parse("40000000-0000-0000-0000-0000000f0002")
            val pv2 = Uuid.parse("40000000-0000-0000-0000-0000000f0003")
            insertSesEligibleBooking(pv1, checkinAt = OffsetDateTime.now(ZoneOffset.UTC).minusHours(7))
            insertSesEligibleBooking(
                pv2,
                checkinAt = OffsetDateTime.now(ZoneOffset.UTC).minusHours(7),
                propertyId = property2.id,
            )

            // Both healthy: both discovered.
            assertEquals(setOf(pv1, pv2), repository.getBookingsPendingPvAlta().map { it.id }.toSet())

            // Flag integration 1: only ITS property pauses; the sibling keeps filing.
            val integrationsRepository =
                IntegrationsRepository(PropertiesRepository(), EncryptionService(key = TEST_ENCRYPTION_KEY))
            integrationsRepository.markSesCredentialsError(sesIntegrationId, 10107, "Usuario incorrecto")
            assertEquals(listOf(pv2), repository.getBookingsPendingPvAlta().map { it.id })

            // Flag cleared (credentials fixed): discovery resumes without any booking change.
            integrationsRepository.clearSesCredentialsError(sesIntegrationId)
            assertEquals(setOf(pv1, pv2), repository.getBookingsPendingPvAlta().map { it.id }.toSet())
        }

    @Test
    fun `a property config error pauses only the flagged property`() =
        runBlocking {
            // Property 2 sits on the SAME integration as property 1 (setupData): the property-level
            // flag must pause property 1 alone, not the whole integration.
            val property2 =
                testDBConnection.insertProperty(
                    Uuid.parse("50000000-0000-0000-0000-0000000000c2"),
                    TEST_ACCOUNT_1_PROPERTY_1.accountId,
                    "Sibling on same integration",
                )
            testDBConnection.assignPropertyToSesIntegration(property2.id, sesIntegrationId, establishmentCode = "EST-002")

            val pv1 = Uuid.parse("40000000-0000-0000-0000-0000000f0005")
            val pv2 = Uuid.parse("40000000-0000-0000-0000-0000000f0006")
            insertSesEligibleBooking(pv1, checkinAt = OffsetDateTime.now(ZoneOffset.UTC).minusHours(7))
            insertSesEligibleBooking(
                pv2,
                checkinAt = OffsetDateTime.now(ZoneOffset.UTC).minusHours(7),
                propertyId = property2.id,
            )

            // Both healthy: both discovered.
            assertEquals(setOf(pv1, pv2), repository.getBookingsPendingPvAlta().map { it.id }.toSet())

            // Flag property 1's SES config: only ITS bookings pause; the sibling property on the
            // same integration keeps filing.
            val propertiesRepository = PropertiesRepository()
            propertiesRepository.markSesConfigError(TEST_ACCOUNT_1_PROPERTY_1.id, "No existe un establecimiento para ese código")
            assertEquals(listOf(pv2), repository.getBookingsPendingPvAlta().map { it.id })

            // Flag cleared (code fixed): discovery resumes without any booking change.
            propertiesRepository.clearSesConfigError(TEST_ACCOUNT_1_PROPERTY_1.id)
            assertEquals(setOf(pv1, pv2), repository.getBookingsPendingPvAlta().map { it.id }.toSet())
        }

    @Test
    fun `discovery skips properties of a disabled integration`() =
        runBlocking {
            val id = Uuid.parse("40000000-0000-0000-0000-0000000f0004")
            insertSesEligibleBooking(id, checkinAt = OffsetDateTime.now(ZoneOffset.UTC).minusHours(7))

            assertEquals(listOf(id), repository.getBookingsPendingPvAlta().map { it.id })
            testDBConnection.executeQuery(
                "UPDATE account_ses_hospedajes_integrations SET enabled = false WHERE id = '$sesIntegrationId'",
            )
            assertTrue(repository.getBookingsPendingPvAlta().isEmpty())
        }
}
