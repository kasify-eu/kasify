package eu.kasify.model

import eu.kasify.db.model.BookingChannel
import eu.kasify.db.model.GuestRegistryEntriesTable
import eu.kasify.db.model.SesCodigoEstado
import eu.kasify.plugins.ses.SesAddress
import eu.kasify.plugins.ses.SesContract
import eu.kasify.plugins.ses.SesGuest
import eu.kasify.plugins.ses.SesReservaGuest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.ResultRow
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import kotlin.uuid.Uuid

/**
 * One line of the libro registro de viajeros: the frozen content of an alta accepted by SES,
 * plus the denormalized establishment/contract data that keeps it readable on its own.
 */
data class GuestRegistryEntry(
    val id: Uuid,
    val accountId: Uuid,
    val propertyId: Uuid,
    val bookingId: Uuid,
    val entryNumber: Long,
    val communicationType: SesCommunicationType,
    val sesArrendadorCode: String,
    val arrendadorNif: String?,
    val sesEstablishmentCode: String,
    val propertyName: String,
    val propertyAddress: String,
    val postalCode: String?,
    val city: String?,
    val province: String?,
    val contractReference: String,
    val contractDate: LocalDate,
    val bookingChannel: BookingChannel,
    val checkinDate: LocalDate,
    val checkoutDate: LocalDate,
    val numGuests: Int,
    val snapshot: GuestRegistrySnapshot,
    val requestXml: String,
    val sesLote: String?,
    val codigoComunicacion: String?,
    val sesEstado: SesCodigoEstado?,
    val submittedAt: OffsetDateTime,
    val createdAt: OffsetDateTime,
) {
    companion object {
        fun from(row: ResultRow): GuestRegistryEntry =
            GuestRegistryEntry(
                id = row[GuestRegistryEntriesTable.id],
                accountId = row[GuestRegistryEntriesTable.accountId],
                propertyId = row[GuestRegistryEntriesTable.propertyId],
                bookingId = row[GuestRegistryEntriesTable.bookingId],
                entryNumber = row[GuestRegistryEntriesTable.entryNumber],
                communicationType = row[GuestRegistryEntriesTable.communicationType],
                sesArrendadorCode = row[GuestRegistryEntriesTable.sesArrendadorCode],
                arrendadorNif = row[GuestRegistryEntriesTable.arrendadorNif],
                sesEstablishmentCode = row[GuestRegistryEntriesTable.sesEstablishmentCode],
                propertyName = row[GuestRegistryEntriesTable.propertyName],
                propertyAddress = row[GuestRegistryEntriesTable.propertyAddress],
                postalCode = row[GuestRegistryEntriesTable.postalCode],
                city = row[GuestRegistryEntriesTable.city],
                province = row[GuestRegistryEntriesTable.province],
                contractReference = row[GuestRegistryEntriesTable.contractReference],
                contractDate = row[GuestRegistryEntriesTable.contractDate],
                bookingChannel = row[GuestRegistryEntriesTable.bookingChannel],
                checkinDate = row[GuestRegistryEntriesTable.checkinDate],
                checkoutDate = row[GuestRegistryEntriesTable.checkoutDate],
                numGuests = row[GuestRegistryEntriesTable.numGuests],
                snapshot = row[GuestRegistryEntriesTable.snapshot],
                requestXml = row[GuestRegistryEntriesTable.requestXml],
                sesLote = row[GuestRegistryEntriesTable.sesLote],
                codigoComunicacion = row[GuestRegistryEntriesTable.codigoComunicacion],
                sesEstado = row[GuestRegistryEntriesTable.sesEstado],
                submittedAt = row[GuestRegistryEntriesTable.submittedAt],
                createdAt = row[GuestRegistryEntriesTable.createdAt],
            )
    }
}

/**
 * The frozen parte as communicated to SES, mirroring the solicitud structure. Dates are ISO-8601
 * strings so the JSON stays stable and self-describing for the whole retention window. PV guests
 * carry `soporte_documento`/`parentesco`; RH titulars may carry `telefono2` — one shape covers both.
 */
@Serializable
data class GuestRegistrySnapshot(
    val contract: Contract,
    val guests: List<Person>,
) {
    @Serializable
    data class Contract(
        val referencia: String,
        @SerialName("fecha_contrato") val fechaContrato: String,
        @SerialName("fecha_entrada") val fechaEntrada: String,
        @SerialName("fecha_salida") val fechaSalida: String,
        @SerialName("num_personas") val numPersonas: Int,
        @SerialName("num_habitaciones") val numHabitaciones: Int? = null,
        val internet: Boolean? = null,
        @SerialName("tipo_pago") val tipoPago: String,
    )

    @Serializable
    data class Person(
        val rol: String,
        val nombre: String,
        val apellido1: String,
        val apellido2: String? = null,
        @SerialName("tipo_documento") val tipoDocumento: String? = null,
        @SerialName("numero_documento") val numeroDocumento: String? = null,
        @SerialName("soporte_documento") val soporteDocumento: String? = null,
        @SerialName("fecha_nacimiento") val fechaNacimiento: String? = null,
        val nacionalidad: String? = null,
        val sexo: String? = null,
        val direccion: Address? = null,
        val telefono: String? = null,
        val telefono2: String? = null,
        val correo: String? = null,
        val parentesco: String? = null,
    )

    @Serializable
    data class Address(
        val direccion: String,
        @SerialName("direccion_complementaria") val direccionComplementaria: String? = null,
        @SerialName("codigo_municipio") val codigoMunicipio: String? = null,
        @SerialName("nombre_municipio") val nombreMunicipio: String? = null,
        @SerialName("codigo_postal") val codigoPostal: String,
        val pais: String,
    )

    companion object {
        private val DATE: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
        private val DATETIME: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

        fun fromPv(
            contract: SesContract,
            guests: List<SesGuest>,
        ): GuestRegistrySnapshot =
            GuestRegistrySnapshot(
                contract = contract.toSnapshot(),
                guests =
                    guests.map { guest ->
                        Person(
                            rol = guest.rol,
                            nombre = guest.nombre,
                            apellido1 = guest.apellido1,
                            apellido2 = guest.apellido2,
                            tipoDocumento = guest.tipoDocumento,
                            numeroDocumento = guest.numeroDocumento,
                            soporteDocumento = guest.soporteDocumento,
                            fechaNacimiento = guest.fechaNacimiento.format(DATE),
                            nacionalidad = guest.nacionalidad,
                            sexo = guest.sexo,
                            direccion = guest.direccion.toSnapshot(),
                            telefono = guest.telefono,
                            correo = guest.correo,
                            parentesco = guest.parentesco,
                        )
                    },
            )

        fun fromRh(
            contract: SesContract,
            guests: List<SesReservaGuest>,
        ): GuestRegistrySnapshot =
            GuestRegistrySnapshot(
                contract = contract.toSnapshot(),
                guests =
                    guests.map { guest ->
                        Person(
                            rol = guest.rol,
                            nombre = guest.nombre,
                            apellido1 = guest.apellido1,
                            apellido2 = guest.apellido2,
                            tipoDocumento = guest.tipoDocumento,
                            numeroDocumento = guest.numeroDocumento,
                            fechaNacimiento = guest.fechaNacimiento?.format(DATE),
                            nacionalidad = guest.nacionalidad,
                            sexo = guest.sexo,
                            direccion = guest.direccion?.toSnapshot(),
                            telefono = guest.telefono,
                            telefono2 = guest.telefono2,
                            correo = guest.correo,
                        )
                    },
            )

        private fun SesContract.toSnapshot(): Contract =
            Contract(
                referencia = referencia,
                fechaContrato = fechaContrato.format(DATE),
                fechaEntrada = fechaEntrada.format(DATETIME),
                fechaSalida = fechaSalida.format(DATETIME),
                numPersonas = numPersonas,
                numHabitaciones = numHabitaciones,
                internet = internet,
                tipoPago = tipoPago,
            )

        private fun SesAddress.toSnapshot(): Address =
            Address(
                direccion = direccion,
                direccionComplementaria = direccionComplementaria,
                codigoMunicipio = codigoMunicipio,
                nombreMunicipio = nombreMunicipio,
                codigoPostal = codigoPostal,
                pais = pais,
            )
    }
}
