package eu.kasify.db

import eu.kasify.TEST_ACCOUNT_1_PROPERTY_1
import eu.kasify.db.model.SesCodigoEstado
import eu.kasify.model.SesSubmissionOperation
import eu.kasify.routes.BaseRouteDBTests
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.uuid.Uuid

class SesHospedajesSubmissionsRepositoryTest : BaseRouteDBTests() {
    private val repository = SesHospedajesSubmissionsRepository()

    private val eventId = Uuid.parse("E0000000-0000-0000-0000-0000000000a1")
    private val bookingId = Uuid.parse("40000000-0000-0000-0000-0000000000a1")

    override fun setupData() {
        testDBConnection.executeQuery(
            "INSERT INTO public.calendar_events (id, property_id, from_datetime, to_datetime, \"type\", created_at, updated_at) " +
                "VALUES('$eventId', '${TEST_ACCOUNT_1_PROPERTY_1.id}', " +
                "'2026-05-20 15:00:00+00', '2026-05-25 11:00:00+00', 'booking', now(), now())",
        )
        testDBConnection.executeQuery(
            "INSERT INTO public.bookings (id, calendar_event_id, property_id, channel, channel_id, status, created_at, updated_at) " +
                "VALUES('$bookingId', '$eventId', '${TEST_ACCOUNT_1_PROPERTY_1.id}', 'direct', 'ref1', 'confirmed', now(), now())",
        )
    }

    @Test
    fun `recordSubmitAttempt persists a submit row that lists_back`() =
        runBlocking {
            repository.recordSubmitAttempt(
                bookingId = bookingId,
                respuestaCodigo = 0,
                respuestaDescripcion = "Ok",
                sesLote = "ac2e43fc-5417-11f1-aabd-00505695dcc7",
                responseXml = "<xml/>",
                errorText = null,
            )

            val rows = repository.listSubmissionsForBooking(bookingId)
            assertEquals(1, rows.size)
            val row = rows.first()
            assertEquals(SesSubmissionOperation.Submit, row.operation)
            assertEquals(0, row.respuestaCodigo)
            assertEquals("ac2e43fc-5417-11f1-aabd-00505695dcc7", row.sesLote)
            assertNull(row.sesEstado)
            assertNull(row.errorText)
        }

    @Test
    fun `recordSubmitAttempt with SES unreachable persists null codigo and error_text`() =
        runBlocking {
            repository.recordSubmitAttempt(
                bookingId = bookingId,
                respuestaCodigo = null,
                respuestaDescripcion = null,
                sesLote = null,
                responseXml = null,
                errorText = "ConnectException: connection refused",
            )

            val rows = repository.listSubmissionsForBooking(bookingId)
            assertEquals(1, rows.size)
            val row = rows.first()
            assertNull(row.respuestaCodigo)
            assertNull(row.sesLote)
            assertEquals("ConnectException: connection refused", row.errorText)
        }

    @Test
    fun `recordQueryAttempt persists tramitado_con_errores_parciales with ses_estado_desc`(): Unit =
        runBlocking {
            repository.recordSubmitAttempt(
                bookingId = bookingId,
                respuestaCodigo = 0,
                respuestaDescripcion = "Ok",
                sesLote = "ac2e43fc",
                responseXml = null,
                errorText = null,
            )
            repository.recordQueryAttempt(
                bookingId = bookingId,
                sesLote = "ac2e43fc",
                respuestaCodigo = 0,
                respuestaDescripcion = "Ok",
                sesEstado = SesCodigoEstado.TramitadoConErroresParciales_6,
                sesEstadoDesc = "Lote tramitado pero hay errores en algunas comunicaciones",
                responseXml = "<xml/>",
                errorText = null,
            )

            val rows = repository.listSubmissionsForBooking(bookingId)
            assertEquals(2, rows.size)
            // Listed DESC by attempted_at — the query row is the most recent.
            val latest = rows.first()
            assertEquals(SesSubmissionOperation.Query, latest.operation)
            assertEquals(SesCodigoEstado.TramitadoConErroresParciales_6, latest.sesEstado)
            assertNotNull(latest.sesEstadoDesc)
        }

    @Test
    fun `error_text longer than 1000 chars is truncated`() =
        runBlocking {
            val longError = "x".repeat(2000)
            repository.recordSubmitAttempt(
                bookingId = bookingId,
                respuestaCodigo = null,
                respuestaDescripcion = null,
                sesLote = null,
                responseXml = null,
                errorText = longError,
            )

            val rows = repository.listSubmissionsForBooking(bookingId)
            assertEquals(1000, rows.first().errorText!!.length)
        }
}
