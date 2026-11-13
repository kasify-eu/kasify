package eu.kasify.sesmock

import eu.kasify.plugins.ses.internal.SesXml
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Base64
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipInputStream

/**
 * Local mock of the SES Hospedajes SOAP endpoint (`hospedajes.ses.mir.es`), for use when the
 * official pre-production server is unavailable. Speaks the same wire protocol the production
 * client (`plugins/ses/`) uses: catalogo, comunicación alta/anulación (PV + RH) and consultaLote,
 * including the async lote lifecycle (pendiente → en proceso → tramitado) and the rule that
 * `codigoComunicacion` is only ever returned by consultaLote.
 *
 * Run with `./gradlew runSesMock` and point the API at it with
 * `SES_ENDPOINT_URL=http://localhost:8888/hospedajes-web/ws/v1/comunicacion`.
 * Full usage: see the "SES Hospedajes mock server" section of `api/README.md`.
 */

private val logger = KotlinLogging.logger {}

private const val COMUNICACION_NS = "http://www.soap.servicios.hospedajes.mir.es/comunicacion"

private val FECHA_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

/** Keywords that, inside a persona nombre/apellido, flip the lote to estado 6 at poll time. */
private val NAME_TRIGGER_KEYWORDS = listOf("BOOM", "PARTIAL", "E-UNKNOWN")

/**
 * Layer-1 códigos the `E<código>` establishment-code trigger can force synchronously, with the
 * real spec descriptions (v3.1.3 §5). Restricted to this map on purpose: a realistic-looking
 * establishment code such as `E12345` must never trigger accidentally. In real SES these códigos
 * come from the arrendador/credential/portal config, not the establishment code — the establishment
 * code is just a convenient per-property trigger vehicle; the wire behavior (non-zero código in an
 * HTTP 200 body, nothing queued) matches production exactly.
 */
private val LAYER1_DESCRIPTIONS =
    mapOf(
        10103 to "El código de arrendador no existe en el sistema",
        10107 to "Usuario incorrecto",
        10119 to "El arrendador no puede realizar ese tipo de comunicaciones",
        10120 to "El arrendador no tiene habilitada la opción de realizar operaciones a través del servicio web",
        10121 to "Error de validación",
        10999 to "Error no controlado",
    )

private val DESC_ESTADO =
    mapOf(
        1 to "Lote tramitado sin errores",
        2 to "Lote con errores en la cabecera o formato de la solicitud",
        3 to "Error inesperado",
        4 to "En proceso",
        5 to "Pendiente",
        6 to "Lote tramitado pero hay errores en algunas comunicaciones",
    )

private data class MockConfig(
    val port: Int,
    val username: String,
    val password: String,
    val processSeconds: Long,
) {
    val expectedAuthHeader: String =
        "Basic " + Base64.getEncoder().encodeToString("$username:$password".toByteArray())

    companion object {
        fun fromEnv(): MockConfig {
            val instant = System.getenv("SES_MOCK_INSTANT") == "1"
            return MockConfig(
                port = System.getenv("SES_MOCK_PORT")?.toIntOrNull() ?: 8888,
                username = System.getenv("SES_MOCK_USER") ?: "test",
                password = System.getenv("SES_MOCK_PASS") ?: "test",
                processSeconds = if (instant) 0 else System.getenv("SES_MOCK_PROCESS_SECONDS")?.toLongOrNull() ?: 10,
            )
        }
    }
}

private data class MockResult(
    val orden: Int,
    val codigoComunicacion: String? = null,
    val tipoError: String? = null,
    val error: String? = null,
    val anulada: Boolean? = null,
)

private class MockLote(
    val lote: String,
    val tipoComunicacion: String,
    val tipoOperacion: String,
    val codigoArrendador: String,
    val aplicacion: String,
    val createdAt: Instant,
    val solicitudXml: String,
    val referencia: String?,
    val establecimiento: String?,
    val personaNames: List<String> = emptyList(),
    val codigosToAnnul: List<String> = emptyList(),
) {
    @Volatile var codigoEstado: Int = 5

    @Volatile var processedAt: Instant? = null

    @Volatile var resultados: List<MockResult> = emptyList()

    val descEstado: String get() = DESC_ESTADO.getValue(codigoEstado)

    fun isTerminal(): Boolean = codigoEstado !in setOf(4, 5)
}

private class CodigoEntry(
    val tipoComunicacion: String,
) {
    @Volatile var anulada: Boolean = false
}

private class MockState(
    private val processSeconds: Long,
) {
    val lotes = ConcurrentHashMap<String, MockLote>()
    val codigos = ConcurrentHashMap<String, CodigoEntry>()

    // Requests rejected at the door with código 10107 (the `badcreds` magic username). Like real
    // SES, nothing is queued so there is no lote — but the counter keeps the request visible in
    // /__admin/state, otherwise the documented debugging loop looks like requests never arrived.
    val badCredsRejections =
        java.util.concurrent.atomic
            .AtomicInteger(0)

    fun reset() {
        lotes.clear()
        codigos.clear()
        badCredsRejections.set(0)
    }

    /** Advance the lote's lifecycle based on elapsed time and its scenario triggers. */
    fun refresh(
        lote: MockLote,
        now: Instant = Instant.now(),
    ) {
        if (lote.isTerminal()) return
        synchronized(lote) {
            if (lote.isTerminal()) return
            val elapsed = Duration.between(lote.createdAt, now).seconds
            val stuck = lote.referencia?.contains("STUCK") == true
            when {
                elapsed >= processSeconds && !stuck -> finalize(lote, now)
                stuck || elapsed >= processSeconds / 2 -> lote.codigoEstado = 4
                else -> lote.codigoEstado = 5
            }
        }
    }

    private fun finalize(
        lote: MockLote,
        now: Instant,
    ) {
        lote.processedAt = now
        when (lote.tipoOperacion) {
            "A" -> finalizeAlta(lote)
            "B" -> finalizeAnulacion(lote)
        }
        logger.info { "Lote ${lote.lote} (${lote.tipoComunicacion}/${lote.tipoOperacion}) processed → estado ${lote.codigoEstado}" }
    }

    /**
     * A persona nombre/apellido containing any trigger keyword (case-insensitive) turns the lote
     * into estado 6 with a per-comm data-validation error. Guest names are editable from the
     * dashboard, so this simulates the full "rejected → fix the guest data → resubmit" loop
     * without touching the booking's channel_id.
     */
    private fun triggeringPersonaName(lote: MockLote): String? =
        lote.personaNames.firstOrNull { name ->
            NAME_TRIGGER_KEYWORDS.any { keyword -> name.uppercase().contains(keyword) }
        }

    private fun finalizeAlta(lote: MockLote) {
        val referencia = lote.referencia.orEmpty()
        when {
            referencia.contains("BOOM") -> lote.codigoEstado = 3
            lote.establecimiento == "E-UNKNOWN" -> {
                lote.codigoEstado = 6
                lote.resultados =
                    listOf(
                        MockResult(
                            orden = 1,
                            tipoError = "Error validación de datos",
                            error =
                                "Error de validación en la comunicación [1]: [No existe un establecimiento para ese " +
                                    "código. Solo puede enviar comunicaciones a sus establecimientos. Compruebe que " +
                                    "está correctamente escrito. Código de establecimiento: [${lote.establecimiento}]]",
                        ),
                    )
            }
            referencia.contains("PARTIAL") -> {
                lote.codigoEstado = 6
                lote.resultados =
                    listOf(
                        MockResult(
                            orden = 1,
                            tipoError = "Error validación de datos",
                            error =
                                "Error de validación en la comunicación [1]: [Simulación de error parcial " +
                                    "(la referencia contiene PARTIAL)]",
                        ),
                    )
            }
            triggeringPersonaName(lote) != null -> {
                val name = triggeringPersonaName(lote)
                lote.codigoEstado = 6
                lote.resultados =
                    listOf(
                        MockResult(
                            orden = 1,
                            tipoError = "Error validación de datos",
                            error =
                                "Error de validación en la comunicación [1]: [Datos de la persona no válidos: " +
                                    "el nombre o apellido [$name] contiene una palabra de simulación de error " +
                                    "(${NAME_TRIGGER_KEYWORDS.joinToString("/")})]",
                        ),
                    )
            }
            else -> {
                val codigo = "${lote.tipoComunicacion}-${UUID.randomUUID().toString().take(8).uppercase()}"
                codigos[codigo] = CodigoEntry(lote.tipoComunicacion)
                lote.codigoEstado = 1
                lote.resultados = listOf(MockResult(orden = 1, codigoComunicacion = codigo))
            }
        }
    }

    private fun finalizeAnulacion(lote: MockLote) {
        val results =
            lote.codigosToAnnul.mapIndexed { index, codigo ->
                val entry = codigos[codigo]
                when {
                    entry == null ->
                        MockResult(
                            orden = index + 1,
                            tipoError = "Error validación de datos",
                            error = "No existe una comunicación con el código [$codigo]",
                        )
                    entry.tipoComunicacion != lote.tipoComunicacion ->
                        MockResult(
                            orden = index + 1,
                            tipoError = "Error validación de datos",
                            error =
                                "El tipo de comunicación [${lote.tipoComunicacion}] no coincide con el de la " +
                                    "comunicación [$codigo] (${entry.tipoComunicacion})",
                        )
                    entry.anulada ->
                        MockResult(
                            orden = index + 1,
                            tipoError = "Error validación de datos",
                            error = "La comunicación [$codigo] ya está anulada",
                        )
                    else -> {
                        entry.anulada = true
                        MockResult(orden = index + 1, codigoComunicacion = codigo, anulada = true)
                    }
                }
            }
        lote.resultados = results
        lote.codigoEstado = if (results.all { it.error == null }) 1 else 6
    }
}

// --- SOAP request handling -----------------------------------------------------------------

private fun firstElement(
    doc: Document,
    localName: String,
): Element? = doc.getElementsByTagNameNS("*", localName).item(0) as Element?

private fun allTexts(
    doc: Document,
    localName: String,
): List<String> {
    val nodes = doc.getElementsByTagNameNS("*", localName)
    return (0 until nodes.length).map { nodes.item(it).textContent.trim() }
}

private fun decodeSolicitud(encoded: String): String {
    val bytes = Base64.getDecoder().decode(encoded.trim())
    ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
        requireNotNull(zip.nextEntry) { "solicitud ZIP is empty" }
        return zip.readBytes().decodeToString()
    }
}

private fun handleComunicacion(
    state: MockState,
    doc: Document,
): String {
    val cabecera =
        firstElement(doc, "cabecera")
            ?: return comunicacionErrorXml(101, "Falta la cabecera de la petición")
    val codigoArrendador = SesXml.childText(cabecera, "codigoArrendador")
    val aplicacion = SesXml.childText(cabecera, "aplicacion")
    val tipoOperacion = SesXml.childText(cabecera, "tipoOperacion")
    val tipoComunicacion = SesXml.childText(cabecera, "tipoComunicacion")

    if (codigoArrendador.isNullOrBlank() || aplicacion.isNullOrBlank()) {
        return comunicacionErrorXml(101, "Faltan codigoArrendador o aplicacion en la cabecera")
    }
    if (tipoOperacion !in setOf("A", "B")) {
        return comunicacionErrorXml(101, "tipoOperacion no soportado por el mock: [$tipoOperacion] (esperado A o B)")
    }
    if (tipoComunicacion !in setOf("PV", "RH")) {
        return comunicacionErrorXml(101, "tipoComunicacion no soportado por el mock: [$tipoComunicacion] (esperado PV o RH)")
    }

    val encoded =
        firstElement(doc, "solicitud")?.textContent?.trim()?.takeIf { it.isNotBlank() }
            ?: return comunicacionErrorXml(103, "Falta el elemento solicitud")
    val solicitudXml =
        try {
            decodeSolicitud(encoded)
        } catch (e: Exception) {
            return comunicacionErrorXml(103, "No se pudo decodificar la solicitud (Base64+ZIP): ${e.message}")
        }
    val inner =
        try {
            SesXml.parse(solicitudXml)
        } catch (e: Exception) {
            return comunicacionErrorXml(103, "La solicitud no es XML válido: ${e.message}")
        }

    return if (tipoOperacion == "A") {
        handleAlta(state, inner, solicitudXml, codigoArrendador, aplicacion, tipoComunicacion!!)
    } else {
        handleAnulacion(state, inner, solicitudXml, codigoArrendador, aplicacion, tipoComunicacion!!)
    }
}

private fun handleAlta(
    state: MockState,
    inner: Document,
    solicitudXml: String,
    codigoArrendador: String,
    aplicacion: String,
    tipoComunicacion: String,
): String {
    val establecimiento = firstElement(inner, "codigoEstablecimiento")?.textContent?.trim()
    if (establecimiento.isNullOrBlank()) {
        return comunicacionErrorXml(104, "La solicitud no contiene codigoEstablecimiento")
    }
    if (inner.getElementsByTagNameNS("*", "persona").length == 0) {
        return comunicacionErrorXml(104, "La solicitud no contiene ninguna persona")
    }
    val referencia = firstElement(inner, "referencia")?.textContent?.trim()
    if (referencia?.contains("REJECT") == true) {
        return comunicacionErrorXml(102, "Comunicación rechazada por el mock (la referencia contiene REJECT)")
    }

    // Magic establishment code E<código> (e.g. E10103): return that Layer-1 código synchronously,
    // like real SES does for arrendador/credential/config errors. Only códigos present in
    // LAYER1_DESCRIPTIONS fire — any other E-prefixed value is a normal establishment code.
    val forcedCodigo =
        Regex("""^E(\d{5})$""")
            .matchEntire(establecimiento)
            ?.groupValues
            ?.get(1)
            ?.toInt()
            ?.takeIf { it in LAYER1_DESCRIPTIONS }
    if (forcedCodigo != null) {
        logger.info { "Returning código $forcedCodigo (establishment code $establecimiento)" }
        return comunicacionErrorXml(forcedCodigo, LAYER1_DESCRIPTIONS.getValue(forcedCodigo))
    }

    val lote =
        MockLote(
            lote = UUID.randomUUID().toString(),
            tipoComunicacion = tipoComunicacion,
            tipoOperacion = "A",
            codigoArrendador = codigoArrendador,
            aplicacion = aplicacion,
            createdAt = Instant.now(),
            solicitudXml = solicitudXml,
            referencia = referencia,
            establecimiento = establecimiento,
            personaNames = allTexts(inner, "nombre") + allTexts(inner, "apellido1") + allTexts(inner, "apellido2"),
        )
    state.lotes[lote.lote] = lote
    logger.info {
        "Alta $tipoComunicacion accepted: lote=${lote.lote} referencia=$referencia establecimiento=$establecimiento"
    }
    return comunicacionOkXml(lote.lote)
}

private fun handleAnulacion(
    state: MockState,
    inner: Document,
    solicitudXml: String,
    codigoArrendador: String,
    aplicacion: String,
    tipoComunicacion: String,
): String {
    val codigosToAnnul = allTexts(inner, "codigoComunicacion").filter { it.isNotBlank() }
    if (codigosToAnnul.isEmpty()) {
        return comunicacionErrorXml(104, "La solicitud de anulación no contiene ningún codigoComunicacion")
    }
    val lote =
        MockLote(
            lote = UUID.randomUUID().toString(),
            tipoComunicacion = tipoComunicacion,
            tipoOperacion = "B",
            codigoArrendador = codigoArrendador,
            aplicacion = aplicacion,
            createdAt = Instant.now(),
            solicitudXml = solicitudXml,
            referencia = null,
            establecimiento = null,
            codigosToAnnul = codigosToAnnul,
        )
    state.lotes[lote.lote] = lote
    logger.info { "Anulación $tipoComunicacion accepted: lote=${lote.lote} codigos=$codigosToAnnul" }
    return comunicacionOkXml(lote.lote)
}

private fun handleConsulta(
    state: MockState,
    doc: Document,
    usuario: String,
): String {
    val requested = allTexts(doc, "lote").filter { it.isNotBlank() }
    val known = requested.mapNotNull { state.lotes[it] }
    known.forEach { state.refresh(it) }
    logger.info { "Consulta de ${requested.size} lote(s), ${known.size} conocido(s)" }
    return consultaOkXml(known, usuario)
}

// --- SOAP response builders ----------------------------------------------------------------

private fun soapBody(inner: String): String =
    """
    |<?xml version="1.0" encoding="UTF-8" standalone="no"?>
    |<SOAP-ENV:Envelope xmlns:SOAP-ENV="http://schemas.xmlsoap.org/soap/envelope/">
    |<SOAP-ENV:Header/>
    |<SOAP-ENV:Body>
    |$inner
    |</SOAP-ENV:Body>
    |</SOAP-ENV:Envelope>
    """.trimMargin()

private fun comunicacionOkXml(lote: String): String =
    soapBody(
        """
        |<ns3:comunicacionResponse xmlns:ns3="$COMUNICACION_NS">
        |<respuesta>
        |<codigo>0</codigo>
        |<descripcion>Ok</descripcion>
        |<lote>${SesXml.escape(lote)}</lote>
        |</respuesta>
        |</ns3:comunicacionResponse>
        """.trimMargin(),
    )

private fun comunicacionErrorXml(
    codigo: Int,
    descripcion: String,
): String =
    soapBody(
        """
        |<ns3:comunicacionResponse xmlns:ns3="$COMUNICACION_NS">
        |<respuesta>
        |<codigo>$codigo</codigo>
        |<descripcion>${SesXml.escape(descripcion)}</descripcion>
        |</respuesta>
        |</ns3:comunicacionResponse>
        """.trimMargin(),
    )

private fun catalogoOkXml(): String {
    val tuplas =
        listOf("NIF" to "DNI/NIF", "NIE" to "NIE", "PAS" to "Pasaporte", "OTRO" to "Otro documento")
            .joinToString("\n") { (code, desc) ->
                "<tupla><codigo>$code</codigo><descripcion>$desc</descripcion></tupla>"
            }
    return soapBody(
        """
        |<ns3:catalogoResponse xmlns:ns3="$COMUNICACION_NS">
        |<respuesta>
        |<codigo>0</codigo>
        |<descripcion>Ok</descripcion>
        |</respuesta>
        |<catalogo>
        |$tuplas
        |</catalogo>
        |</ns3:catalogoResponse>
        """.trimMargin(),
    )
}

private fun consultaOkXml(
    lotes: List<MockLote>,
    usuario: String,
): String {
    val resultados = lotes.joinToString("\n") { resultadoXml(it, usuario) }
    return soapBody(
        """
        |<ns3:consultaLoteResponse xmlns:ns3="$COMUNICACION_NS">
        |<respuesta>
        |<codigo>0</codigo>
        |<descripcion>Ok</descripcion>
        |</respuesta>
        |$resultados
        |</ns3:consultaLoteResponse>
        """.trimMargin(),
    )
}

private fun formatFecha(instant: Instant): String = LocalDateTime.ofInstant(instant, ZoneId.systemDefault()).format(FECHA_FORMAT)

private fun resultadoXml(
    lote: MockLote,
    usuario: String,
): String {
    val comunicaciones =
        if (lote.resultados.isEmpty()) {
            ""
        } else {
            val entries =
                lote.resultados.joinToString("\n") { r ->
                    buildString {
                        appendLine("<resultadoComunicacion>")
                        appendLine("<orden>${r.orden}</orden>")
                        r.codigoComunicacion?.let { appendLine("<codigoComunicacion>${SesXml.escape(it)}</codigoComunicacion>") }
                        r.tipoError?.let { appendLine("<tipoError>${SesXml.escape(it)}</tipoError>") }
                        r.error?.let { appendLine("<error>${SesXml.escape(it)}</error>") }
                        r.anulada?.let { appendLine("<anulada>$it</anulada>") }
                        append("</resultadoComunicacion>")
                    }
                }
            "\n<resultadoComunicaciones>\n$entries\n</resultadoComunicaciones>"
        }
    return """
        |<resultado>
        |<lote>${SesXml.escape(lote.lote)}</lote>
        |<tipoComunicacion>${lote.tipoComunicacion}</tipoComunicacion>
        |<tipoOperacion>${lote.tipoOperacion}</tipoOperacion>
        |<fechaPeticion>${formatFecha(lote.createdAt)}</fechaPeticion>
        |<fechaProcesamiento>${formatFecha(lote.processedAt ?: lote.createdAt)}</fechaProcesamiento>
        |<codigoEstado>${lote.codigoEstado}</codigoEstado>
        |<descEstado>${SesXml.escape(lote.descEstado)}</descEstado>
        |<identificadorUsuario>${SesXml.escape(usuario)}</identificadorUsuario>
        |<nombreUsuario>SES MOCK</nombreUsuario>
        |<codigoArrendador>${SesXml.escape(lote.codigoArrendador)}</codigoArrendador>
        |<aplicacion>${SesXml.escape(lote.aplicacion)}</aplicacion>$comunicaciones
        |</resultado>
        """.trimMargin()
}

// --- Admin / server wiring -----------------------------------------------------------------

private fun stateJson(state: MockState): String =
    buildJsonObject {
        put(
            "lotes",
            buildJsonArray {
                state.lotes.values.sortedBy { it.createdAt }.forEach { lote ->
                    add(
                        buildJsonObject {
                            put("lote", lote.lote)
                            put("tipoComunicacion", lote.tipoComunicacion)
                            put("tipoOperacion", lote.tipoOperacion)
                            put("codigoArrendador", lote.codigoArrendador)
                            put("referencia", lote.referencia)
                            put("establecimiento", lote.establecimiento)
                            put("codigoEstado", lote.codigoEstado)
                            put("descEstado", lote.descEstado)
                            put("createdAt", lote.createdAt.toString())
                            put("processedAt", lote.processedAt?.toString())
                            put(
                                "resultados",
                                buildJsonArray {
                                    lote.resultados.forEach { r ->
                                        add(
                                            buildJsonObject {
                                                put("orden", r.orden)
                                                put("codigoComunicacion", r.codigoComunicacion)
                                                put("tipoError", r.tipoError)
                                                put("error", r.error)
                                                put("anulada", r.anulada)
                                            },
                                        )
                                    }
                                },
                            )
                            put("solicitudXml", lote.solicitudXml)
                        },
                    )
                }
            },
        )
        put(
            "codigos",
            buildJsonObject {
                state.codigos.forEach { (codigo, entry) ->
                    put(
                        codigo,
                        buildJsonObject {
                            put("tipoComunicacion", entry.tipoComunicacion)
                            put("anulada", entry.anulada)
                        },
                    )
                }
            },
        )
        // Door-level 10107 rejections (`badcreds` username) — no lote exists for these, like real
        // SES, so the counter is the only trace that the requests arrived.
        put("badCredsRejections", state.badCredsRejections.get())
    }.toString()

fun main() {
    val config = MockConfig.fromEnv()

    logger.info {
        "SES mock server starting on port ${config.port} " +
            "(user=${config.username}, processSeconds=${config.processSeconds})"
    }
    logger.info {
        "Point the API at it with SES_ENDPOINT_URL=http://localhost:${config.port}/hospedajes-web/ws/v1/comunicacion"
    }

    sesMockServer(config).start(wait = true)
}

/**
 * Start the mock in-process (for tests). `port = 0` picks a free port — read it back with
 * `server.engine.resolvedConnectors().first().port` after `start(wait = false)`.
 * `processSeconds = 0` makes lotes reach their terminal estado on the first poll.
 */
internal fun startSesMock(
    port: Int = 0,
    username: String = "test",
    password: String = "test",
    processSeconds: Long = 0,
) = sesMockServer(MockConfig(port, username, password, processSeconds))

private fun sesMockServer(config: MockConfig): EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration> {
    val state = MockState(config.processSeconds)
    return embeddedServer(Netty, port = config.port) {
        routing {
            get("/__admin/state") {
                call.respondText(stateJson(state), ContentType.Application.Json)
            }
            post("/__admin/reset") {
                state.reset()
                logger.info { "State reset" }
                call.respondText("""{"reset":true}""", ContentType.Application.Json)
            }
            post("/{path...}") {
                val forcedStatus = call.request.headers["X-Mock-Http"]?.toIntOrNull()
                if (forcedStatus != null) {
                    call.respondText("Forced HTTP $forcedStatus (X-Mock-Http)", status = HttpStatusCode.fromValue(forcedStatus))
                    return@post
                }
                // Magic credentials: a WS username containing "badcreds" reproduces the real 10107
                // "Usuario incorrecto" — SES returns it in an HTTP 200 body, not as a 401. Handy to
                // exercise the credential-error pause (integration flag + owner email) end to end.
                // Catalogo still succeeds for it so the integration can be saved through the
                // dashboard (the handler pings catalogo before persisting credentials).
                val badCredsUser =
                    call.request.headers[HttpHeaders.Authorization]
                        ?.removePrefix("Basic ")
                        ?.let { runCatching { String(Base64.getDecoder().decode(it)) }.getOrNull() }
                        ?.substringBefore(':')
                        ?.contains("badcreds", ignoreCase = true) == true
                if (!badCredsUser && call.request.headers[HttpHeaders.Authorization] != config.expectedAuthHeader) {
                    logger.warn { "Rejected request with bad/missing Basic auth" }
                    call.respondText("Unauthorized", status = HttpStatusCode.Unauthorized)
                    return@post
                }

                val body = call.receiveText()
                val doc =
                    try {
                        SesXml.parse(body)
                    } catch (e: Exception) {
                        logger.warn { "Unparseable SOAP request: ${e.message}" }
                        call.respondText("Malformed XML", status = HttpStatusCode.BadRequest)
                        return@post
                    }

                val responseXml =
                    when {
                        firstElement(doc, "catalogoRequest") != null -> catalogoOkXml()
                        badCredsUser -> {
                            val total = state.badCredsRejections.incrementAndGet()
                            logger.info { "Returning código 10107 (username contains 'badcreds'), rejection #$total" }
                            comunicacionErrorXml(10107, "Usuario incorrecto")
                        }
                        firstElement(doc, "comunicacionRequest") != null -> handleComunicacion(state, doc)
                        firstElement(doc, "consultaLoteRequest") != null -> handleConsulta(state, doc, config.username)
                        else -> {
                            logger.warn { "Unknown SOAP operation, root=${doc.documentElement?.localName}" }
                            call.respondText("Unknown SOAP operation", status = HttpStatusCode.InternalServerError)
                            return@post
                        }
                    }
                call.respondText(responseXml, ContentType.Text.Xml)
            }
        }
    }
}
