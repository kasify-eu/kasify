package eu.kasify.db

import eu.kasify.TEST_ACCOUNT_1_PROPERTY_1
import eu.kasify.db.model.SesCodigoEstado
import eu.kasify.extensions.TEST_ENCRYPTION_KEY
import eu.kasify.model.SesCommunicationOperation
import eu.kasify.model.SesCommunicationStatus
import eu.kasify.model.SesCommunicationType
import eu.kasify.routes.BaseRouteDBTests
import eu.kasify.security.EncryptionService
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class SesCommunicationsRepositoryTest : BaseRouteDBTests() {
    private val repository = SesCommunicationsRepository()

    private val eventId = Uuid.parse("E1000000-0000-0000-0000-0000000000a1")
    private val bookingId = Uuid.parse("41000000-0000-0000-0000-0000000000a1")

    // A second, cancelled booking used by the anulación discovery tests.
    private val cancelledEventId = Uuid.parse("E1000000-0000-0000-0000-0000000000b2")
    private val cancelledBookingId = Uuid.parse("41000000-0000-0000-0000-0000000000b2")

    // Fixture SES integration the test property is assigned to: anulación discovery applies the
    // strict participation gate (explicit assignment + enabled + unflagged integration).
    private val sesIntegrationId = Uuid.parse("51000000-0000-0000-0000-0000000000b1")

    override fun setupData() {
        testDBConnection.insertSesIntegration(TEST_ACCOUNT_1_PROPERTY_1.accountId, integrationId = sesIntegrationId)
        testDBConnection.assignPropertyToSesIntegration(TEST_ACCOUNT_1_PROPERTY_1.id, sesIntegrationId)
        testDBConnection.executeQuery(
            "INSERT INTO public.calendar_events (id, property_id, from_datetime, to_datetime, \"type\", created_at, updated_at) " +
                "VALUES('$eventId', '${TEST_ACCOUNT_1_PROPERTY_1.id}', " +
                "'2026-05-20 15:00:00+00', '2026-05-25 11:00:00+00', 'booking', now(), now())",
        )
        testDBConnection.executeQuery(
            "INSERT INTO public.bookings (id, calendar_event_id, property_id, channel, channel_id, status, created_at, updated_at) " +
                "VALUES('$bookingId', '$eventId', '${TEST_ACCOUNT_1_PROPERTY_1.id}', 'direct', 'ref1', 'confirmed', now(), now())",
        )
        testDBConnection.executeQuery(
            "INSERT INTO public.calendar_events (id, property_id, from_datetime, to_datetime, \"type\", created_at, updated_at) " +
                "VALUES('$cancelledEventId', '${TEST_ACCOUNT_1_PROPERTY_1.id}', " +
                "'2026-05-20 15:00:00+00', '2026-05-25 11:00:00+00', 'booking', now(), now())",
        )
        testDBConnection.executeQuery(
            "INSERT INTO public.bookings (id, calendar_event_id, property_id, channel, channel_id, status, cancelled_at, created_at, updated_at) " +
                "VALUES('$cancelledBookingId', '$cancelledEventId', '${TEST_ACCOUNT_1_PROPERTY_1.id}', " +
                "'direct', 'ref2', 'cancelled', now(), now(), now())",
        )
    }

    @Test
    fun `upsertSubmitted creates an alta row that lists back`() =
        runBlocking {
            repository.upsertSubmitted(
                bookingId = bookingId,
                type = SesCommunicationType.PV,
                operation = SesCommunicationOperation.Alta,
                lote = "LOTE-PV-1",
                codigoComunicacion = "PV-C1",
            )

            val rows = repository.listByBooking(bookingId)
            assertEquals(1, rows.size)
            val row = rows.first()
            assertEquals(SesCommunicationType.PV, row.communicationType)
            assertEquals(SesCommunicationOperation.Alta, row.operation)
            assertEquals(SesCommunicationStatus.Submitted, row.status)
            assertEquals("LOTE-PV-1", row.lote)
            assertEquals("PV-C1", row.codigoComunicacion)
            assertNotNull(row.submittedAt)
            assertNull(row.lastError)
        }

    @Test
    fun `upsertFailed then upsertSubmitted updates the same row and clears the error`() =
        runBlocking {
            repository.upsertFailed(
                bookingId = bookingId,
                type = SesCommunicationType.PV,
                operation = SesCommunicationOperation.Alta,
                error = "ConnectException: refused",
            )
            val afterFail = repository.getByKey(bookingId, SesCommunicationType.PV, SesCommunicationOperation.Alta)
            assertNotNull(afterFail)
            assertEquals(SesCommunicationStatus.Failed, afterFail.status)
            assertEquals("ConnectException: refused", afterFail.lastError)

            repository.upsertSubmitted(
                bookingId = bookingId,
                type = SesCommunicationType.PV,
                operation = SesCommunicationOperation.Alta,
                lote = "LOTE-PV-1",
                codigoComunicacion = "PV-C1",
            )

            val rows = repository.listByBooking(bookingId)
            assertEquals(1, rows.size)
            val row = rows.first()
            assertEquals(SesCommunicationStatus.Submitted, row.status)
            assertNull(row.lastError)
            assertEquals("LOTE-PV-1", row.lote)
        }

    @Test
    fun `upsertSubmitted with Anulacion creates an anulacion row pointing at the alta`() =
        runBlocking {
            val altaId =
                repository.upsertSubmitted(
                    bookingId = bookingId,
                    type = SesCommunicationType.RH,
                    operation = SesCommunicationOperation.Alta,
                    lote = "LOTE-RH-1",
                    codigoComunicacion = "RH-C1",
                )

            repository.upsertSubmitted(
                bookingId = bookingId,
                type = SesCommunicationType.RH,
                operation = SesCommunicationOperation.Anulacion,
                lote = "LOTE-RH-2",
                codigoComunicacion = "RH-C1",
                cancelsId = altaId,
            )

            val anulacion = repository.getByKey(bookingId, SesCommunicationType.RH, SesCommunicationOperation.Anulacion)
            assertNotNull(anulacion)
            assertEquals(SesCommunicationStatus.Submitted, anulacion.status)
            assertEquals(altaId, anulacion.cancelsId)
            assertEquals("RH-C1", anulacion.codigoComunicacion)
            assertEquals("LOTE-RH-2", anulacion.lote)
        }

    @Test
    fun `listPendingPoll returns submitted rows with a lote and drops them after markPolled to accepted`() =
        runBlocking {
            val pvId =
                repository.upsertSubmitted(
                    bookingId = bookingId,
                    type = SesCommunicationType.PV,
                    operation = SesCommunicationOperation.Alta,
                    lote = "LOTE-PV-1",
                    codigoComunicacion = "PV-C1",
                )
            // A lote-less failed row must not be polled.
            repository.upsertFailed(
                bookingId = bookingId,
                type = SesCommunicationType.RH,
                operation = SesCommunicationOperation.Alta,
                error = "boom",
            )

            val pending = repository.listPendingPoll(50)
            assertEquals(1, pending.size)
            assertEquals(pvId, pending.first().id)

            repository.markPolled(pvId, SesCodigoEstado.TramitadoSinErrores_1, "Tramitado sin errores")

            val afterPoll = repository.listPendingPoll(50)
            assertTrue(afterPoll.isEmpty())
            val polled = repository.getById(pvId)
            assertNotNull(polled)
            assertEquals(SesCommunicationStatus.Accepted, polled.status)
            assertEquals(SesCodigoEstado.TramitadoSinErrores_1, polled.estado)
        }

    @Test
    fun `resubmit after a terminal-error poll clears estado so the new lote is polled again`() =
        runBlocking {
            val pvId =
                repository.upsertSubmitted(
                    bookingId = bookingId,
                    type = SesCommunicationType.PV,
                    operation = SesCommunicationOperation.Alta,
                    lote = "LOTE-PV-1",
                    codigoComunicacion = null,
                )
            // Poll resolves the first lote to a terminal partial-error estado → Rejected, drops out of the poll index.
            repository.markPolled(pvId, SesCodigoEstado.TramitadoConErroresParciales_6, "Tramitado con errores parciales")
            assertTrue(repository.listPendingPoll(50).isEmpty())

            // The submission job retries the booking and SES accepts a fresh lote.
            repository.upsertSubmitted(
                bookingId = bookingId,
                type = SesCommunicationType.PV,
                operation = SesCommunicationOperation.Alta,
                lote = "LOTE-PV-2",
                codigoComunicacion = null,
            )

            // The new lote must re-enter the poll index — the stale estado/desc from the old lote is cleared.
            val resubmitted = repository.getById(pvId)
            assertNotNull(resubmitted)
            assertEquals(SesCommunicationStatus.Submitted, resubmitted.status)
            assertEquals("LOTE-PV-2", resubmitted.lote)
            assertNull(resubmitted.estado)
            assertNull(resubmitted.estadoDesc)

            val pending = repository.listPendingPoll(50)
            assertEquals(1, pending.size)
            assertEquals(pvId, pending.first().id)
        }

    @Test
    fun `markPolled maps EnProceso to Processing and error estado to Rejected`() =
        runBlocking {
            val pvId =
                repository.upsertSubmitted(
                    bookingId = bookingId,
                    type = SesCommunicationType.PV,
                    operation = SesCommunicationOperation.Alta,
                    lote = "LOTE-PV-1",
                    codigoComunicacion = "PV-C1",
                )

            repository.markPolled(pvId, SesCodigoEstado.EnProceso_4, "En proceso")
            assertEquals(SesCommunicationStatus.Processing, repository.getById(pvId)!!.status)
            // Still non-terminal → remains pollable.
            assertEquals(1, repository.listPendingPoll(50).size)

            repository.markPolled(pvId, SesCodigoEstado.ErroresCabeceraFormato_2, "Errores de cabecera")
            assertEquals(SesCommunicationStatus.Rejected, repository.getById(pvId)!!.status)
            assertTrue(repository.listPendingPoll(50).isEmpty())
        }

    @Test
    fun `listPendingPoll re-includes an accepted alta still missing its codigo`() =
        runBlocking {
            val id =
                repository.upsertSubmitted(
                    bookingId = bookingId,
                    type = SesCommunicationType.PV,
                    operation = SesCommunicationOperation.Alta,
                    lote = "LOTE-NO-CODIGO",
                    codigoComunicacion = null,
                )
            // Accepted but the código wasn't captured (terminal estado, yet still cancellable-blocked).
            repository.markPolled(id, SesCodigoEstado.TramitadoSinErrores_1, "ok")
            val pending = repository.listPendingPoll(50)
            assertEquals(1, pending.size)
            assertEquals(id, pending.first().id)

            // The next poll supplies the código → the row drops out of the poll set.
            repository.markPolled(id, SesCodigoEstado.TramitadoSinErrores_1, "ok", codigoComunicacion = "PV-CODE")
            assertEquals("PV-CODE", repository.getById(id)!!.codigoComunicacion)
            assertTrue(repository.listPendingPoll(50).isEmpty())
        }

    // Creates an accepted alta for the given booking/type by submitting then polling to terminal success.
    private suspend fun acceptedAlta(
        booking: Uuid,
        type: SesCommunicationType,
    ): Uuid {
        val id =
            repository.upsertSubmitted(
                bookingId = booking,
                type = type,
                operation = SesCommunicationOperation.Alta,
                lote = "LOTE-${type.dbValue}",
                codigoComunicacion = "CODIGO-${type.dbValue}",
            )
        repository.markPolled(id, SesCodigoEstado.TramitadoSinErrores_1, "ok")
        return id
    }

    @Test
    fun `discovery returns accepted alta of a cancelled booking with no anulacion`() =
        runBlocking {
            acceptedAlta(cancelledBookingId, SesCommunicationType.PV)

            val found = repository.acceptedAltasForCancelledBookingsWithoutAnulacion(50)
            assertEquals(1, found.size)
            assertEquals(cancelledBookingId, found.first().bookingId)
            assertEquals(SesCommunicationType.PV, found.first().communicationType)
        }

    @Test
    fun `discovery excludes accepted altas of non-cancelled bookings`() =
        runBlocking {
            acceptedAlta(bookingId, SesCommunicationType.PV) // bookingId is confirmed

            assertTrue(repository.acceptedAltasForCancelledBookingsWithoutAnulacion(50).isEmpty())
        }

    @Test
    fun `discovery excludes when an active anulacion exists`() =
        runBlocking {
            val altaId = acceptedAlta(cancelledBookingId, SesCommunicationType.PV)
            repository.upsertSubmitted(
                bookingId = cancelledBookingId,
                type = SesCommunicationType.PV,
                operation = SesCommunicationOperation.Anulacion,
                lote = "LOTE-ANUL",
                codigoComunicacion = "CODIGO-pv",
                cancelsId = altaId,
            )

            assertTrue(repository.acceptedAltasForCancelledBookingsWithoutAnulacion(50).isEmpty())
        }

    @Test
    fun `discovery retries when the only anulacion is failed`() =
        runBlocking {
            acceptedAlta(cancelledBookingId, SesCommunicationType.PV)
            repository.upsertFailed(
                bookingId = cancelledBookingId,
                type = SesCommunicationType.PV,
                operation = SesCommunicationOperation.Anulacion,
                error = "transport boom",
            )

            val found = repository.acceptedAltasForCancelledBookingsWithoutAnulacion(50)
            assertEquals(1, found.size)
            assertEquals(SesCommunicationOperation.Alta, found.first().operation)
        }

    @Test
    fun `discovery skips anulaciones of a flagged or unassigned integration`() =
        runBlocking {
            acceptedAlta(cancelledBookingId, SesCommunicationType.PV)
            val integrationsRepository =
                IntegrationsRepository(PropertiesRepository(), EncryptionService(key = TEST_ENCRYPTION_KEY))

            // Healthy assigned integration (setupData): the anulación candidate is discovered.
            assertEquals(1, repository.acceptedAltasForCancelledBookingsWithoutAnulacion(50).size)

            // Credential error flagged on the assigned integration: its anulaciones pause too.
            integrationsRepository.markSesCredentialsError(sesIntegrationId, 10107, "Usuario incorrecto")
            assertTrue(repository.acceptedAltasForCancelledBookingsWithoutAnulacion(50).isEmpty())

            // Flag cleared: discovery resumes.
            integrationsRepository.clearSesCredentialsError(sesIntegrationId)
            assertEquals(1, repository.acceptedAltasForCancelledBookingsWithoutAnulacion(50).size)

            // Strict model: unassigning the property removes it from discovery entirely.
            testDBConnection.executeQuery(
                "UPDATE properties SET ses_integration_id = NULL WHERE id = '${TEST_ACCOUNT_1_PROPERTY_1.id}'",
            )
            assertTrue(repository.acceptedAltasForCancelledBookingsWithoutAnulacion(50).isEmpty())
        }
}
