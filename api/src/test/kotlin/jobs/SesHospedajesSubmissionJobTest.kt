package eu.kasify.jobs

import eu.kasify.TEST_ACCOUNT_1_PROPERTY_1
import eu.kasify.db.BookingsRepository
import eu.kasify.db.SesCommunicationsRepository
import eu.kasify.db.model.BookingChannel
import eu.kasify.db.model.BookingStatus
import eu.kasify.model.Booking
import eu.kasify.model.SesCommunication
import eu.kasify.model.SesCommunicationOperation
import eu.kasify.model.SesCommunicationStatus
import eu.kasify.model.SesCommunicationType
import eu.kasify.services.SesHospedajesService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import kotlin.uuid.Uuid

/**
 * Job-level test. Per-booking orchestration (eligibility, audit row writing, SES rejection vs
 * upstream-exception branching, etc.) lives in [SesHospedajesService] and is covered by its own
 * tests. Here we only verify the job drives the three work sets (PV altas, RH altas behind the
 * env gate, anulaciones) and delegates.
 */
class SesHospedajesSubmissionJobTest {
    private val propertyId = TEST_ACCOUNT_1_PROPERTY_1.id

    private fun buildBooking(id: Uuid): Booking =
        Booking(
            id = id,
            propertyId = propertyId,
            propertyName = "Test Apartment",
            from = LocalDate.of(2026, 4, 20),
            to = LocalDate.of(2026, 4, 25),
            channel = BookingChannel.Direct,
            channelId = "REF-${id.toString().take(6)}",
            checkinCode = "AAAABBBBCCCCDDDD0000111122223333",
            status = BookingStatus.Confirmed,
            adultsCount = 1,
            childrenCount = 0,
            infantsCount = 0,
            arrivalTime = LocalTime.of(15, 0),
            departureTime = LocalTime.of(11, 0),
            checkinTime = LocalTime.of(15, 0),
            checkoutTime = LocalTime.of(11, 0),
            createdAt = LocalDate.of(2026, 4, 10),
        )

    private fun acceptedAltaComm(
        bookingId: Uuid,
        type: SesCommunicationType,
    ): SesCommunication =
        SesCommunication(
            id = Uuid.random(),
            bookingId = bookingId,
            communicationType = type,
            operation = SesCommunicationOperation.Alta,
            cancelsId = null,
            status = SesCommunicationStatus.Accepted,
            lote = "lote",
            codigoComunicacion = "codigo",
            estado = null,
            estadoDesc = null,
            submittedAt = null,
            lastAttemptAt = null,
            lastError = null,
            createdAt = OffsetDateTime.now(),
            updatedAt = OffsetDateTime.now(),
        )

    private fun submitted() = SesHospedajesService.ManualSesSubmitResult.Submitted(lote = "lote-x", submissionId = Uuid.random())

    private fun buildJob(
        bookings: BookingsRepository,
        comms: SesCommunicationsRepository,
        sesService: SesHospedajesService,
    ) = SesHospedajesSubmissionJob(
        bookingsRepository = bookings,
        communicationsRepository = comms,
        sesService = sesService,
    )

    @Test
    fun `runs nothing when no work is pending`() =
        runBlocking {
            val bookings: BookingsRepository = mockk()
            val comms: SesCommunicationsRepository = mockk()
            val sesService: SesHospedajesService = mockk()
            coEvery { bookings.getBookingsPendingPvAlta() } returns emptyList()
            coEvery { bookings.getBookingsPendingRhAlta() } returns emptyList()
            coEvery { comms.acceptedAltasForCancelledBookingsWithoutAnulacion(any()) } returns emptyList()

            buildJob(bookings, comms, sesService).runOnce()

            coVerify(exactly = 0) { sesService.submitAlta(any(), any(), any()) }
            coVerify(exactly = 0) { sesService.submitCancellation(any(), any()) }
        }

    @Test
    fun `submits a PV alta for each PV candidate`() =
        runBlocking {
            val bookings: BookingsRepository = mockk()
            val comms: SesCommunicationsRepository = mockk()
            val sesService: SesHospedajesService = mockk()
            val a = buildBooking(Uuid.parse("40000000-0000-0000-0000-0000000000a1"))
            val b = buildBooking(Uuid.parse("40000000-0000-0000-0000-0000000000a2"))
            coEvery { bookings.getBookingsPendingPvAlta() } returns listOf(a, b)
            coEvery { bookings.getBookingsPendingRhAlta() } returns emptyList()
            coEvery { comms.acceptedAltasForCancelledBookingsWithoutAnulacion(any()) } returns emptyList()
            coEvery { sesService.submitAlta(any(), SesCommunicationType.PV, any()) } returns submitted()

            buildJob(bookings, comms, sesService).runOnce()

            coVerify(exactly = 1) { sesService.submitAlta(a.id, SesCommunicationType.PV, honorScopedPauses = true) }
            coVerify(exactly = 1) { sesService.submitAlta(b.id, SesCommunicationType.PV, honorScopedPauses = true) }
        }

    @Test
    fun `with RH gate off, RH altas are skipped but a discovered anulación is still sent`() =
        runBlocking {
            // The gate keys off SES_RH_AUTO_SUBMIT_ENABLED; in the test env it is unset → off.
            val bookings: BookingsRepository = mockk()
            val comms: SesCommunicationsRepository = mockk()
            val sesService: SesHospedajesService = mockk()
            val rhCandidate = buildBooking(Uuid.parse("40000000-0000-0000-0000-0000000000c1"))
            val cancelledBookingId = Uuid.parse("40000000-0000-0000-0000-0000000000c2")
            val discoveredAlta = acceptedAltaComm(cancelledBookingId, SesCommunicationType.RH)
            coEvery { bookings.getBookingsPendingPvAlta() } returns emptyList()
            coEvery { bookings.getBookingsPendingRhAlta() } returns listOf(rhCandidate)
            coEvery { comms.acceptedAltasForCancelledBookingsWithoutAnulacion(any()) } returns listOf(discoveredAlta)
            coEvery { sesService.submitCancellation(any(), any()) } returns submitted()

            buildJob(bookings, comms, sesService).runOnce()

            // RH alta NOT auto-submitted (gate off)...
            coVerify(exactly = 0) { sesService.submitAlta(any(), SesCommunicationType.RH, any()) }
            // ...but the RH anulación of the discovered alta still fires, by communication id.
            coVerify(exactly = 1) { sesService.submitCancellation(discoveredAlta.id, honorScopedPauses = true) }
        }

    @Test
    fun `continues processing remaining items when one throws`() =
        runBlocking {
            val bookings: BookingsRepository = mockk()
            val comms: SesCommunicationsRepository = mockk()
            val sesService: SesHospedajesService = mockk()
            val a = buildBooking(Uuid.parse("40000000-0000-0000-0000-0000000000b1"))
            val b = buildBooking(Uuid.parse("40000000-0000-0000-0000-0000000000b2"))
            coEvery { bookings.getBookingsPendingPvAlta() } returns listOf(a, b)
            coEvery { bookings.getBookingsPendingRhAlta() } returns emptyList()
            coEvery { comms.acceptedAltasForCancelledBookingsWithoutAnulacion(any()) } returns emptyList()
            coEvery { sesService.submitAlta(a.id, SesCommunicationType.PV, any()) } throws RuntimeException("boom")
            coEvery { sesService.submitAlta(b.id, SesCommunicationType.PV, any()) } returns submitted()

            buildJob(bookings, comms, sesService).runOnce()

            coVerify(exactly = 1) { sesService.submitAlta(a.id, SesCommunicationType.PV, honorScopedPauses = true) }
            coVerify(exactly = 1) { sesService.submitAlta(b.id, SesCommunicationType.PV, honorScopedPauses = true) }
        }
}
