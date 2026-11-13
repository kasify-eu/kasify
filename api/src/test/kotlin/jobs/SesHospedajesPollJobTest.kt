package eu.kasify.jobs

import eu.kasify.db.BookingsRepository
import eu.kasify.db.GuestRegistryRepository
import eu.kasify.db.IntegrationsRepository
import eu.kasify.db.PropertiesRepository
import eu.kasify.db.SesCommunicationsRepository
import eu.kasify.db.SesHospedajesSubmissionsRepository
import eu.kasify.db.model.BookingChannel
import eu.kasify.db.model.BookingStatus
import eu.kasify.db.model.SesCodigoEstado
import eu.kasify.model.Booking
import eu.kasify.model.Property
import eu.kasify.model.SesCommunication
import eu.kasify.model.SesCommunicationOperation
import eu.kasify.model.SesCommunicationStatus
import eu.kasify.model.SesCommunicationType
import eu.kasify.model.SesHospedajesIntegration
import eu.kasify.services.SesBookingRejectedNotifier
import eu.kasify.services.SesPropertyConfigGuard
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import kotlin.uuid.Uuid

class SesHospedajesPollJobTest {
    private val accountId = Uuid.parse("00000000-0000-0000-0000-0000000000a1")
    private val propertyId = Uuid.parse("00000000-0000-0000-0000-0000000000b1")
    private val bookingId = Uuid.parse("00000000-0000-0000-0000-0000000000c1")

    // Relaxed so tests that don't care about the property-config lifecycle stay quiet; the
    // dedicated tests below verify the calls explicitly.
    private val propertyConfigGuard: SesPropertyConfigGuard = mockk(relaxed = true)

    // Relaxed for the same reason; the estado-6 tests below verify the calls explicitly.
    private val bookingRejectedNotifier: SesBookingRejectedNotifier = mockk(relaxed = true)

    private fun loadTestResource(fileName: String): String =
        this::class.java.classLoader
            .getResourceAsStream("ses/$fileName")
            ?.readBytes()
            ?.decodeToString()
            ?: throw IllegalArgumentException("Resource not found: ses/$fileName")

    private fun buildComm(
        type: SesCommunicationType,
        operation: SesCommunicationOperation,
        lote: String,
    ): SesCommunication =
        SesCommunication(
            id = Uuid.random(),
            bookingId = bookingId,
            communicationType = type,
            operation = operation,
            cancelsId = null,
            status = SesCommunicationStatus.Submitted,
            lote = lote,
            codigoComunicacion = "codigo",
            estado = null,
            estadoDesc = null,
            submittedAt = null,
            lastAttemptAt = null,
            lastError = null,
            createdAt = OffsetDateTime.now(),
            updatedAt = OffsetDateTime.now(),
        )

    private fun buildBooking(): Booking =
        Booking(
            id = bookingId,
            propertyId = propertyId,
            propertyName = "Test Apartment",
            from = LocalDate.of(2026, 4, 20),
            to = LocalDate.of(2026, 4, 25),
            channel = BookingChannel.Direct,
            channelId = "REF-1",
            checkinCode = "AAAABBBBCCCCDDDD0000111122223333",
            status = BookingStatus.Confirmed,
            arrivalTime = LocalTime.of(15, 0),
            departureTime = LocalTime.of(11, 0),
            checkinTime = LocalTime.of(15, 0),
            checkoutTime = LocalTime.of(11, 0),
            createdAt = LocalDate.of(2026, 4, 10),
        )

    private fun buildProperty(): Property =
        Property(
            id = propertyId,
            accountId = accountId,
            name = "Test Apartment",
            address = "C/ Test 1",
            city = "Madrid",
            zipcode = "28001",
            countryId = eu.kasify.TEST_SPAIN_COUNTRY_ID,
            tzone = "Europe/Madrid",
            createdById = Uuid.parse("00000000-0000-0000-0000-0000000000d1"),
            defaultCleanerId = Uuid.parse("00000000-0000-0000-0000-0000000000d1"),
            sesEstablishmentCode = "E12345",
            // Strict model: the poll job groups comms by the property's assigned integration.
            sesIntegrationId = Uuid.parse("00000000-0000-0000-0000-0000000000f1"),
        )

    private fun buildIntegration(): SesHospedajesIntegration =
        SesHospedajesIntegration(
            id = Uuid.parse("00000000-0000-0000-0000-0000000000f1"),
            accountIntegrationId = Uuid.parse("00000000-0000-0000-0000-0000000000f2"),
            accountId = accountId,
            arrendadorCode = "0000145750",
            sesUsername = "ses-user",
            sesPassword = "ses-pass",
            enabled = true,
            rhDirectBookingsEnabled = true,
        )

    @Test
    fun `polls a submitted alta and anulación row, marking both terminal-success (operation-blind)`() =
        runBlocking {
            val altaComm = buildComm(SesCommunicationType.PV, SesCommunicationOperation.Alta, "LOTE-ALTA-1")
            val anulComm = buildComm(SesCommunicationType.RH, SesCommunicationOperation.Anulacion, "LOTE-ANUL-1")

            val comms: SesCommunicationsRepository = mockk(relaxUnitFun = true)
            coEvery { comms.listPendingPoll(any()) } returns listOf(altaComm, anulComm)

            val bookings: BookingsRepository = mockk()
            coEvery { bookings.getBooking(bookingId) } returns buildBooking()
            val properties: PropertiesRepository = mockk()
            coEvery { properties.getProperty(propertyId) } returns buildProperty()
            val integrations: IntegrationsRepository = mockk()
            coEvery { integrations.getSesHospedajesIntegrationById(accountId, any()) } returns buildIntegration()
            val submissions: SesHospedajesSubmissionsRepository = mockk()
            coEvery {
                submissions.recordQueryAttempt(any(), any(), any(), any(), any(), any(), any(), any(), any())
            } returns Uuid.random()
            val guestRegistry: GuestRegistryRepository = mockk()
            coEvery { guestRegistry.updateEstado(any(), any(), any(), any(), any()) } returns true

            val engine =
                MockEngine {
                    respond(
                        content = ByteReadChannel(loadTestResource("consult_lote_response_tramitado.xml")),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "text/xml"),
                    )
                }

            val job =
                SesHospedajesPollJob(
                    bookingsRepository = bookings,
                    propertiesRepository = properties,
                    integrationsRepository = integrations,
                    submissionsRepository = submissions,
                    communicationsRepository = comms,
                    guestRegistryRepository = guestRegistry,
                    httpClient = HttpClient(engine),
                    propertyConfigGuard = propertyConfigGuard,
                    bookingRejectedNotifier = bookingRejectedNotifier,
                )

            job.runOnce()

            // Both the alta and the anulación transition to terminal success — same code path.
            coVerify(exactly = 1) {
                comms.markPolled(altaComm.id, SesCodigoEstado.TramitadoSinErrores_1, any())
            }
            coVerify(exactly = 1) {
                comms.markPolled(anulComm.id, SesCodigoEstado.TramitadoSinErrores_1, any())
            }
            coVerify(exactly = 1) {
                submissions.recordQueryAttempt(
                    bookingId,
                    "LOTE-ALTA-1",
                    0,
                    any(),
                    SesCodigoEstado.TramitadoSinErrores_1,
                    any(),
                    any(),
                    null,
                    altaComm.id,
                )
            }
            // Only the alta mirrors its estado onto the libro-registro entry — never the anulación.
            coVerify(exactly = 1) {
                guestRegistry.updateEstado(bookingId, SesCommunicationType.PV, "LOTE-ALTA-1", SesCodigoEstado.TramitadoSinErrores_1, any())
            }
            coVerify(exactly = 1) { guestRegistry.updateEstado(any(), any(), any(), any(), any()) }
        }

    @Test
    fun `accepted poll captures the per-communication codigo so the alta is cancellable`() =
        runBlocking {
            val rhComm = buildComm(SesCommunicationType.RH, SesCommunicationOperation.Alta, "LOTE-RH-OK")

            val comms: SesCommunicationsRepository = mockk(relaxUnitFun = true)
            coEvery { comms.listPendingPoll(any()) } returns listOf(rhComm)

            val bookings: BookingsRepository = mockk()
            coEvery { bookings.getBooking(bookingId) } returns buildBooking()
            val properties: PropertiesRepository = mockk()
            coEvery { properties.getProperty(propertyId) } returns buildProperty()
            val integrations: IntegrationsRepository = mockk()
            coEvery { integrations.getSesHospedajesIntegrationById(accountId, any()) } returns buildIntegration()
            val submissions: SesHospedajesSubmissionsRepository = mockk()
            coEvery {
                submissions.recordQueryAttempt(any(), any(), any(), any(), any(), any(), any(), any(), any())
            } returns Uuid.random()
            val guestRegistry: GuestRegistryRepository = mockk()
            coEvery { guestRegistry.updateEstado(any(), any(), any(), any(), any()) } returns true

            val engine =
                MockEngine {
                    respond(
                        content = ByteReadChannel(loadTestResource("consult_lote_response_accepted_with_codigo.xml")),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "text/xml"),
                    )
                }

            val job =
                SesHospedajesPollJob(
                    bookingsRepository = bookings,
                    propertiesRepository = properties,
                    integrationsRepository = integrations,
                    submissionsRepository = submissions,
                    communicationsRepository = comms,
                    guestRegistryRepository = guestRegistry,
                    httpClient = HttpClient(engine),
                    propertyConfigGuard = propertyConfigGuard,
                    bookingRejectedNotifier = bookingRejectedNotifier,
                )

            job.runOnce()

            coVerify(exactly = 1) {
                comms.markPolled(
                    rhComm.id,
                    SesCodigoEstado.TramitadoSinErrores_1,
                    any(),
                    null,
                    "RH-CODIGO-9999",
                )
            }
            // The late-arriving código also reaches the libro-registro entry.
            coVerify(exactly = 1) {
                guestRegistry.updateEstado(
                    bookingId,
                    SesCommunicationType.RH,
                    "LOTE-RH-OK",
                    SesCodigoEstado.TramitadoSinErrores_1,
                    "RH-CODIGO-9999",
                )
            }
        }

    @Test
    fun `partial-error poll stores the per-communication error in last_error`() =
        runBlocking {
            val rhComm = buildComm(SesCommunicationType.RH, SesCommunicationOperation.Alta, "LOTE-RH-ERR")

            val comms: SesCommunicationsRepository = mockk(relaxUnitFun = true)
            coEvery { comms.listPendingPoll(any()) } returns listOf(rhComm)

            val bookings: BookingsRepository = mockk()
            coEvery { bookings.getBooking(bookingId) } returns buildBooking()
            val properties: PropertiesRepository = mockk()
            coEvery { properties.getProperty(propertyId) } returns buildProperty()
            val integrations: IntegrationsRepository = mockk()
            coEvery { integrations.getSesHospedajesIntegrationById(accountId, any()) } returns buildIntegration()
            val submissions: SesHospedajesSubmissionsRepository = mockk()
            coEvery {
                submissions.recordQueryAttempt(any(), any(), any(), any(), any(), any(), any(), any(), any())
            } returns Uuid.random()
            val guestRegistry: GuestRegistryRepository = mockk()
            coEvery { guestRegistry.updateEstado(any(), any(), any(), any(), any()) } returns true

            val engine =
                MockEngine {
                    respond(
                        content = ByteReadChannel(loadTestResource("consult_lote_response_partial_errors.xml")),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "text/xml"),
                    )
                }

            val job =
                SesHospedajesPollJob(
                    bookingsRepository = bookings,
                    propertiesRepository = properties,
                    integrationsRepository = integrations,
                    submissionsRepository = submissions,
                    communicationsRepository = comms,
                    guestRegistryRepository = guestRegistry,
                    httpClient = HttpClient(engine),
                    propertyConfigGuard = propertyConfigGuard,
                    bookingRejectedNotifier = bookingRejectedNotifier,
                )

            job.runOnce()

            val expectedError =
                "Error validación de datos: Error de validación en la comunicación [1]: " +
                    "[Datos de contacto obligatorios, teléfono o correo.]"
            // The per-communication validation error reaches the comm row's last_error...
            coVerify(exactly = 1) {
                comms.markPolled(rhComm.id, SesCodigoEstado.TramitadoConErroresParciales_6, any(), expectedError)
            }
            // ...and the wire-log query row.
            coVerify(exactly = 1) {
                submissions.recordQueryAttempt(
                    bookingId,
                    "LOTE-RH-ERR",
                    0,
                    any(),
                    SesCodigoEstado.TramitadoConErroresParciales_6,
                    any(),
                    any(),
                    expectedError,
                    rhComm.id,
                )
            }
            // A generic data-validation error is booking-scoped: the property flag is untouched
            // and the owners are told which booking to fix.
            coVerify(exactly = 0) { propertyConfigGuard.onConfigError(any(), any()) }
            coVerify(exactly = 1) {
                bookingRejectedNotifier.notifyOwners(
                    match { it.id == bookingId },
                    match { it.id == propertyId },
                    SesCommunicationType.RH,
                    expectedError,
                )
            }
        }

    @Test
    fun `unknown-establishment poll marks the comm failed (not rejected) and flags the property`() =
        runBlocking {
            val pvComm = buildComm(SesCommunicationType.PV, SesCommunicationOperation.Alta, "LOTE-PV-EST")

            val comms: SesCommunicationsRepository = mockk(relaxUnitFun = true)
            coEvery { comms.listPendingPoll(any()) } returns listOf(pvComm)

            val bookings: BookingsRepository = mockk()
            coEvery { bookings.getBooking(bookingId) } returns buildBooking()
            val properties: PropertiesRepository = mockk()
            coEvery { properties.getProperty(propertyId) } returns buildProperty()
            val integrations: IntegrationsRepository = mockk()
            coEvery { integrations.getSesHospedajesIntegrationById(accountId, any()) } returns buildIntegration()
            val submissions: SesHospedajesSubmissionsRepository = mockk()
            coEvery {
                submissions.recordQueryAttempt(any(), any(), any(), any(), any(), any(), any(), any(), any())
            } returns Uuid.random()
            val guestRegistry: GuestRegistryRepository = mockk()
            coEvery { guestRegistry.updateEstado(any(), any(), any(), any(), any()) } returns true

            val engine =
                MockEngine {
                    respond(
                        content = ByteReadChannel(loadTestResource("consult_lote_response_unknown_establishment.xml")),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "text/xml"),
                    )
                }

            val job =
                SesHospedajesPollJob(
                    bookingsRepository = bookings,
                    propertiesRepository = properties,
                    integrationsRepository = integrations,
                    submissionsRepository = submissions,
                    communicationsRepository = comms,
                    guestRegistryRepository = guestRegistry,
                    httpClient = HttpClient(engine),
                    propertyConfigGuard = propertyConfigGuard,
                    bookingRejectedNotifier = bookingRejectedNotifier,
                )

            job.runOnce()

            // Property-config failure: the comm lands `failed` (retry-eligible once the code is
            // fixed) instead of the estado-derived `rejected`...
            coVerify(exactly = 1) {
                comms.markPolled(
                    pvComm.id,
                    SesCodigoEstado.TramitadoConErroresParciales_6,
                    any(),
                    match { it != null && it.contains("No existe un establecimiento") },
                    null,
                    SesCommunicationStatus.Failed,
                )
            }
            // ...and the property is flagged (the guard handles first-writer-wins + the owner email).
            coVerify(exactly = 1) {
                propertyConfigGuard.onConfigError(
                    match { it.id == propertyId },
                    match { it != null && it.contains("No existe un establecimiento") },
                )
            }
            coVerify(exactly = 0) { propertyConfigGuard.onSesSuccess(any()) }
            // The establishment error already fires its own one-time owner email via the guard —
            // no per-booking rejection email on top.
            coVerify(exactly = 0) { bookingRejectedNotifier.notifyOwners(any(), any(), any(), any()) }
        }

    @Test
    fun `successful poll self-heals a stale property config flag`() =
        runBlocking {
            val altaComm = buildComm(SesCommunicationType.PV, SesCommunicationOperation.Alta, "LOTE-ALTA-1")

            val comms: SesCommunicationsRepository = mockk(relaxUnitFun = true)
            coEvery { comms.listPendingPoll(any()) } returns listOf(altaComm)

            val bookings: BookingsRepository = mockk()
            coEvery { bookings.getBooking(bookingId) } returns buildBooking()
            val properties: PropertiesRepository = mockk()
            coEvery { properties.getProperty(propertyId) } returns buildProperty()
            val integrations: IntegrationsRepository = mockk()
            coEvery { integrations.getSesHospedajesIntegrationById(accountId, any()) } returns buildIntegration()
            val submissions: SesHospedajesSubmissionsRepository = mockk()
            coEvery {
                submissions.recordQueryAttempt(any(), any(), any(), any(), any(), any(), any(), any(), any())
            } returns Uuid.random()
            val guestRegistry: GuestRegistryRepository = mockk()
            coEvery { guestRegistry.updateEstado(any(), any(), any(), any(), any()) } returns true

            val engine =
                MockEngine {
                    respond(
                        content = ByteReadChannel(loadTestResource("consult_lote_response_tramitado.xml")),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "text/xml"),
                    )
                }

            val job =
                SesHospedajesPollJob(
                    bookingsRepository = bookings,
                    propertiesRepository = properties,
                    integrationsRepository = integrations,
                    submissionsRepository = submissions,
                    communicationsRepository = comms,
                    guestRegistryRepository = guestRegistry,
                    httpClient = HttpClient(engine),
                    propertyConfigGuard = propertyConfigGuard,
                    bookingRejectedNotifier = bookingRejectedNotifier,
                )

            job.runOnce()

            // A successful estado proves the establishment code works: any stale flag is cleared.
            coVerify(atLeast = 1) { propertyConfigGuard.onSesSuccess(match { it.id == propertyId }) }
            coVerify(exactly = 0) { propertyConfigGuard.onConfigError(any(), any()) }
            coVerify(exactly = 0) { bookingRejectedNotifier.notifyOwners(any(), any(), any(), any()) }
        }
}
