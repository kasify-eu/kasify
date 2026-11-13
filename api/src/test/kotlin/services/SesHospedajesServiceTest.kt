package eu.kasify.services

import eu.kasify.db.BookingsRepository
import eu.kasify.db.GeoRepository
import eu.kasify.db.GuestRegistryRepository
import eu.kasify.db.GuestsRepository
import eu.kasify.db.IntegrationsRepository
import eu.kasify.db.NewGuestRegistryEntry
import eu.kasify.db.PropertiesRepository
import eu.kasify.db.SesCommunicationsRepository
import eu.kasify.db.SesHospedajesSubmissionsRepository
import eu.kasify.db.model.BookingChannel
import eu.kasify.db.model.BookingStatus
import eu.kasify.db.model.DocumentType
import eu.kasify.db.model.Gender
import eu.kasify.db.model.SesCodigoEstado
import eu.kasify.model.Booking
import eu.kasify.model.Guest
import eu.kasify.model.Property
import eu.kasify.model.SesCommunication
import eu.kasify.model.SesCommunicationOperation
import eu.kasify.model.SesCommunicationStatus
import eu.kasify.model.SesCommunicationType
import eu.kasify.model.SesHospedajesIntegration
import eu.kasify.routes.exceptions.ConflictException
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.ByteArrayInputStream
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.Base64
import java.util.zip.ZipInputStream
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class SesHospedajesServiceTest {
    private val accountId = Uuid.parse("00000000-0000-0000-0000-0000000000a1")
    private val propertyId = Uuid.parse("00000000-0000-0000-0000-0000000000b1")
    private val bookingId = Uuid.parse("00000000-0000-0000-0000-0000000000c1")
    private val createdById = Uuid.parse("00000000-0000-0000-0000-0000000000d1")

    private fun loadTestResource(fileName: String): String =
        this::class.java.classLoader
            .getResourceAsStream("ses/$fileName")
            ?.readBytes()
            ?.decodeToString()
            ?: throw IllegalArgumentException("Resource not found: ses/$fileName")

    private val integrationId = Uuid.parse("00000000-0000-0000-0000-0000000000f1")

    private fun buildProperty(
        sesEstablishmentCode: String? = "E12345",
        sesIntegrationId: Uuid? = integrationId,
    ): Property =
        Property(
            id = propertyId,
            accountId = accountId,
            sesIntegrationId = sesIntegrationId,
            name = "Test Apartment",
            address = "C/ Test 1",
            city = "Madrid",
            zipcode = "28001",
            countryId = eu.kasify.TEST_SPAIN_COUNTRY_ID,
            tzone = "Europe/Madrid",
            createdById = createdById,
            defaultCleanerId = createdById,
            sesEstablishmentCode = sesEstablishmentCode,
            hasInternet = true,
            roomsCount = 1,
        )

    private fun buildBooking(
        checkinCompletedAt: OffsetDateTime? = OffsetDateTime.of(2026, 4, 15, 10, 0, 0, 0, ZoneOffset.UTC),
        channel: BookingChannel = BookingChannel.Airbnb,
        guestFirstName: String? = "Juan",
        guestLastName: String? = "Garcia",
        guestEmail: String? = "guest@example.com",
        guestPhone: String? = null,
    ): Booking =
        Booking(
            id = bookingId,
            propertyId = propertyId,
            propertyName = "Test Apartment",
            from = LocalDate.of(2026, 4, 20),
            to = LocalDate.of(2026, 4, 25),
            channel = channel,
            channelId = "AIRBNB-001",
            checkinCode = "AAAABBBBCCCCDDDD0000111122223333",
            status = BookingStatus.Confirmed,
            guestFirstName = guestFirstName,
            guestLastName = guestLastName,
            guestEmail = guestEmail,
            guestPhone = guestPhone,
            adultsCount = 1,
            childrenCount = 0,
            infantsCount = 0,
            checkinCompletedAt = checkinCompletedAt,
            arrivalTime = LocalTime.of(15, 0),
            departureTime = LocalTime.of(11, 0),
            checkinTime = LocalTime.of(15, 0),
            checkoutTime = LocalTime.of(11, 0),
            createdAt = LocalDate.of(2026, 4, 10),
        )

    private fun buildGuest(nationalityCountryId: Uuid? = null): Guest =
        Guest(
            id = Uuid.parse("00000000-0000-0000-0000-0000000000e1"),
            bookingId = bookingId,
            propertyId = propertyId,
            firstName = "Juan",
            lastName = "Garcia",
            nationalityCountryId = nationalityCountryId,
            dateOfBirth = LocalDate.of(1990, 1, 1),
            sex = Gender.Male,
            residenceAddress = "C/ Serrano 1",
            residencePostalCode = "28001",
            documentType = DocumentType.DNI,
            documentNumber = "12345678Z",
        )

    private fun buildIntegration(
        enabled: Boolean = true,
        username: String = "ses-user",
        password: String = "ses-pass",
        rhDirectBookingsEnabled: Boolean = true,
    ): SesHospedajesIntegration =
        SesHospedajesIntegration(
            id = Uuid.parse("00000000-0000-0000-0000-0000000000f1"),
            accountIntegrationId = Uuid.parse("00000000-0000-0000-0000-0000000000f2"),
            accountId = accountId,
            arrendadorCode = "0000145750",
            sesUsername = username,
            sesPassword = password,
            enabled = enabled,
            rhDirectBookingsEnabled = rhDirectBookingsEnabled,
        )

    private fun successEngine(capture: (body: String) -> Unit = {}): MockEngine =
        MockEngine { request ->
            capture(String(request.body.toByteArray()))
            respond(
                content = ByteReadChannel(loadTestResource("communication_pv_response_success.xml")),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "text/xml"),
            )
        }

    // =====================================================================================
    // submitAlta / submitCancellation (ses_communication-backed)
    // =====================================================================================

    private fun buildAltaComm(
        type: SesCommunicationType,
        status: SesCommunicationStatus,
        codigo: String? = "CODIGO-1",
        id: Uuid = Uuid.parse("00000000-0000-0000-0000-00000000a101"),
    ): SesCommunication =
        SesCommunication(
            id = id,
            bookingId = bookingId,
            communicationType = type,
            operation = SesCommunicationOperation.Alta,
            cancelsId = null,
            status = status,
            lote = "LOTE-1",
            codigoComunicacion = codigo,
            estado = null,
            estadoDesc = null,
            submittedAt = null,
            lastAttemptAt = null,
            lastError = null,
            createdAt = OffsetDateTime.now(),
            updatedAt = OffsetDateTime.now(),
        )

    private fun buildCommService(
        booking: Booking,
        integrations: List<SesHospedajesIntegration> = listOf(buildIntegration()),
        mockEngine: MockEngine = successEngine(),
        existingAlta: SesCommunication? = null,
        existingAnulacion: SesCommunication? = null,
        comms: SesCommunicationsRepository = mockk(relaxed = true),
        guestRegistry: GuestRegistryRepository = mockk(relaxUnitFun = true),
        credentialsGuard: SesCredentialsGuard = mockk(relaxUnitFun = true),
        bookingRejectedNotifier: SesBookingRejectedNotifier = mockk(relaxUnitFun = true),
    ): Pair<SesHospedajesService, SesCommunicationsRepository> {
        val bookings: BookingsRepository = mockk(relaxUnitFun = true)
        val properties: PropertiesRepository = mockk()
        val guestsRepo: GuestsRepository = mockk()
        val submissions: SesHospedajesSubmissionsRepository = mockk()
        coEvery { bookings.getBooking(bookingId) } returns booking
        coEvery { properties.getProperty(propertyId) } returns buildProperty()
        coEvery { guestsRepo.getAllGuestsByBooking(bookingId) } returns listOf(buildGuest())
        coEvery {
            submissions.recordSubmitAttempt(any(), any(), any(), any(), any(), any(), any(), any())
        } returns Uuid.parse("00000000-0000-0000-0000-0000000000ff")
        coEvery { guestRegistry.record(any()) } returns Uuid.parse("00000000-0000-0000-0000-0000000000fd")
        coEvery { guestRegistry.updateEstado(any(), any(), any(), any(), any()) } returns true
        coEvery {
            submissions.recordQueryAttempt(any(), any(), any(), any(), any(), any(), any(), any(), any())
        } returns Uuid.parse("00000000-0000-0000-0000-0000000000fe")
        coEvery { comms.getByKey(bookingId, any(), SesCommunicationOperation.Alta) } returns existingAlta
        coEvery { comms.getByKey(bookingId, any(), SesCommunicationOperation.Anulacion) } returns existingAnulacion
        // submitCancellation/pollCommunication look the row up by id.
        coEvery { comms.getById(any()) } returns existingAlta

        val integrationsRepository = mockk<IntegrationsRepository>()
        // Strict model: the service resolves the property's ASSIGNED integration by id.
        coEvery { integrationsRepository.getSesHospedajesIntegrationById(accountId, any()) } returns
            integrations.firstOrNull()
        val geoRepository =
            mockk<GeoRepository>().apply {
                coEvery { getCountryById(any()) } returns null
                coEvery { getCityById(any()) } returns null
            }
        val service =
            SesHospedajesService(
                integrationsRepository = integrationsRepository,
                geoRepository = geoRepository,
                bookingsRepository = bookings,
                propertiesRepository = properties,
                guestsRepository = guestsRepo,
                submissionsRepository = submissions,
                communicationsRepository = comms,
                guestRegistryRepository = guestRegistry,
                httpClient = HttpClient(mockEngine),
                credentialsGuard = credentialsGuard,
                bookingRejectedNotifier = bookingRejectedNotifier,
            )
        return service to comms
    }

    @Test
    fun `submitAlta PV accepted persists upsertSubmitted with lote and codigo`() =
        runTest {
            // The partial fixture carries codigo=0, a lote, and exactly one codigoComunicacion.
            val engine =
                MockEngine {
                    respond(
                        content = ByteReadChannel(loadTestResource("communication_pv_response_partial.xml")),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "text/xml"),
                    )
                }
            val (service, comms) = buildCommService(buildBooking(), mockEngine = engine)

            val result = service.submitAlta(bookingId, SesCommunicationType.PV)

            assertTrue(result is SesHospedajesService.ManualSesSubmitResult.Submitted)
            coVerify(exactly = 1) {
                comms.upsertSubmitted(
                    bookingId,
                    SesCommunicationType.PV,
                    SesCommunicationOperation.Alta,
                    "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
                    "comm-uuid-001",
                    null,
                )
            }
        }

    @Test
    fun `submitAlta PV accepted writes a libro-registro entry with the frozen parte`() =
        runTest {
            val engine =
                MockEngine {
                    respond(
                        content = ByteReadChannel(loadTestResource("communication_pv_response_partial.xml")),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "text/xml"),
                    )
                }
            val guestRegistry: GuestRegistryRepository = mockk()
            val (service, _) = buildCommService(buildBooking(), mockEngine = engine, guestRegistry = guestRegistry)
            // Re-stub after buildCommService so the capturing matcher is the one in effect.
            val entrySlot = slot<NewGuestRegistryEntry>()
            coEvery { guestRegistry.record(capture(entrySlot)) } returns Uuid.random()

            service.submitAlta(bookingId, SesCommunicationType.PV)

            coVerify(exactly = 1) { guestRegistry.record(any()) }
            val entry = entrySlot.captured
            assertEquals(accountId, entry.accountId)
            assertEquals(propertyId, entry.propertyId)
            assertEquals(bookingId, entry.bookingId)
            assertEquals(SesCommunicationType.PV, entry.communicationType)
            assertEquals("0000145750", entry.sesArrendadorCode)
            assertEquals("E12345", entry.sesEstablishmentCode)
            assertEquals("Test Apartment", entry.propertyName)
            assertEquals("AIRBNB-001", entry.contractReference)
            assertEquals(LocalDate.of(2026, 4, 20), entry.checkinDate)
            assertEquals(LocalDate.of(2026, 4, 25), entry.checkoutDate)
            assertEquals(1, entry.numGuests)
            assertEquals("a1b2c3d4-e5f6-7890-abcd-ef1234567890", entry.sesLote)
            assertEquals("comm-uuid-001", entry.codigoComunicacion)
            // The exact solicitud XML SES received, plus the frozen guest data.
            assertTrue(entry.requestXml.contains("http://www.neg.hospedajes.mir.es/altaParteHospedaje"))
            assertTrue(entry.requestXml.contains("<nombre>Juan</nombre>"))
            assertEquals(
                "Juan",
                entry.snapshot.guests
                    .first()
                    .nombre,
            )
            assertEquals(
                "12345678Z",
                entry.snapshot.guests
                    .first()
                    .numeroDocumento,
            )
        }

    @Test
    fun `submitAlta on SES rejection writes no libro-registro entry`() =
        runTest {
            val engine =
                MockEngine {
                    respond(
                        content = ByteReadChannel(loadTestResource("communication_pv_response_error.xml")),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "text/xml"),
                    )
                }
            val guestRegistry: GuestRegistryRepository = mockk()
            val (service, _) = buildCommService(buildBooking(), mockEngine = engine, guestRegistry = guestRegistry)

            service.submitAlta(bookingId, SesCommunicationType.PV)

            coVerify(exactly = 0) { guestRegistry.record(any()) }
        }

    @Test
    fun `submitAlta RH on a direct booking with no check-in succeeds`() =
        runTest {
            val booking = buildBooking(checkinCompletedAt = null, channel = BookingChannel.Direct)
            val (service, comms) = buildCommService(booking)

            val result = service.submitAlta(bookingId, SesCommunicationType.RH)

            assertTrue(result is SesHospedajesService.ManualSesSubmitResult.Submitted)
            coVerify(exactly = 1) {
                comms.upsertSubmitted(bookingId, SesCommunicationType.RH, SesCommunicationOperation.Alta, any(), any(), null)
            }
        }

    @Test
    fun `submitAlta RH files the booking principal guest, not the PV guest list`() =
        runTest {
            var capturedBody: String? = null
            val engine =
                MockEngine { request ->
                    capturedBody = String(request.body.toByteArray())
                    respond(
                        content = ByteReadChannel(loadTestResource("communication_pv_response_success.xml")),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "text/xml"),
                    )
                }
            // Principal guest name on the booking differs from the (PV) guests list (Juan Garcia),
            // so we can prove RH uses the booking principal.
            val booking =
                buildBooking(
                    checkinCompletedAt = null,
                    channel = BookingChannel.Direct,
                    guestFirstName = "Reserva",
                    guestLastName = "Holder",
                )
            val (service, _) = buildCommService(booking, mockEngine = engine)

            service.submitAlta(bookingId, SesCommunicationType.RH)

            val base64 =
                Regex("<solicitud>([^<]+)</solicitud>").find(requireNotNull(capturedBody))?.groupValues?.get(1)
            val xml =
                ZipInputStream(ByteArrayInputStream(Base64.getDecoder().decode(requireNotNull(base64))))
                    .also { it.nextEntry }
                    .readBytes()
                    .decodeToString()

            assertTrue(xml.contains("altaReservaHospedaje"))
            assertTrue(xml.contains("<rol>TI</rol>"))
            assertTrue(xml.contains("<nombre>Reserva</nombre>"), "RH should use the booking principal guest's name")
            assertTrue(xml.contains("<apellido1>Holder</apellido1>"))
            // SES requires a contact on the titular (teléfono o correo); we file the booking's email.
            assertTrue(xml.contains("<correo>guest@example.com</correo>"))
            // The PV guests list (Juan Garcia) must NOT leak into the RH communication.
            assertTrue(!xml.contains("Juan"), "RH must not file the PV guest list")
        }

    @Test
    fun `submitAlta RH without a phone or email is ineligible and sends no SOAP`() =
        runTest {
            val engine = MockEngine { error("SOAP must not be called for an RH without contact data") }
            val (service, comms) =
                buildCommService(
                    buildBooking(channel = BookingChannel.Direct, guestEmail = null, guestPhone = null),
                    mockEngine = engine,
                )

            assertFailsWith<ConflictException> { service.submitAlta(bookingId, SesCommunicationType.RH) }
            coVerify(exactly = 0) { comms.upsertSubmitted(any(), any(), any(), any(), any(), any()) }
        }

    @Test
    fun `submitAlta RH without a principal guest name is ineligible and sends no SOAP`() =
        runTest {
            val engine = MockEngine { error("SOAP must not be called for an RH without a guest name") }
            val (service, comms) =
                buildCommService(
                    buildBooking(channel = BookingChannel.Direct, guestFirstName = null, guestLastName = null),
                    mockEngine = engine,
                )

            assertFailsWith<ConflictException> { service.submitAlta(bookingId, SesCommunicationType.RH) }
            coVerify(exactly = 0) { comms.upsertSubmitted(any(), any(), any(), any(), any(), any()) }
        }

    @Test
    fun `submitAlta RH on a non-direct booking is ineligible and sends no SOAP`() =
        runTest {
            // MockEngine throws if hit → proves no SOAP call is made.
            val engine = MockEngine { error("SOAP must not be called for an ineligible RH") }
            val (service, comms) = buildCommService(buildBooking(channel = BookingChannel.Airbnb), mockEngine = engine)

            assertFailsWith<ConflictException> { service.submitAlta(bookingId, SesCommunicationType.RH) }
            coVerify(exactly = 0) { comms.upsertSubmitted(any(), any(), any(), any(), any(), any()) }
        }

    @Test
    fun `submitAlta RH with rhDirectBookingsEnabled false is ineligible and sends no SOAP`() =
        runTest {
            val engine = MockEngine { error("SOAP must not be called when RH is disabled") }
            val (service, comms) =
                buildCommService(
                    buildBooking(channel = BookingChannel.Direct),
                    integrations = listOf(buildIntegration(rhDirectBookingsEnabled = false)),
                    mockEngine = engine,
                )

            assertFailsWith<ConflictException> { service.submitAlta(bookingId, SesCommunicationType.RH) }
            coVerify(exactly = 0) { comms.upsertSubmitted(any(), any(), any(), any(), any(), any()) }
        }

    @Test
    fun `submitAlta on SOAP failure records upsertFailed`() =
        runTest {
            val engine = MockEngine { respond(content = ByteReadChannel("Unauthorized"), status = HttpStatusCode.Unauthorized) }
            val (service, comms) = buildCommService(buildBooking(), mockEngine = engine)

            val result = service.submitAlta(bookingId, SesCommunicationType.PV)

            assertTrue(result is SesHospedajesService.ManualSesSubmitResult.UpstreamError)
            coVerify(exactly = 1) {
                comms.upsertFailed(bookingId, SesCommunicationType.PV, SesCommunicationOperation.Alta, any())
            }
        }

    @Test
    fun `submitAlta on SES rejection records upsertRejected`() =
        runTest {
            val engine =
                MockEngine {
                    respond(
                        content = ByteReadChannel(loadTestResource("communication_pv_response_error.xml")),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "text/xml"),
                    )
                }
            val notifier: SesBookingRejectedNotifier = mockk(relaxUnitFun = true)
            val (service, comms) = buildCommService(buildBooking(), mockEngine = engine, bookingRejectedNotifier = notifier)

            val result = service.submitAlta(bookingId, SesCommunicationType.PV)

            assertTrue(result is SesHospedajesService.ManualSesSubmitResult.RejectedBySes)
            coVerify(exactly = 1) {
                comms.upsertRejected(bookingId, SesCommunicationType.PV, SesCommunicationOperation.Alta, any())
            }
            // A data rejection is owner-actionable: the owners are told which booking to fix.
            coVerify(exactly = 1) {
                notifier.notifyOwners(
                    match { it.id == bookingId },
                    match { it.id == propertyId },
                    SesCommunicationType.PV,
                    any(),
                )
            }
        }

    @Test
    fun `submitAlta on an already-accepted alta is ineligible`() =
        runTest {
            val engine = MockEngine { error("SOAP must not be called when already filed") }
            val (service, _) =
                buildCommService(
                    buildBooking(),
                    mockEngine = engine,
                    existingAlta = buildAltaComm(SesCommunicationType.PV, SesCommunicationStatus.Accepted),
                )

            assertFailsWith<ConflictException> { service.submitAlta(bookingId, SesCommunicationType.PV) }
        }

    @Test
    fun `submitCancellation on an accepted alta submits the anulación row`() =
        runTest {
            val engine =
                MockEngine {
                    respond(
                        content = ByteReadChannel(loadTestResource("anulacion_response_success.xml")),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "text/xml"),
                    )
                }
            val alta = buildAltaComm(SesCommunicationType.RH, SesCommunicationStatus.Accepted, codigo = "RH-C1")
            val (service, comms) =
                buildCommService(buildBooking(channel = BookingChannel.Direct), mockEngine = engine, existingAlta = alta)

            val result = service.submitCancellation(alta.id)

            assertTrue(result is SesHospedajesService.ManualSesSubmitResult.Submitted)
            coVerify(exactly = 1) {
                comms.upsertSubmitted(
                    bookingId,
                    SesCommunicationType.RH,
                    SesCommunicationOperation.Anulacion,
                    "b2c3d4e5-f6a7-8901-bcde-f23456789012",
                    "RH-C1",
                    alta.id,
                )
            }
        }

    @Test
    fun `submitCancellation when the alta is not accepted is ineligible`() =
        runTest {
            val engine = MockEngine { error("SOAP must not be called when there is no accepted alta") }
            val notAcceptedAlta = buildAltaComm(SesCommunicationType.PV, SesCommunicationStatus.Submitted)
            val (service, _) =
                buildCommService(buildBooking(), mockEngine = engine, existingAlta = notAcceptedAlta)

            assertFailsWith<ConflictException> { service.submitCancellation(notAcceptedAlta.id) }
        }

    @Test
    fun `submitCancellation when an active anulación already exists is ineligible`() =
        runTest {
            val engine = MockEngine { error("SOAP must not be called when an anulación is already active") }
            val alta = buildAltaComm(SesCommunicationType.PV, SesCommunicationStatus.Accepted)
            val activeAnulacion =
                SesCommunication(
                    id = Uuid.parse("00000000-0000-0000-0000-00000000a202"),
                    bookingId = bookingId,
                    communicationType = SesCommunicationType.PV,
                    operation = SesCommunicationOperation.Anulacion,
                    cancelsId = alta.id,
                    status = SesCommunicationStatus.Submitted,
                    lote = "LOTE-ANUL",
                    codigoComunicacion = alta.codigoComunicacion,
                    estado = null,
                    estadoDesc = null,
                    submittedAt = null,
                    lastAttemptAt = null,
                    lastError = null,
                    createdAt = OffsetDateTime.now(),
                    updatedAt = OffsetDateTime.now(),
                )
            val (service, _) =
                buildCommService(
                    buildBooking(),
                    mockEngine = engine,
                    existingAlta = alta,
                    existingAnulacion = activeAnulacion,
                )

            assertFailsWith<ConflictException> { service.submitCancellation(alta.id) }
        }

    @Test
    fun `pollCommunication re-polls and captures the codigo`() =
        runTest {
            val engine =
                MockEngine {
                    respond(
                        content = ByteReadChannel(loadTestResource("consult_lote_response_accepted_with_codigo.xml")),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "text/xml"),
                    )
                }
            val (service, comms) = buildCommService(buildBooking(channel = BookingChannel.Direct), mockEngine = engine)
            val pollable =
                SesCommunication(
                    id = Uuid.parse("00000000-0000-0000-0000-00000000a303"),
                    bookingId = bookingId,
                    communicationType = SesCommunicationType.RH,
                    operation = SesCommunicationOperation.Alta,
                    cancelsId = null,
                    status = SesCommunicationStatus.Submitted,
                    lote = "LOTE-RH-OK",
                    codigoComunicacion = null,
                    estado = null,
                    estadoDesc = null,
                    submittedAt = null,
                    lastAttemptAt = null,
                    lastError = null,
                    createdAt = OffsetDateTime.now(),
                    updatedAt = OffsetDateTime.now(),
                )
            coEvery { comms.getById(pollable.id) } returns pollable

            service.pollCommunication(pollable.id)

            coVerify(exactly = 1) {
                comms.markPolled(pollable.id, SesCodigoEstado.TramitadoSinErrores_1, any(), null, "RH-CODIGO-9999")
            }
        }

    // =====================================================================================
    // Credential-family errors (10103/10107/10119/10120) → integration flag, comm `failed`
    // =====================================================================================

    private fun badCredentialsEngine(): MockEngine =
        MockEngine {
            respond(
                content = ByteReadChannel(loadTestResource("communication_pv_response_bad_credentials.xml")),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "text/xml"),
            )
        }

    @Test
    fun `submitAlta on credential error marks comm failed and flags the integration`() =
        runTest {
            val guard: SesCredentialsGuard = mockk(relaxUnitFun = true)
            val notifier: SesBookingRejectedNotifier = mockk(relaxUnitFun = true)
            val (service, comms) =
                buildCommService(
                    buildBooking(),
                    mockEngine = badCredentialsEngine(),
                    credentialsGuard = guard,
                    bookingRejectedNotifier = notifier,
                )

            val result = service.submitAlta(bookingId, SesCommunicationType.PV)

            assertTrue(result is SesHospedajesService.ManualSesSubmitResult.CredentialsError)
            assertEquals(10107, result.codigo)
            // The comm row is `failed` (retry-eligible once the flag clears) — never `rejected`,
            // which would gate the booking on a data change that will never come.
            coVerify(exactly = 1) {
                comms.upsertFailed(
                    bookingId,
                    SesCommunicationType.PV,
                    SesCommunicationOperation.Alta,
                    match { it.contains("10107") },
                )
            }
            coVerify(exactly = 0) { comms.upsertRejected(any(), any(), any(), any()) }
            coVerify(exactly = 1) { guard.onCredentialError(any(), 10107, "Usuario incorrecto") }
            // A credential outage already fires its own one-time owner email — no per-booking one.
            coVerify(exactly = 0) { notifier.notifyOwners(any(), any(), any(), any()) }
        }

    @Test
    fun `submitAlta with honorCredentialsPause skips an already-flagged integration without calling SES`() =
        runTest {
            val flagged = buildIntegration().copy(credentialsErrorCode = 10107, credentialsErrorMessage = "Usuario incorrecto")
            // The auto-job path must short-circuit before any SOAP call: an engine that throws
            // proves SES is never contacted.
            val engine = MockEngine { throw IllegalStateException("SES must not be called for a paused integration") }
            val (service, comms) = buildCommService(buildBooking(), integrations = listOf(flagged), mockEngine = engine)

            val result = service.submitAlta(bookingId, SesCommunicationType.PV, honorScopedPauses = true)

            assertTrue(result is SesHospedajesService.ManualSesSubmitResult.CredentialsError)
            assertEquals(10107, result.codigo)
            // No comm-row churn either — the booking simply stays pending until the pause lifts.
            coVerify(exactly = 0) { comms.upsertFailed(any(), any(), any(), any()) }
            coVerify(exactly = 0) { comms.upsertRejected(any(), any(), any(), any()) }
        }

    @Test
    fun `submitAlta on non-credential rejection keeps the rejected path and does not flag`() =
        runTest {
            val guard: SesCredentialsGuard = mockk(relaxUnitFun = true)
            val engine =
                MockEngine {
                    respond(
                        content = ByteReadChannel(loadTestResource("communication_pv_response_error.xml")),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "text/xml"),
                    )
                }
            val (service, comms) = buildCommService(buildBooking(), mockEngine = engine, credentialsGuard = guard)

            val result = service.submitAlta(bookingId, SesCommunicationType.PV)

            assertTrue(result is SesHospedajesService.ManualSesSubmitResult.RejectedBySes)
            coVerify(exactly = 1) { comms.upsertRejected(any(), any(), any(), any()) }
            coVerify(exactly = 0) { guard.onCredentialError(any(), any(), any()) }
        }

    @Test
    fun `submitAlta success reports to the guard so a stale credential flag self-heals`() =
        runTest {
            val guard: SesCredentialsGuard = mockk(relaxUnitFun = true)
            val integration = buildIntegration()
            val (service, _) =
                buildCommService(
                    buildBooking(),
                    integrations = listOf(integration),
                    credentialsGuard = guard,
                )

            val result = service.submitAlta(bookingId, SesCommunicationType.PV)

            assertTrue(result is SesHospedajesService.ManualSesSubmitResult.Submitted)
            coVerify(exactly = 1) { guard.onSesSuccess(integration) }
        }

    @Test
    fun `submitCancellation on credential error marks anulacion failed and flags the integration`() =
        runTest {
            val guard: SesCredentialsGuard = mockk(relaxUnitFun = true)
            val accepted = buildAltaComm(SesCommunicationType.PV, SesCommunicationStatus.Accepted)
            val (service, comms) =
                buildCommService(
                    buildBooking(),
                    mockEngine = badCredentialsEngine(),
                    existingAlta = accepted,
                    credentialsGuard = guard,
                )

            val result = service.submitCancellation(accepted.id)

            assertTrue(result is SesHospedajesService.ManualSesSubmitResult.CredentialsError)
            coVerify(exactly = 1) {
                comms.upsertFailed(
                    bookingId,
                    SesCommunicationType.PV,
                    SesCommunicationOperation.Anulacion,
                    match { it.contains("10107") },
                )
            }
            coVerify(exactly = 0) { comms.upsertRejected(any(), any(), any(), any()) }
            coVerify(exactly = 1) { guard.onCredentialError(any(), 10107, "Usuario incorrecto") }
        }
}
