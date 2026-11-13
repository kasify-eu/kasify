package eu.kasify.plugins.ses.internal

import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Writes the inner `anularComunicacion` solicitud XML — the `<comunicaciones>` element carrying one
 * or more `<codigoComunicacion>` (the códigos of the altas to annul) — and packages it as a ZIP
 * entry, Base64-encoded, exactly like [SesPvSolicitudWriter] does for an alta.
 *
 * Schema: `anularComunicacion.xsd`, published by the Spanish Ministry of the Interior (SES Hospedajes)
 * (namespace `http://www.neg.hospedajes.mir.es/anularComunicacion`).
 */
internal object SesAnulacionSolicitudWriter {
    /** Builds the inner XML and returns it zipped + Base64-encoded, ready to embed in a SOAP body. */
    fun buildEncodedSolicitud(codigosComunicacion: List<String>): String = compressAndEncode(buildXml(codigosComunicacion))

    /** Pure XML form, exposed for tests that want to inspect the structure before compression. */
    fun buildXml(codigosComunicacion: List<String>): String {
        require(codigosComunicacion.isNotEmpty()) { "At least one codigoComunicacion is required to anular" }
        val codigosXml =
            codigosComunicacion.joinToString("\n") {
                "  <codigoComunicacion>${SesXml.escape(it)}</codigoComunicacion>"
            }
        return """
            |<comunicaciones xmlns="http://www.neg.hospedajes.mir.es/anularComunicacion">
            |$codigosXml
            |</comunicaciones>
            """.trimMargin()
    }

    private fun compressAndEncode(xml: String): String {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zip ->
            zip.putNextEntry(ZipEntry("solicitud.xml"))
            zip.write(xml.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
        return Base64.getEncoder().encodeToString(baos.toByteArray())
    }
}
