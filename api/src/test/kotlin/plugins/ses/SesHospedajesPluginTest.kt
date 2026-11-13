package eu.kasify.plugins.ses

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.ByteArrayInputStream
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Base64
import java.util.zip.ZipInputStream
import kotlin.test.Ignore
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SesHospedajesPluginTest {
    private fun loadTestResource(fileName: String): String =
        this::class.java.classLoader
            .getResourceAsStream("ses/$fileName")
            ?.readBytes()
            ?.decodeToString()
            ?: throw IllegalArgumentException("Resource not found: ses/$fileName")

    private fun createPlugin(mockEngine: MockEngine): SesHospedajesPlugin =
        SesHospedajesPlugin(
            sesUsername = "testuser",
            sesPassword = "testpass",
            environment = SesEnvironment.TEST,
            httpClient = HttpClient(mockEngine),
        )

    private fun createTestRequest(): SesParteViajerosRequest =
        SesParteViajerosRequest(
            arrendadorCode = "0000000001",
            establishmentCode = "1234567890",
            contract =
                SesContract(
                    referencia = "BOOK-001",
                    fechaContrato = LocalDate.of(2026, 4, 15),
                    fechaEntrada = LocalDateTime.of(2026, 4, 20, 15, 0),
                    fechaSalida = LocalDateTime.of(2026, 4, 25, 11, 0),
                    numPersonas = 2,
                    numHabitaciones = 1,
                    internet = true,
                    tipoPago = "OTROS",
                ),
            guests =
                listOf(
                    SesGuest(
                        rol = "VI",
                        nombre = "Juan",
                        apellido1 = "Garcia",
                        apellido2 = "Lopez",
                        tipoDocumento = "NIF",
                        numeroDocumento = "12345678Z",
                        soporteDocumento = "999999999",
                        fechaNacimiento = LocalDate.of(1990, 5, 15),
                        nacionalidad = "ESP",
                        sexo = "H",
                        direccion =
                            SesAddress(
                                direccion = "C/Serrano 127",
                                direccionComplementaria = "Portal A. 2B",
                                codigoMunicipio = "28079",
                                nombreMunicipio = null,
                                codigoPostal = "28001",
                                pais = "ESP",
                            ),
                        telefono = "666666666",
                        correo = "juan@example.com",
                        parentesco = null,
                    ),
                    SesGuest(
                        rol = "VI",
                        nombre = "Pedro",
                        apellido1 = "Garcia",
                        apellido2 = "Fernandez",
                        tipoDocumento = null,
                        numeroDocumento = null,
                        soporteDocumento = null,
                        fechaNacimiento = LocalDate.of(2018, 1, 1),
                        nacionalidad = "ESP",
                        sexo = "H",
                        direccion =
                            SesAddress(
                                direccion = "C/Serrano 127",
                                direccionComplementaria = "Portal A. 2B",
                                codigoMunicipio = "28079",
                                nombreMunicipio = null,
                                codigoPostal = "28001",
                                pais = "ESP",
                            ),
                        telefono = "666666666",
                        correo = null,
                        parentesco = "HJ",
                    ),
                ),
        )

    // --- Catalog tests (existing) ---

    @Test @Ignore
    fun `production test to get the catalog`() =
        runTest {
            val sesUsername = System.getenv("SES_USERNAME") ?: error("SES_USERNAME env var not set")
            val sesPassword = System.getenv("SES_PASSWORD") ?: error("SES_PASSWORD env var not set")
            val plugin = SesHospedajesPlugin(sesUsername, sesPassword, environment = SesEnvironment.TEST)
            val catalog = plugin.getCatalog()
            val items = catalog.size
        }

    @Test @Ignore
    fun `production test - consulta lotes`() =
        runTest {
            val sesUsername = System.getenv("SES_USERNAME") ?: error("SES_USERNAME env var not set")
            val sesPassword = System.getenv("SES_PASSWORD") ?: error("SES_PASSWORD env var not set")
            val plugin = SesHospedajesPlugin(sesUsername, sesPassword, environment = SesEnvironment.PRODUCTION)
            val response = plugin.consultBatch(listOf("ac2e43fc-5417-11f1-aabd-00505695dcc7"))
            assertNotNull(response)
        }

    @Test
    fun `getCatalog without name returns list of available tables`() =
        runTest {
            val mockEngine =
                MockEngine { request ->
                    assertEquals(HttpMethod.Post, request.method)
                    assertTrue(request.url.toString().contains("pre-ses.mir.es"))
                    respond(
                        content = ByteReadChannel(loadTestResource("catalog_tables_response.xml")),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "text/xml"),
                    )
                }

            val plugin = createPlugin(mockEngine)
            val entries = plugin.getCatalog()

            assertEquals(9, entries.size)
            assertEquals("SEXO", entries[0].code)
            assertEquals("sexos", entries[0].description)
            assertEquals("TIPO_DOCUMENTO", entries[2].code)
            assertEquals("TIPO_PAGO", entries[5].code)
        }

    @Test
    fun `getCatalog with TIPO_DOCUMENTO returns document types`() =
        runTest {
            val mockEngine =
                MockEngine { request ->
                    val body = String(request.body.toByteArray())
                    assertTrue(body.contains("TIPO_DOCUMENTO"))
                    respond(
                        content = ByteReadChannel(loadTestResource("catalog_tipo_documento_response.xml")),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "text/xml"),
                    )
                }

            val plugin = createPlugin(mockEngine)
            val entries = plugin.getCatalog("TIPO_DOCUMENTO")

            assertEquals(6, entries.size)
            assertEquals("CIF", entries[0].code)
            assertEquals("CIF", entries[0].description)
            assertEquals("CIF_E", entries[1].code)
            assertEquals("CIF extranjero", entries[1].description)
            assertEquals("PAS", entries[5].code)
            assertEquals("Pasaporte", entries[5].description)
        }

    @Test
    fun `getCatalog sends Basic Auth header`() =
        runTest {
            val mockEngine =
                MockEngine { request ->
                    val authHeader = request.headers[HttpHeaders.Authorization]
                    assertTrue(authHeader != null && authHeader.startsWith("Basic "))
                    // base64("testuser:testpass") = "dGVzdHVzZXI6dGVzdHBhc3M="
                    assertEquals("Basic dGVzdHVzZXI6dGVzdHBhc3M=", authHeader)
                    respond(
                        content = ByteReadChannel(loadTestResource("catalog_tables_response.xml")),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "text/xml"),
                    )
                }

            val plugin = createPlugin(mockEngine)
            plugin.getCatalog()
        }

    @Test
    fun `getCatalog sends correct SOAP envelope`() =
        runTest {
            val mockEngine =
                MockEngine { request ->
                    val body = String(request.body.toByteArray())
                    assertTrue(body.contains("soapenv:Envelope"))
                    assertTrue(body.contains("hospedajes.mir.es/comunicacion"))
                    assertTrue(body.contains("com:catalogoRequest"))
                    respond(
                        content = ByteReadChannel(loadTestResource("catalog_tables_response.xml")),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "text/xml"),
                    )
                }

            val plugin = createPlugin(mockEngine)
            plugin.getCatalog()
        }

    @Test
    fun `getCatalog throws on unauthorized response`() =
        runTest {
            val mockEngine =
                MockEngine {
                    respond(
                        content = ByteReadChannel("Unauthorized"),
                        status = HttpStatusCode.Unauthorized,
                    )
                }

            val plugin = createPlugin(mockEngine)
            assertFailsWith<SesHospedajesException> {
                plugin.getCatalog()
            }
        }

    @Test
    fun `getCatalog throws on server error response`() =
        runTest {
            val mockEngine =
                MockEngine {
                    respond(
                        content = ByteReadChannel("Internal Server Error"),
                        status = HttpStatusCode.InternalServerError,
                    )
                }

            val plugin = createPlugin(mockEngine)
            assertFailsWith<SesHospedajesException> {
                plugin.getCatalog()
            }
        }

    @Test
    fun `getCatalog throws on SES error code in response`() =
        runTest {
            val mockEngine =
                MockEngine {
                    respond(
                        content = ByteReadChannel(loadTestResource("catalog_error_response.xml")),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "text/xml"),
                    )
                }

            val plugin = createPlugin(mockEngine)
            val exception =
                assertFailsWith<SesHospedajesException> {
                    plugin.getCatalog("INVALID_TABLE")
                }
            assertTrue(exception.message!!.contains("10100"))
        }

    @Test
    fun `plugin uses test environment URL by default`() =
        runTest {
            val mockEngine =
                MockEngine { request ->
                    assertTrue(request.url.toString().contains("pre-ses.mir.es"))
                    respond(
                        content = ByteReadChannel(loadTestResource("catalog_tables_response.xml")),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "text/xml"),
                    )
                }

            val plugin = createPlugin(mockEngine)
            plugin.getCatalog()
        }

    @Test
    fun `plugin uses production URL when environment is production`() =
        runTest {
            val mockEngine =
                MockEngine { request ->
                    assertTrue(request.url.toString().contains("hospedajes.ses.mir.es"))
                    respond(
                        content = ByteReadChannel(loadTestResource("catalog_tables_response.xml")),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "text/xml"),
                    )
                }

            val plugin =
                SesHospedajesPlugin(
                    sesUsername = "testuser",
                    sesPassword = "testpass",
                    environment = SesEnvironment.PRODUCTION,
                    httpClient = HttpClient(mockEngine),
                )
            plugin.getCatalog()
        }

    // --- submitParteViajeros tests ---

    @Test
    fun `submitParteViajeros builds correct SOAP envelope`() =
        runTest {
            val mockEngine =
                MockEngine { request ->
                    val body = String(request.body.toByteArray())
                    assertTrue(body.contains("soapenv:Envelope"))
                    assertTrue(body.contains("com:comunicacionRequest"))
                    assertTrue(body.contains("<codigoArrendador>0000000001</codigoArrendador>"))
                    assertTrue(body.contains("<aplicacion>Kasify</aplicacion>"))
                    assertTrue(body.contains("<tipoOperacion>A</tipoOperacion>"))
                    assertTrue(body.contains("<tipoComunicacion>PV</tipoComunicacion>"))
                    assertTrue(body.contains("<solicitud>"))
                    respond(
                        content = ByteReadChannel(loadTestResource("communication_pv_response_success.xml")),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "text/xml"),
                    )
                }

            val plugin = createPlugin(mockEngine)
            plugin.submitParteViajeros(createTestRequest())
        }

    @Test
    fun `submitParteViajeros compresses and base64-encodes solicitud`() =
        runTest {
            var capturedSolicitud: String? = null

            val mockEngine =
                MockEngine { request ->
                    val body = String(request.body.toByteArray())
                    // Extract the base64 content between <solicitud> tags
                    val regex = Regex("<solicitud>([^<]+)</solicitud>")
                    capturedSolicitud = regex.find(body)?.groupValues?.get(1)
                    respond(
                        content = ByteReadChannel(loadTestResource("communication_pv_response_success.xml")),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "text/xml"),
                    )
                }

            val plugin = createPlugin(mockEngine)
            plugin.submitParteViajeros(createTestRequest())

            assertNotNull(capturedSolicitud)

            // Decode Base64
            val zipBytes = Base64.getDecoder().decode(capturedSolicitud!!)

            // Unzip and read XML
            val zipInput = ZipInputStream(ByteArrayInputStream(zipBytes))
            val entry = zipInput.nextEntry
            assertNotNull(entry)
            assertEquals("solicitud.xml", entry.name)

            val xml = zipInput.readBytes().decodeToString()

            // Verify inner XML structure
            assertTrue(xml.contains("altaParteHospedaje"))
            assertTrue(xml.contains("<codigoEstablecimiento>1234567890</codigoEstablecimiento>"))
            assertTrue(xml.contains("<referencia>BOOK-001</referencia>"))
            assertTrue(xml.contains("<fechaContrato>2026-04-15</fechaContrato>"))
            assertTrue(xml.contains("<fechaEntrada>2026-04-20T15:00:00</fechaEntrada>"))
            assertTrue(xml.contains("<fechaSalida>2026-04-25T11:00:00</fechaSalida>"))
            assertTrue(xml.contains("<numPersonas>2</numPersonas>"))
            assertTrue(xml.contains("<numHabitaciones>1</numHabitaciones>"))
            assertTrue(xml.contains("<internet>true</internet>"))
            assertTrue(xml.contains("<tipoPago>OTROS</tipoPago>"))

            // Verify guest data
            assertTrue(xml.contains("<nombre>Juan</nombre>"))
            assertTrue(xml.contains("<apellido1>Garcia</apellido1>"))
            assertTrue(xml.contains("<apellido2>Lopez</apellido2>"))
            assertTrue(xml.contains("<tipoDocumento>NIF</tipoDocumento>"))
            assertTrue(xml.contains("<numeroDocumento>12345678Z</numeroDocumento>"))
            assertTrue(xml.contains("<fechaNacimiento>1990-05-15</fechaNacimiento>"))
            assertTrue(xml.contains("<nacionalidad>ESP</nacionalidad>"))
            assertTrue(xml.contains("<sexo>H</sexo>"))
            assertTrue(xml.contains("<codigoMunicipio>28079</codigoMunicipio>"))
            assertTrue(xml.contains("<codigoPostal>28001</codigoPostal>"))
            assertTrue(xml.contains("<pais>ESP</pais>"))

            // Verify second guest (minor with parentesco)
            assertTrue(xml.contains("<nombre>Pedro</nombre>"))
            assertTrue(xml.contains("<parentesco>HJ</parentesco>"))
        }

    @Test
    fun `submitParteViajeros returns lote on success`() =
        runTest {
            val mockEngine =
                MockEngine {
                    respond(
                        content = ByteReadChannel(loadTestResource("communication_pv_response_success.xml")),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "text/xml"),
                    )
                }

            val plugin = createPlugin(mockEngine)
            val response = plugin.submitParteViajeros(createTestRequest())

            assertEquals(0, response.codigo)
            assertEquals("Operacion realizada correctamente", response.descripcion)
            assertEquals("a1b2c3d4-e5f6-7890-abcd-ef1234567890", response.lote)
            assertTrue(response.resultados.isEmpty())
        }

    @Test
    fun `submitParteViajeros returns non-zero code on SES error`() =
        runTest {
            val mockEngine =
                MockEngine {
                    respond(
                        content = ByteReadChannel(loadTestResource("communication_pv_response_error.xml")),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "text/xml"),
                    )
                }

            val plugin = createPlugin(mockEngine)
            val response = plugin.submitParteViajeros(createTestRequest())

            assertEquals(10200, response.codigo)
            assertEquals("Error en la validacion de los datos de entrada", response.descripcion)
            assertNull(response.lote)
        }

    @Test
    fun `submitParteViajeros handles per-communication results`() =
        runTest {
            val mockEngine =
                MockEngine {
                    respond(
                        content = ByteReadChannel(loadTestResource("communication_pv_response_partial.xml")),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "text/xml"),
                    )
                }

            val plugin = createPlugin(mockEngine)
            val response = plugin.submitParteViajeros(createTestRequest())

            assertEquals(0, response.codigo)
            assertNotNull(response.lote)
            assertEquals(2, response.resultados.size)

            // First result: success
            val result1 = response.resultados[0]
            assertEquals(1, result1.orden)
            assertEquals("comm-uuid-001", result1.codigoComunicacion)
            assertNull(result1.tipoError)

            // Second result: error
            val result2 = response.resultados[1]
            assertEquals(2, result2.orden)
            assertNull(result2.codigoComunicacion)
            assertEquals("VAL", result2.tipoError)
            assertEquals("Documento de identidad no valido", result2.error)
        }

    @Test
    fun `submitParteViajeros throws on HTTP error`() =
        runTest {
            val mockEngine =
                MockEngine {
                    respond(
                        content = ByteReadChannel("Unauthorized"),
                        status = HttpStatusCode.Unauthorized,
                    )
                }

            val plugin = createPlugin(mockEngine)
            assertFailsWith<SesHospedajesException> {
                plugin.submitParteViajeros(createTestRequest())
            }
        }

    @Test
    fun `submitParteViajeros escapes XML special characters in guest data`() =
        runTest {
            var capturedSolicitud: String? = null

            val mockEngine =
                MockEngine { request ->
                    val body = String(request.body.toByteArray())
                    val regex = Regex("<solicitud>([^<]+)</solicitud>")
                    capturedSolicitud = regex.find(body)?.groupValues?.get(1)
                    respond(
                        content = ByteReadChannel(loadTestResource("communication_pv_response_success.xml")),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "text/xml"),
                    )
                }

            val requestWithSpecialChars =
                SesParteViajerosRequest(
                    arrendadorCode = "0000000001",
                    establishmentCode = "1234567890",
                    contract =
                        SesContract(
                            referencia = "REF&<>\"'TEST",
                            fechaContrato = LocalDate.of(2026, 4, 15),
                            fechaEntrada = LocalDateTime.of(2026, 4, 20, 15, 0),
                            fechaSalida = LocalDateTime.of(2026, 4, 25, 11, 0),
                            numPersonas = 1,
                            numHabitaciones = null,
                            internet = null,
                            tipoPago = "OTROS",
                        ),
                    guests =
                        listOf(
                            SesGuest(
                                rol = "VI",
                                nombre = "O'Brien",
                                apellido1 = "D&G",
                                apellido2 = null,
                                tipoDocumento = "PAS",
                                numeroDocumento = "AB<123>",
                                soporteDocumento = null,
                                fechaNacimiento = LocalDate.of(1985, 3, 20),
                                nacionalidad = "GBR",
                                sexo = "H",
                                direccion =
                                    SesAddress(
                                        direccion = "10 O'Connell Street",
                                        direccionComplementaria = null,
                                        codigoMunicipio = null,
                                        nombreMunicipio = "London",
                                        codigoPostal = "SW1A 1AA",
                                        pais = "GBR",
                                    ),
                                telefono = null,
                                correo = null,
                                parentesco = null,
                            ),
                        ),
                )

            val plugin = createPlugin(mockEngine)
            plugin.submitParteViajeros(requestWithSpecialChars)

            assertNotNull(capturedSolicitud)

            val zipBytes = Base64.getDecoder().decode(capturedSolicitud!!)
            val zipInput = ZipInputStream(ByteArrayInputStream(zipBytes))
            zipInput.nextEntry
            val xml = zipInput.readBytes().decodeToString()

            // Verify XML special characters are escaped
            assertTrue(xml.contains("REF&amp;&lt;&gt;&quot;&apos;TEST"))
            assertTrue(xml.contains("O&apos;Brien"))
            assertTrue(xml.contains("D&amp;G"))
            assertTrue(xml.contains("AB&lt;123&gt;"))
            assertTrue(xml.contains("10 O&apos;Connell Street"))

            // Verify optional fields are omitted when null
            assertTrue(!xml.contains("<numHabitaciones>"))
            assertTrue(!xml.contains("<internet>"))
            assertTrue(!xml.contains("<apellido2>"))
            assertTrue(!xml.contains("<soporteDocumento>"))
            assertTrue(!xml.contains("<telefono>"))
            assertTrue(!xml.contains("<correo>"))
            assertTrue(!xml.contains("<parentesco>"))
            assertTrue(!xml.contains("<codigoMunicipio>"))

            // Verify non-Spain address uses nombreMunicipio
            assertTrue(xml.contains("<nombreMunicipio>London</nombreMunicipio>"))
        }

    // --- submitReservaHospedaje tests ---

    private fun createTestReservaRequest(): SesReservaHospedajeRequest =
        SesReservaHospedajeRequest(
            arrendadorCode = "0000000001",
            establishmentCode = "1234567890",
            contract =
                SesContract(
                    referencia = "BOOK-001",
                    fechaContrato = LocalDate.of(2026, 4, 15),
                    fechaEntrada = LocalDateTime.of(2026, 4, 20, 15, 0),
                    fechaSalida = LocalDateTime.of(2026, 4, 25, 11, 0),
                    numPersonas = 2,
                    numHabitaciones = 1,
                    internet = true,
                    tipoPago = "OTROS",
                ),
            guests =
                listOf(
                    SesReservaGuest(
                        rol = "TI",
                        nombre = "Juan",
                        apellido1 = "Garcia",
                        apellido2 = "Lopez",
                        tipoDocumento = "NIF",
                        numeroDocumento = "12345678Z",
                        fechaNacimiento = LocalDate.of(1990, 5, 15),
                        nacionalidad = "ESP",
                        sexo = "H",
                        direccion =
                            SesAddress(
                                direccion = "C/Serrano 127",
                                direccionComplementaria = "Portal A. 2B",
                                codigoMunicipio = "28079",
                                nombreMunicipio = null,
                                codigoPostal = "28001",
                                pais = "ESP",
                            ),
                        telefono = "666666666",
                        telefono2 = null,
                        correo = "juan@example.com",
                    ),
                    SesReservaGuest(
                        rol = "VI",
                        nombre = "Pedro",
                        apellido1 = "Garcia",
                        apellido2 = "Fernandez",
                        tipoDocumento = null,
                        numeroDocumento = null,
                        fechaNacimiento = null,
                        nacionalidad = null,
                        sexo = null,
                        direccion = null,
                        telefono = null,
                        telefono2 = null,
                        correo = "pedro@example.com",
                    ),
                ),
        )

    @Test
    fun `submitReservaHospedaje builds correct SOAP envelope`() =
        runTest {
            val mockEngine =
                MockEngine { request ->
                    val body = String(request.body.toByteArray())
                    assertTrue(body.contains("soapenv:Envelope"))
                    assertTrue(body.contains("com:comunicacionRequest"))
                    assertTrue(body.contains("<codigoArrendador>0000000001</codigoArrendador>"))
                    assertTrue(body.contains("<aplicacion>Kasify</aplicacion>"))
                    assertTrue(body.contains("<tipoOperacion>A</tipoOperacion>"))
                    assertTrue(body.contains("<tipoComunicacion>RH</tipoComunicacion>"))
                    assertTrue(body.contains("<solicitud>"))
                    respond(
                        content = ByteReadChannel(loadTestResource("communication_pv_response_success.xml")),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "text/xml"),
                    )
                }

            val plugin = createPlugin(mockEngine)
            plugin.submitReservaHospedaje(createTestReservaRequest())
        }

    @Test
    fun `submitReservaHospedaje compresses and base64-encodes solicitud`() =
        runTest {
            var capturedSolicitud: String? = null

            val mockEngine =
                MockEngine { request ->
                    val body = String(request.body.toByteArray())
                    val regex = Regex("<solicitud>([^<]+)</solicitud>")
                    capturedSolicitud = regex.find(body)?.groupValues?.get(1)
                    respond(
                        content = ByteReadChannel(loadTestResource("communication_pv_response_success.xml")),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "text/xml"),
                    )
                }

            val plugin = createPlugin(mockEngine)
            plugin.submitReservaHospedaje(createTestReservaRequest())

            assertNotNull(capturedSolicitud)

            val zipBytes = Base64.getDecoder().decode(capturedSolicitud!!)
            val zipInput = ZipInputStream(ByteArrayInputStream(zipBytes))
            val entry = zipInput.nextEntry
            assertNotNull(entry)
            assertEquals("solicitud.xml", entry.name)

            val xml = zipInput.readBytes().decodeToString()

            // RH-specific structure: reservation namespace + establecimiento/codigo inside comunicacion
            assertTrue(xml.contains("altaReservaHospedaje"))
            assertTrue(xml.contains("<establecimiento>"))
            assertTrue(xml.contains("<codigo>1234567890</codigo>"))
            // RH places the establishment inside comunicacion, not at solicitud level like PV
            assertTrue(!xml.contains("<codigoEstablecimiento>"))

            // Contract block (shared shape with PV)
            assertTrue(xml.contains("<referencia>BOOK-001</referencia>"))
            assertTrue(xml.contains("<fechaContrato>2026-04-15</fechaContrato>"))
            assertTrue(xml.contains("<fechaEntrada>2026-04-20T15:00:00</fechaEntrada>"))
            assertTrue(xml.contains("<fechaSalida>2026-04-25T11:00:00</fechaSalida>"))
            assertTrue(xml.contains("<numPersonas>2</numPersonas>"))
            assertTrue(xml.contains("<numHabitaciones>1</numHabitaciones>"))
            assertTrue(xml.contains("<internet>true</internet>"))
            assertTrue(xml.contains("<tipoPago>OTROS</tipoPago>"))

            // Titular with full data
            assertTrue(xml.contains("<rol>TI</rol>"))
            assertTrue(xml.contains("<nombre>Juan</nombre>"))
            assertTrue(xml.contains("<apellido1>Garcia</apellido1>"))
            assertTrue(xml.contains("<numeroDocumento>12345678Z</numeroDocumento>"))
            assertTrue(xml.contains("<fechaNacimiento>1990-05-15</fechaNacimiento>"))
            assertTrue(xml.contains("<correo>juan@example.com</correo>"))

            // personaReservaType drops these fields entirely
            assertTrue(!xml.contains("<soporteDocumento>"))
            assertTrue(!xml.contains("<parentesco>"))

            // Second guest (minimal): only contact is correo; optional blocks omitted
            assertTrue(xml.contains("<nombre>Pedro</nombre>"))
            assertTrue(xml.contains("<correo>pedro@example.com</correo>"))
        }

    @Test
    fun `submitReservaHospedaje omits optional fields when null`() =
        runTest {
            var capturedSolicitud: String? = null

            val mockEngine =
                MockEngine { request ->
                    val body = String(request.body.toByteArray())
                    val regex = Regex("<solicitud>([^<]+)</solicitud>")
                    capturedSolicitud = regex.find(body)?.groupValues?.get(1)
                    respond(
                        content = ByteReadChannel(loadTestResource("communication_pv_response_success.xml")),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "text/xml"),
                    )
                }

            val minimalRequest =
                SesReservaHospedajeRequest(
                    arrendadorCode = "0000000001",
                    establishmentCode = "1234567890",
                    contract =
                        SesContract(
                            referencia = "BOOK-002",
                            fechaContrato = LocalDate.of(2026, 4, 15),
                            fechaEntrada = LocalDateTime.of(2026, 4, 20, 0, 0),
                            fechaSalida = LocalDateTime.of(2026, 4, 25, 0, 0),
                            numPersonas = 1,
                            numHabitaciones = null,
                            internet = null,
                            tipoPago = "OTROS",
                        ),
                    guests =
                        listOf(
                            SesReservaGuest(
                                rol = "TI",
                                nombre = "O'Brien",
                                apellido1 = "D&G",
                                apellido2 = null,
                                tipoDocumento = null,
                                numeroDocumento = null,
                                fechaNacimiento = null,
                                nacionalidad = null,
                                sexo = null,
                                direccion = null,
                                telefono = "+44 20 7946 0000",
                                telefono2 = null,
                                correo = null,
                            ),
                        ),
                )

            val plugin = createPlugin(mockEngine)
            plugin.submitReservaHospedaje(minimalRequest)

            assertNotNull(capturedSolicitud)
            val zipBytes = Base64.getDecoder().decode(capturedSolicitud!!)
            val zipInput = ZipInputStream(ByteArrayInputStream(zipBytes))
            zipInput.nextEntry
            val xml = zipInput.readBytes().decodeToString()

            // XML escaping still applies
            assertTrue(xml.contains("O&apos;Brien"))
            assertTrue(xml.contains("D&amp;G"))
            assertTrue(xml.contains("<telefono>+44 20 7946 0000</telefono>"))

            // Optional contract + persona fields omitted
            assertTrue(!xml.contains("<numHabitaciones>"))
            assertTrue(!xml.contains("<internet>"))
            assertTrue(!xml.contains("<apellido2>"))
            assertTrue(!xml.contains("<tipoDocumento>"))
            assertTrue(!xml.contains("<numeroDocumento>"))
            assertTrue(!xml.contains("<fechaNacimiento>"))
            assertTrue(!xml.contains("<nacionalidad>"))
            assertTrue(!xml.contains("<sexo>"))
            assertTrue(!xml.contains("<direccion>"))
            assertTrue(!xml.contains("<telefono2>"))
            assertTrue(!xml.contains("<correo>"))
        }

    @Test
    fun `submitReservaHospedaje returns lote on success`() =
        runTest {
            val mockEngine =
                MockEngine {
                    respond(
                        content = ByteReadChannel(loadTestResource("communication_pv_response_success.xml")),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "text/xml"),
                    )
                }

            val plugin = createPlugin(mockEngine)
            val response = plugin.submitReservaHospedaje(createTestReservaRequest())

            assertEquals(0, response.codigo)
            assertEquals("Operacion realizada correctamente", response.descripcion)
            assertEquals("a1b2c3d4-e5f6-7890-abcd-ef1234567890", response.lote)
            assertTrue(response.resultados.isEmpty())
        }

    @Test
    fun `submitReservaHospedaje returns non-zero code on SES error`() =
        runTest {
            val mockEngine =
                MockEngine {
                    respond(
                        content = ByteReadChannel(loadTestResource("communication_pv_response_error.xml")),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "text/xml"),
                    )
                }

            val plugin = createPlugin(mockEngine)
            val response = plugin.submitReservaHospedaje(createTestReservaRequest())

            assertEquals(10200, response.codigo)
            assertEquals("Error en la validacion de los datos de entrada", response.descripcion)
            assertNull(response.lote)
        }

    @Test
    fun `submitReservaHospedaje throws on HTTP error`() =
        runTest {
            val mockEngine =
                MockEngine {
                    respond(
                        content = ByteReadChannel("Unauthorized"),
                        status = HttpStatusCode.Unauthorized,
                    )
                }

            val plugin = createPlugin(mockEngine)
            assertFailsWith<SesHospedajesException> {
                plugin.submitReservaHospedaje(createTestReservaRequest())
            }
        }

    // --- cancelCommunication (anulación) tests ---

    @Test
    fun `cancelCommunication builds anulación envelope with tipoOperacion B and the codigo`() =
        runTest {
            var capturedSolicitud: String? = null

            val mockEngine =
                MockEngine { request ->
                    val body = String(request.body.toByteArray())
                    assertTrue(body.contains("com:comunicacionRequest"))
                    assertTrue(body.contains("<codigoArrendador>0000000001</codigoArrendador>"))
                    assertTrue(body.contains("<aplicacion>Kasify</aplicacion>"))
                    assertTrue(body.contains("<tipoOperacion>B</tipoOperacion>"))
                    assertTrue(body.contains("<tipoComunicacion>RH</tipoComunicacion>"))
                    val regex = Regex("<solicitud>([^<]+)</solicitud>")
                    capturedSolicitud = regex.find(body)?.groupValues?.get(1)
                    respond(
                        content = ByteReadChannel(loadTestResource("anulacion_response_success.xml")),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "text/xml"),
                    )
                }

            val plugin = createPlugin(mockEngine)
            val response =
                plugin.cancelCommunication(
                    SesCancelRequest(
                        arrendadorCode = "0000000001",
                        tipoComunicacion = "RH",
                        codigoComunicacion = "RH-CODIGO-001",
                    ),
                )

            // The inner solicitud carries the anularComunicacion <comunicaciones> with the código.
            assertNotNull(capturedSolicitud)
            val zipBytes = Base64.getDecoder().decode(capturedSolicitud!!)
            val zipInput = ZipInputStream(ByteArrayInputStream(zipBytes))
            val entry = zipInput.nextEntry
            assertNotNull(entry)
            assertEquals("solicitud.xml", entry.name)
            val xml = zipInput.readBytes().decodeToString()
            assertTrue(xml.contains("anularComunicacion"))
            assertTrue(xml.contains("<codigoComunicacion>RH-CODIGO-001</codigoComunicacion>"))

            // The response is a normal comunicacionResponse carrying a lote → polled like an alta.
            assertEquals(0, response.codigo)
            assertEquals("b2c3d4e5-f6a7-8901-bcde-f23456789012", response.lote)
        }

    @Test
    fun `cancelCommunication throws on HTTP error`() =
        runTest {
            val mockEngine =
                MockEngine {
                    respond(
                        content = ByteReadChannel("Unauthorized"),
                        status = HttpStatusCode.Unauthorized,
                    )
                }

            val plugin = createPlugin(mockEngine)
            assertFailsWith<SesHospedajesException> {
                plugin.cancelCommunication(
                    SesCancelRequest(
                        arrendadorCode = "0000000001",
                        tipoComunicacion = "PV",
                        codigoComunicacion = "PV-CODIGO-001",
                    ),
                )
            }
        }

    // --- consultBatch tests ---

    @Test
    fun `consultBatch builds correct SOAP envelope`() =
        runTest {
            val mockEngine =
                MockEngine { request ->
                    val body = String(request.body.toByteArray())
                    assertTrue(body.contains("soapenv:Envelope"))
                    assertTrue(body.contains("com:consultaLoteRequest"))
                    assertTrue(body.contains("<codigosLote>"))
                    assertTrue(body.contains("<lote>a1b2c3d4-e5f6-7890-abcd-ef1234567890</lote>"))
                    assertTrue(body.contains("<lote>b2c3d4e5-f6a7-8901-bcde-f12345678901</lote>"))
                    respond(
                        content = ByteReadChannel(loadTestResource("consult_lote_response_success.xml")),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "text/xml"),
                    )
                }

            val plugin = createPlugin(mockEngine)
            plugin.consultBatch(
                listOf(
                    "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
                    "b2c3d4e5-f6a7-8901-bcde-f12345678901",
                ),
            )
        }

    @Test
    fun `consultBatch parses successful response with mixed results`() =
        runTest {
            val mockEngine =
                MockEngine {
                    respond(
                        content = ByteReadChannel(loadTestResource("consult_lote_response_success.xml")),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "text/xml"),
                    )
                }

            val plugin = createPlugin(mockEngine)
            val response = plugin.consultBatch(listOf("a1b2c3d4-e5f6-7890-abcd-ef1234567890"))

            assertEquals(0, response.codigo)
            assertEquals("Operacion realizada correctamente", response.descripcion)
            assertEquals(2, response.resultados.size)

            // First batch: PV alta with per-comunicación results
            val batch1 = response.resultados[0]
            assertEquals("a1b2c3d4-e5f6-7890-abcd-ef1234567890", batch1.lote)
            assertEquals("PV", batch1.tipoComunicacion)
            assertEquals("A", batch1.tipoOperacion)
            assertEquals(LocalDateTime.of(2026, 5, 15, 10, 0, 0), batch1.fechaPeticion)
            assertEquals(LocalDateTime.of(2026, 5, 15, 10, 0, 5), batch1.fechaProcesamiento)
            assertEquals(0, batch1.codigoEstado)
            assertEquals("Procesado correctamente", batch1.descEstado)
            assertEquals("user123", batch1.identificadorUsuario)
            assertEquals("Test User", batch1.nombreUsuario)
            assertEquals("0000000001", batch1.codigoArrendador)
            assertEquals("Kasify", batch1.aplicacion)
            assertEquals(2, batch1.comunicaciones.size)

            val comm1 = batch1.comunicaciones[0]
            assertEquals(1, comm1.orden)
            assertEquals("comm-uuid-001", comm1.codigoComunicacion)
            assertEquals(false, comm1.anulada)
            assertNull(comm1.tipoError)
            assertNull(comm1.error)

            val comm2 = batch1.comunicaciones[1]
            assertEquals(2, comm2.orden)
            assertNull(comm2.codigoComunicacion)
            assertNull(comm2.anulada)
            assertEquals("VAL", comm2.tipoError)
            assertEquals("Documento de identidad no valido", comm2.error)

            // Second batch: RH anulación without per-comunicación results
            val batch2 = response.resultados[1]
            assertEquals("b2c3d4e5-f6a7-8901-bcde-f12345678901", batch2.lote)
            assertEquals("RH", batch2.tipoComunicacion)
            assertEquals("B", batch2.tipoOperacion)
            assertTrue(batch2.comunicaciones.isEmpty())
        }

    @Test
    fun `consultBatch parses production response with communication-level validation error`() =
        runTest {
            val mockEngine =
                MockEngine {
                    respond(
                        content = ByteReadChannel(loadTestResource("consult_lote_response_with_communication_errors.xml")),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "text/xml"),
                    )
                }

            val plugin = createPlugin(mockEngine)
            val response = plugin.consultBatch(listOf("ac2e43fc-5417-11f1-aabd-00505695dcc7"))

            assertEquals(0, response.codigo)
            assertEquals("Ok", response.descripcion)
            assertEquals(1, response.resultados.size)

            val batch = response.resultados[0]
            assertEquals("ac2e43fc-5417-11f1-aabd-00505695dcc7", batch.lote)
            assertEquals("PV", batch.tipoComunicacion)
            assertEquals("A", batch.tipoOperacion)
            assertEquals(LocalDateTime.of(2026, 5, 20, 8, 46, 47), batch.fechaPeticion)
            assertEquals(LocalDateTime.of(2026, 5, 20, 8, 47, 18), batch.fechaProcesamiento)
            assertEquals(6, batch.codigoEstado)
            assertEquals("Lote tramitado pero hay errores en algunas comunicaciones", batch.descEstado)
            assertEquals("SES_USERNAME_REDACTED", batch.identificadorUsuario)
            assertEquals("JOHN DOE", batch.nombreUsuario)
            assertEquals("0000145750", batch.codigoArrendador)
            assertEquals("Kasify", batch.aplicacion)
            assertEquals(1, batch.comunicaciones.size)

            val comm = batch.comunicaciones[0]
            assertEquals(1, comm.orden)
            assertNull(comm.codigoComunicacion)
            assertNull(comm.anulada)
            assertEquals("Error validación de datos", comm.tipoError)
            assertEquals(
                "Error de validación en la comunicación [1]: [No existe un establecimiento para ese código. " +
                    "Solo puede enviar comunicaciones a sus establecimientos. Compruebe que está correctamente escrito. " +
                    "Código de establecimiento: [E12345]]",
                comm.error,
            )
        }

    @Test
    fun `consultBatch returns non-zero code on SES error`() =
        runTest {
            val mockEngine =
                MockEngine {
                    respond(
                        content = ByteReadChannel(loadTestResource("consult_lote_response_error.xml")),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "text/xml"),
                    )
                }

            val plugin = createPlugin(mockEngine)
            val response = plugin.consultBatch(listOf("does-not-exist"))

            assertEquals(10100, response.codigo)
            assertEquals("El codigo de lote no existe o no pertenece al usuario", response.descripcion)
            assertTrue(response.resultados.isEmpty())
        }

    @Test
    fun `consultBatch throws on empty list`() =
        runTest {
            val plugin = createPlugin(MockEngine { error("should not be called") })
            assertFailsWith<IllegalArgumentException> {
                plugin.consultBatch(emptyList())
            }
        }

    @Test
    fun `consultBatch throws when a lote code exceeds 36 chars`() =
        runTest {
            val plugin = createPlugin(MockEngine { error("should not be called") })
            assertFailsWith<IllegalArgumentException> {
                plugin.consultBatch(listOf("x".repeat(37)))
            }
        }

    @Test
    fun `consultBatch throws SesHospedajesUnauthorizedException on HTTP 401`() =
        runTest {
            val mockEngine =
                MockEngine {
                    respond(
                        content = ByteReadChannel("Unauthorized"),
                        status = HttpStatusCode.Unauthorized,
                    )
                }

            val plugin = createPlugin(mockEngine)
            // Catchable as the more specific subclass…
            assertFailsWith<SesHospedajesUnauthorizedException> {
                plugin.consultBatch(listOf("a1b2c3d4-e5f6-7890-abcd-ef1234567890"))
            }
            // …and still catchable as the parent for callers that don't care.
            assertFailsWith<SesHospedajesException> {
                plugin.consultBatch(listOf("a1b2c3d4-e5f6-7890-abcd-ef1234567890"))
            }
        }

    // --- verifyCredentials tests ---

    @Test
    fun `verifyCredentials returns normally when SES accepts the credentials`() =
        runTest {
            val mockEngine =
                MockEngine {
                    respond(
                        content = ByteReadChannel(loadTestResource("catalog_tables_response.xml")),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "text/xml"),
                    )
                }

            val plugin = createPlugin(mockEngine)
            plugin.verifyCredentials() // does not throw
        }

    @Test
    fun `verifyCredentials throws SesHospedajesUnauthorizedException on HTTP 401`() =
        runTest {
            val mockEngine =
                MockEngine {
                    respond(
                        content = ByteReadChannel("Unauthorized"),
                        status = HttpStatusCode.Unauthorized,
                    )
                }

            val plugin = createPlugin(mockEngine)
            assertFailsWith<SesHospedajesUnauthorizedException> {
                plugin.verifyCredentials()
            }
        }
}
