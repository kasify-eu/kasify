package eu.kasify.plugins.ses

import java.time.LocalDate
import java.time.LocalDateTime

data class SesCatalogEntry(
    val code: String,
    val description: String,
)

// --- Communication Request DTOs ---

data class SesParteViajerosRequest(
    val arrendadorCode: String,
    val establishmentCode: String,
    val contract: SesContract,
    val guests: List<SesGuest>,
)

data class SesContract(
    val referencia: String,
    val fechaContrato: LocalDate,
    val fechaEntrada: LocalDateTime,
    val fechaSalida: LocalDateTime,
    val numPersonas: Int,
    val numHabitaciones: Int?,
    val internet: Boolean?,
    val tipoPago: String,
)

data class SesGuest(
    val rol: String,
    val nombre: String,
    val apellido1: String,
    val apellido2: String?,
    val tipoDocumento: String?,
    val numeroDocumento: String?,
    val soporteDocumento: String?,
    val fechaNacimiento: LocalDate,
    val nacionalidad: String?,
    val sexo: String?,
    val direccion: SesAddress,
    val telefono: String?,
    val correo: String?,
    val parentesco: String?,
)

data class SesAddress(
    val direccion: String,
    val direccionComplementaria: String?,
    val codigoMunicipio: String?,
    val nombreMunicipio: String?,
    val codigoPostal: String,
    val pais: String,
)

/**
 * Request for a Reserva de Hospedaje (RH) — the SES communication sent when a hospedaje booking is
 * confirmed. The contract block ([SesContract]) is identical to the PV one; the persona block uses
 * the leaner [SesReservaGuest] (`personaReservaType` in `altaReservaHospedaje.xsd`).
 */
data class SesReservaHospedajeRequest(
    val arrendadorCode: String,
    val establishmentCode: String,
    val contract: SesContract,
    val guests: List<SesReservaGuest>,
)

/**
 * A person in a Reserva de Hospedaje (`personaReservaType`). Compared to [SesGuest] (PV), this drops
 * `soporteDocumento` and `parentesco`, and `fechaNacimiento`/`direccion` are optional. The spec
 * requires at least one of `telefono`/`telefono2`/`correo`, but this is not enforced here.
 */
data class SesReservaGuest(
    val rol: String,
    val nombre: String,
    val apellido1: String,
    val apellido2: String?,
    val tipoDocumento: String?,
    val numeroDocumento: String?,
    val fechaNacimiento: LocalDate?,
    val nacionalidad: String?,
    val sexo: String?,
    val direccion: SesAddress?,
    val telefono: String?,
    val telefono2: String?,
    val correo: String?,
)

/**
 * Request to anular (annul) a previously-filed communication. The same SOAP `comunicacion` call as an
 * alta, but with `tipoOperacion=B` and a solicitud carrying the alta's [codigoComunicacion].
 */
data class SesCancelRequest(
    val arrendadorCode: String,
    val tipoComunicacion: String, // "PV" | "RH"
    val codigoComunicacion: String,
)

// --- Communication Response DTOs ---

data class SesCommunicationResponse(
    val codigo: Int,
    val descripcion: String,
    val lote: String?,
    val resultados: List<SesCommunicationResult>,
    val rawXml: String = "",
)

data class SesCommunicationResult(
    val orden: Int,
    val codigoComunicacion: String?,
    val tipoError: String?,
    val error: String?,
    val anulada: Boolean? = null,
)

// --- Batch Query (consultaLote) DTOs ---

data class SesBatchQueryResponse(
    val codigo: Int,
    val descripcion: String,
    val resultados: List<SesBatchInfo>,
    val rawXml: String = "",
)

data class SesBatchInfo(
    val lote: String,
    val tipoComunicacion: String,
    val tipoOperacion: String,
    val fechaPeticion: LocalDateTime,
    val fechaProcesamiento: LocalDateTime,
    val codigoEstado: Int,
    val descEstado: String,
    val identificadorUsuario: String,
    val nombreUsuario: String,
    val codigoArrendador: String,
    val aplicacion: String,
    val comunicaciones: List<SesCommunicationResult>,
)

/**
 * Joined per-communication error lines (`tipoError: error`) from a consultaLote batch — what SES
 * returns under `resultadoComunicaciones` for codigoEstado 6 — or null when there are none.
 */
fun SesBatchInfo.perCommunicationError(): String? {
    val lines =
        comunicaciones
            .filter { !it.error.isNullOrBlank() || !it.tipoError.isNullOrBlank() }
            .map { result ->
                listOfNotNull(
                    result.tipoError?.takeIf { it.isNotBlank() },
                    result.error?.takeIf { it.isNotBlank() },
                ).joinToString(": ")
            }
    return lines.takeIf { it.isNotEmpty() }?.joinToString(" | ")
}

/**
 * The single per-communication código from a consultaLote batch (SES only returns it here, never in
 * the submit response). Null when absent or — defensively — when more than one is present, since the
 * writers emit exactly one `<comunicacion>` per booking.
 */
fun SesBatchInfo.singleCommunicationCodigo(): String? {
    val codigos = comunicaciones.mapNotNull { it.codigoComunicacion?.takeIf { c -> c.isNotBlank() } }.distinct()
    return if (codigos.size == 1) codigos.first() else null
}
