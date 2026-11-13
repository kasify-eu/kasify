package eu.kasify.sesmock

import eu.kasify.plugins.ses.SesAddress
import eu.kasify.plugins.ses.SesContract
import eu.kasify.plugins.ses.SesGuest
import eu.kasify.plugins.ses.SesHospedajesPlugin
import eu.kasify.plugins.ses.SesParteViajerosRequest
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * End-to-end tests of the local SES mock's async lote lifecycle through the real production
 * client ([SesHospedajesPlugin] → SOAP over HTTP). The key scenario: SES accepts an alta at
 * submit time (codigo=0 + lote) and only a later consultaLote poll reveals the rejection —
 * the flow the poll job, the rejected-alta retry gate and the libro registro must handle.
 */
class SesMockServerLifecycleTest {
    private fun pvRequest(
        referencia: String,
        apellido1: String = "García",
        establishmentCode: String = "H1234AB",
    ) = SesParteViajerosRequest(
        arrendadorCode = "0000000123",
        establishmentCode = establishmentCode,
        contract =
            SesContract(
                referencia = referencia,
                fechaContrato = LocalDate.of(2026, 7, 1),
                fechaEntrada = LocalDateTime.of(2026, 7, 10, 15, 0),
                fechaSalida = LocalDateTime.of(2026, 7, 15, 11, 0),
                numPersonas = 1,
                numHabitaciones = 2,
                internet = true,
                tipoPago = "PLATF",
            ),
        guests =
            listOf(
                SesGuest(
                    rol = "VI",
                    nombre = "Ana",
                    apellido1 = apellido1,
                    apellido2 = null,
                    tipoDocumento = "NIF",
                    numeroDocumento = "12345678Z",
                    soporteDocumento = null,
                    fechaNacimiento = LocalDate.of(1990, 1, 1),
                    nacionalidad = "ESP",
                    sexo = "M",
                    direccion =
                        SesAddress(
                            direccion = "Calle Mayor 1",
                            direccionComplementaria = null,
                            codigoMunicipio = "28079",
                            nombreMunicipio = "Madrid",
                            codigoPostal = "28001",
                            pais = "ESP",
                        ),
                    telefono = "600000000",
                    correo = null,
                    parentesco = null,
                ),
            ),
    )

    /** Boot the mock on a free port (instant processing) and run [block] against a plugin pointed at it. */
    private fun withMockAndPlugin(block: suspend (SesHospedajesPlugin) -> Unit) {
        val server = startSesMock(processSeconds = 0)
        server.start(wait = false)
        try {
            runBlocking {
                val port =
                    server.engine
                        .resolvedConnectors()
                        .first()
                        .port
                val plugin =
                    SesHospedajesPlugin(
                        sesUsername = "test",
                        sesPassword = "test",
                        endpointOverride = "http://localhost:$port/hospedajes-web/ws/v1/comunicacion",
                    )
                try {
                    block(plugin)
                } finally {
                    plugin.close()
                }
            }
        } finally {
            server.stop()
        }
    }

    @Test
    fun `BOOM alta is accepted at submit but polls a terminal error estado 3`() =
        withMockAndPlugin { plugin ->
            val submit = plugin.submitParteViajeros(pvRequest("REF-BOOM-1"))
            // Accepted at submission: SES queues the lote and reports no error...
            assertEquals(0, submit.codigo)
            val lote = assertNotNull(submit.lote)

            // ...and only the consultaLote poll reveals the rejection (estado 3, error inesperado).
            val batch = plugin.consultBatch(listOf(lote)).resultados.single { it.lote == lote }
            assertEquals(3, batch.codigoEstado)
        }

    @Test
    fun `PARTIAL alta is accepted at submit but polls estado 6 with a per-communication error`() =
        withMockAndPlugin { plugin ->
            val submit = plugin.submitParteViajeros(pvRequest("REF-PARTIAL-1"))
            assertEquals(0, submit.codigo)
            val lote = assertNotNull(submit.lote)

            val batch = plugin.consultBatch(listOf(lote)).resultados.single { it.lote == lote }
            assertEquals(6, batch.codigoEstado)
            val error = assertNotNull(batch.comunicaciones.single().error)
            assertTrue(error.contains("PARTIAL"))
        }

    @Test
    fun `a guest name containing a trigger keyword polls estado 6, and a corrected resubmission succeeds`() =
        withMockAndPlugin { plugin ->
            // Trigger keywords in nombre/apellido are matched case-insensitively.
            val submit = plugin.submitParteViajeros(pvRequest("REF-OK-2", apellido1 = "García boom"))
            assertEquals(0, submit.codigo)
            val lote = assertNotNull(submit.lote)

            val rejected = plugin.consultBatch(listOf(lote)).resultados.single { it.lote == lote }
            assertEquals(6, rejected.codigoEstado)
            val error = assertNotNull(rejected.comunicaciones.single().error)
            assertTrue(error.contains("García boom"))

            // The admin fixes the guest's surname and force-resubmits: new lote, tramitado sin errores.
            val resubmit = plugin.submitParteViajeros(pvRequest("REF-OK-2", apellido1 = "García"))
            assertEquals(0, resubmit.codigo)
            val newLote = assertNotNull(resubmit.lote)

            val accepted = plugin.consultBatch(listOf(newLote)).resultados.single { it.lote == newLote }
            assertEquals(1, accepted.codigoEstado)
            assertNotNull(accepted.comunicaciones.single().codigoComunicacion)
        }

    @Test
    fun `clean alta polls estado 1 with the codigo only available via the poll`() =
        withMockAndPlugin { plugin ->
            val submit = plugin.submitParteViajeros(pvRequest("REF-OK-1"))
            assertEquals(0, submit.codigo)
            val lote = assertNotNull(submit.lote)
            // Like production, the submit response never carries the per-communication código.
            assertTrue(submit.resultados.all { it.codigoComunicacion == null })

            val batch = plugin.consultBatch(listOf(lote)).resultados.single { it.lote == lote }
            assertEquals(1, batch.codigoEstado)
            assertNotNull(batch.comunicaciones.single().codigoComunicacion)
        }

    // --- E<código> establishment-code trigger (synchronous Layer-1 errors) ---

    @Test
    fun `establishment code E10103 forces a synchronous credential-family rejection with no lote`() =
        withMockAndPlugin { plugin ->
            val submit = plugin.submitParteViajeros(pvRequest("REF-CRED-1", establishmentCode = "E10103"))

            // Rejected at the door like real SES: non-zero código in a 200 body, nothing queued.
            assertEquals(10103, submit.codigo)
            assertEquals("El código de arrendador no existe en el sistema", submit.descripcion)
            assertEquals(null, submit.lote)
        }

    @Test
    fun `establishment code E10121 forces a synchronous non-credential rejection`() =
        withMockAndPlugin { plugin ->
            // Control case: the trigger covers every mapped Layer-1 código, so the rejected-path
            // classification (10121 → comm `rejected`, no integration flag) is testable too.
            val submit = plugin.submitParteViajeros(pvRequest("REF-VAL-1", establishmentCode = "E10121"))

            assertEquals(10121, submit.codigo)
            assertEquals("Error de validación", submit.descripcion)
            assertEquals(null, submit.lote)
        }

    @Test
    fun `an E-prefixed establishment code outside the mapped codigos behaves normally`() =
        withMockAndPlugin { plugin ->
            // E12345 matches the E+5-digits shape but is not a mapped código — it must be treated
            // as a regular establishment code, so a realistic value can never trigger accidentally.
            val submit = plugin.submitParteViajeros(pvRequest("REF-OK-3", establishmentCode = "E12345"))

            assertEquals(0, submit.codigo)
            val lote = assertNotNull(submit.lote)
            val batch = plugin.consultBatch(listOf(lote)).resultados.single { it.lote == lote }
            assertEquals(1, batch.codigoEstado)
        }
}
