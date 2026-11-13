package eu.kasify.plugins.ses.internal

import eu.kasify.plugins.ses.SesAddress
import eu.kasify.plugins.ses.SesGuest
import eu.kasify.plugins.ses.SesParteViajerosRequest
import java.io.ByteArrayOutputStream
import java.time.format.DateTimeFormatter
import java.util.Base64
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Writes the inner `altaParteHospedaje` solicitud XML for a PV (Parte de Viajeros) comunicación
 * and packages it as a ZIP entry, Base64-encoded — the form expected by the `<solicitud>`
 * element of the SOAP envelope.
 */
internal object SesPvSolicitudWriter {
    private val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    private val DATETIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

    /** Builds the inner XML and returns it zipped + Base64-encoded, ready to embed in a SOAP body. */
    fun buildEncodedSolicitud(request: SesParteViajerosRequest): String = compressAndEncode(buildXml(request))

    /** Pure XML form, exposed for tests that want to inspect the structure before compression. */
    fun buildXml(request: SesParteViajerosRequest): String {
        val contract = request.contract
        val guestsXml = request.guests.joinToString("\n") { buildPersonaXml(it) }

        val numHabitacionesXml =
            contract.numHabitaciones?.let {
                "\n          <numHabitaciones>$it</numHabitaciones>"
            } ?: ""

        val internetXml =
            contract.internet?.let {
                "\n          <internet>$it</internet>"
            } ?: ""

        return """
            |<alt:peticion xmlns:alt="http://www.neg.hospedajes.mir.es/altaParteHospedaje">
            |  <solicitud>
            |    <codigoEstablecimiento>${SesXml.escape(request.establishmentCode)}</codigoEstablecimiento>
            |    <comunicacion>
            |      <contrato>
            |        <referencia>${SesXml.escape(contract.referencia)}</referencia>
            |        <fechaContrato>${contract.fechaContrato.format(DATE_FORMATTER)}</fechaContrato>
            |        <fechaEntrada>${contract.fechaEntrada.format(DATETIME_FORMATTER)}</fechaEntrada>
            |        <fechaSalida>${contract.fechaSalida.format(DATETIME_FORMATTER)}</fechaSalida>
            |        <numPersonas>${contract.numPersonas}</numPersonas>$numHabitacionesXml$internetXml
            |        <pago>
            |          <tipoPago>${SesXml.escape(contract.tipoPago)}</tipoPago>
            |        </pago>
            |      </contrato>
            |$guestsXml
            |    </comunicacion>
            |  </solicitud>
            |</alt:peticion>
            """.trimMargin()
    }

    private fun buildPersonaXml(guest: SesGuest): String {
        val sb = StringBuilder()
        sb.appendLine("      <persona>")
        sb.appendLine("        <rol>${SesXml.escape(guest.rol)}</rol>")
        sb.appendLine("        <nombre>${SesXml.escape(guest.nombre)}</nombre>")
        sb.appendLine("        <apellido1>${SesXml.escape(guest.apellido1)}</apellido1>")
        guest.apellido2?.let { sb.appendLine("        <apellido2>${SesXml.escape(it)}</apellido2>") }
        guest.tipoDocumento?.let { sb.appendLine("        <tipoDocumento>${SesXml.escape(it)}</tipoDocumento>") }
        guest.numeroDocumento?.let { sb.appendLine("        <numeroDocumento>${SesXml.escape(it)}</numeroDocumento>") }
        guest.soporteDocumento?.let { sb.appendLine("        <soporteDocumento>${SesXml.escape(it)}</soporteDocumento>") }
        sb.appendLine("        <fechaNacimiento>${guest.fechaNacimiento.format(DATE_FORMATTER)}</fechaNacimiento>")
        guest.nacionalidad?.let { sb.appendLine("        <nacionalidad>${SesXml.escape(it)}</nacionalidad>") }
        guest.sexo?.let { sb.appendLine("        <sexo>${SesXml.escape(it)}</sexo>") }
        sb.appendLine(buildAddressXml(guest.direccion))
        guest.telefono?.let { sb.appendLine("        <telefono>${SesXml.escape(it)}</telefono>") }
        guest.correo?.let { sb.appendLine("        <correo>${SesXml.escape(it)}</correo>") }
        guest.parentesco?.let { sb.appendLine("        <parentesco>${SesXml.escape(it)}</parentesco>") }
        sb.append("      </persona>")
        return sb.toString()
    }

    private fun buildAddressXml(address: SesAddress): String {
        val sb = StringBuilder()
        sb.appendLine("        <direccion>")
        sb.appendLine("          <direccion>${SesXml.escape(address.direccion)}</direccion>")
        address.direccionComplementaria?.let {
            sb.appendLine("          <direccionComplementaria>${SesXml.escape(it)}</direccionComplementaria>")
        }
        address.codigoMunicipio?.let {
            sb.appendLine("          <codigoMunicipio>${SesXml.escape(it)}</codigoMunicipio>")
        }
        address.nombreMunicipio?.let {
            sb.appendLine("          <nombreMunicipio>${SesXml.escape(it)}</nombreMunicipio>")
        }
        sb.appendLine("          <codigoPostal>${SesXml.escape(address.codigoPostal)}</codigoPostal>")
        sb.appendLine("          <pais>${SesXml.escape(address.pais)}</pais>")
        sb.append("        </direccion>")
        return sb.toString()
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
