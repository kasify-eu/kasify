package eu.kasify.db.model

import eu.kasify.model.GuestRegistrySnapshot
import eu.kasify.model.SesCommunicationType
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.timestampWithTimeZone
import org.jetbrains.exposed.v1.javatime.date
import org.jetbrains.exposed.v1.json.jsonb

private val registryJson = Json { ignoreUnknownKeys = true }

/**
 * Registro documental / libro registro de viajeros (RD 933/2021, Orden INT/1922/2003).
 * Immutable snapshot of every alta accepted by SES. Deliberately has NO foreign keys:
 * entries must survive deletes/purges of bookings, properties and accounts for the
 * 3-year legal retention window.
 */
object GuestRegistryEntriesTable : Table(name = "guest_registry_entries") {
    val id = uuid("id").autoGenerate()
    val accountId = uuid("account_id")
    val propertyId = uuid("property_id")
    val bookingId = uuid("booking_id")
    val entryNumber = long("entry_number")

    val communicationType =
        customEnumeration(
            name = "communication_type",
            sql = "ses_communication_type",
            fromDb = { value -> SesCommunicationType.fromDb(value as String) },
            toDb = { PGEnum("ses_communication_type", it) },
        )

    val sesArrendadorCode = text("ses_arrendador_code")
    val arrendadorNif = text("arrendador_nif").nullable()
    val sesEstablishmentCode = text("ses_establishment_code")
    val propertyName = text("property_name")
    val propertyAddress = text("property_address")
    val postalCode = text("postal_code").nullable()
    val city = text("city").nullable()
    val province = text("province").nullable()

    val contractReference = text("contract_reference")
    val contractDate = date("contract_date")

    val bookingChannel =
        customEnumeration(
            name = "booking_channel",
            sql = "booking_channel",
            fromDb = { value ->
                BookingChannel.entries.find { it.name.equals(value as String, ignoreCase = true) }
                    ?: throw IllegalArgumentException("Invalid BookingChannel: $value")
            },
            toDb = { PGEnum("booking_channel", it) },
        )

    val checkinDate = date("checkin_date")
    val checkoutDate = date("checkout_date")
    val numGuests = integer("num_guests")

    val snapshot =
        jsonb<GuestRegistrySnapshot>(
            name = "snapshot",
            serialize = { registryJson.encodeToString(GuestRegistrySnapshot.serializer(), it) },
            deserialize = { registryJson.decodeFromString(GuestRegistrySnapshot.serializer(), it) },
        )
    val requestXml = text("request_xml")

    val sesLote = text("ses_lote").nullable()
    val codigoComunicacion = text("codigo_comunicacion").nullable()

    val sesEstado =
        customEnumeration(
            name = "ses_estado",
            sql = "ses_codigo_estado",
            fromDb = { value -> SesCodigoEstado.fromDb(value as String) },
            toDb = { SesCodigoEstadoPGEnum(it) },
        ).nullable()

    val submittedAt = timestampWithTimeZone("submitted_at")
    val createdAt = timestampWithTimeZone("created_at")

    override val primaryKey = PrimaryKey(id)
}
