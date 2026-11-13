package eu.kasify.plugins.ses.internal

import eu.kasify.plugins.ses.SesBatchInfo
import eu.kasify.plugins.ses.SesBatchQueryResponse
import eu.kasify.plugins.ses.SesCatalogEntry
import eu.kasify.plugins.ses.SesCommunicationResponse
import eu.kasify.plugins.ses.SesCommunicationResult
import eu.kasify.plugins.ses.SesHospedajesException
import org.w3c.dom.Element

/**
 * Parsers for the three SES SOAP response shapes used by the plugin.
 *
 * Each function is a pure transformation `String → DTO` so callers can wrap them in their own
 * logging / MDC context without the parser needing to know anything about the operation it
 * serves.
 */
internal object SesResponseParsers {
    fun parseCatalog(xml: String): List<SesCatalogEntry> {
        val document = SesXml.parse(xml)

        val resultCodeNodes = document.getElementsByTagNameNS("*", "codigo")
        if (resultCodeNodes.length > 0) {
            val resultCode = resultCodeNodes.item(0).textContent.trim()
            if (resultCode != "0") {
                val descriptionNodes = document.getElementsByTagNameNS("*", "descripcion")
                val errorDescription =
                    if (descriptionNodes.length > 0) descriptionNodes.item(0).textContent.trim() else "Unknown error"
                throw SesHospedajesException("SES catalog error (code $resultCode): $errorDescription")
            }
        }

        val tuplaNodes = document.getElementsByTagNameNS("*", "tupla")
        val entries = mutableListOf<SesCatalogEntry>()
        for (i in 0 until tuplaNodes.length) {
            val tupla = tuplaNodes.item(i) as Element
            val code = SesXml.childText(tupla, "codigo")
            val description = SesXml.childText(tupla, "descripcion")
            if (code != null && description != null) {
                entries.add(SesCatalogEntry(code = code, description = description))
            }
        }
        return entries
    }

    fun parseComunicacion(xml: String): SesCommunicationResponse {
        val document = SesXml.parse(xml)

        val respuestaNodes = document.getElementsByTagNameNS("*", "respuesta")
        var codigo = -1
        var descripcion = "Unknown"
        var lote: String? = null

        if (respuestaNodes.length > 0) {
            val respuesta = respuestaNodes.item(0) as Element
            codigo = SesXml.childText(respuesta, "codigo")?.toIntOrNull() ?: -1
            descripcion = SesXml.childText(respuesta, "descripcion") ?: "Unknown"
            lote = SesXml.childText(respuesta, "lote")
        }

        val resultados = mutableListOf<SesCommunicationResult>()
        val resultadoNodes = document.getElementsByTagNameNS("*", "resultadoComunicacion")
        for (i in 0 until resultadoNodes.length) {
            val rc = resultadoNodes.item(i) as Element
            resultados.add(
                SesCommunicationResult(
                    orden = SesXml.childText(rc, "orden")?.toIntOrNull() ?: i,
                    codigoComunicacion = SesXml.childText(rc, "codigoComunicacion"),
                    tipoError = SesXml.childText(rc, "tipoError"),
                    error = SesXml.childText(rc, "error"),
                    anulada = SesXml.childText(rc, "anulada")?.toBooleanStrictOrNull(),
                ),
            )
        }

        return SesCommunicationResponse(
            codigo = codigo,
            descripcion = descripcion,
            lote = lote,
            resultados = resultados,
            rawXml = xml,
        )
    }

    fun parseConsultaLote(xml: String): SesBatchQueryResponse {
        val document = SesXml.parse(xml)

        var codigo = -1
        var descripcion = "Unknown"
        val respuestaNodes = document.getElementsByTagNameNS("*", "respuesta")
        if (respuestaNodes.length > 0) {
            val respuesta = respuestaNodes.item(0) as Element
            codigo = SesXml.childText(respuesta, "codigo")?.toIntOrNull() ?: -1
            descripcion = SesXml.childText(respuesta, "descripcion") ?: "Unknown"
        }

        val resultadoNodes = document.getElementsByTagNameNS("*", "resultado")
        val batches = mutableListOf<SesBatchInfo>()
        for (i in 0 until resultadoNodes.length) {
            val node = resultadoNodes.item(i) as Element

            val lote = SesXml.childText(node, "lote") ?: continue
            val fechaPeticion = SesXml.childText(node, "fechaPeticion")?.let(SesXml::parseDateTime) ?: continue
            val fechaProcesamiento = SesXml.childText(node, "fechaProcesamiento")?.let(SesXml::parseDateTime) ?: continue

            val comunicaciones = mutableListOf<SesCommunicationResult>()
            val perComm = node.getElementsByTagNameNS("*", "resultadoComunicacion")
            for (j in 0 until perComm.length) {
                val rc = perComm.item(j) as Element
                comunicaciones.add(
                    SesCommunicationResult(
                        orden = SesXml.childText(rc, "orden")?.toIntOrNull() ?: j,
                        codigoComunicacion = SesXml.childText(rc, "codigoComunicacion"),
                        tipoError = SesXml.childText(rc, "tipoError"),
                        error = SesXml.childText(rc, "error"),
                        anulada = SesXml.childText(rc, "anulada")?.toBooleanStrictOrNull(),
                    ),
                )
            }

            batches.add(
                SesBatchInfo(
                    lote = lote,
                    tipoComunicacion = SesXml.childText(node, "tipoComunicacion") ?: "",
                    tipoOperacion = SesXml.childText(node, "tipoOperacion") ?: "",
                    fechaPeticion = fechaPeticion,
                    fechaProcesamiento = fechaProcesamiento,
                    codigoEstado = SesXml.childText(node, "codigoEstado")?.toIntOrNull() ?: -1,
                    descEstado = SesXml.childText(node, "descEstado") ?: "",
                    identificadorUsuario = SesXml.childText(node, "identificadorUsuario") ?: "",
                    nombreUsuario = SesXml.childText(node, "nombreUsuario") ?: "",
                    codigoArrendador = SesXml.childText(node, "codigoArrendador") ?: "",
                    aplicacion = SesXml.childText(node, "aplicacion") ?: "",
                    comunicaciones = comunicaciones,
                ),
            )
        }

        return SesBatchQueryResponse(
            codigo = codigo,
            descripcion = descripcion,
            resultados = batches,
            rawXml = xml,
        )
    }
}
