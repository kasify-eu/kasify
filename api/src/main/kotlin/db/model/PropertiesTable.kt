package eu.kasify.db.model
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.timestampWithTimeZone
import org.jetbrains.exposed.v1.javatime.time
import java.time.LocalTime

object PropertiesTable : Table(name = "properties") {
    val id = uuid("id").autoGenerate()
    val accountId = reference("account_id", AccountsTable.id)
    val name = varchar("name", length = 50)
    val address = text("address")
    val city = text("city")
    val zipcode = text("zipcode")
    val tzone = text("tzone")
    val countryId = reference("country_id", CountriesTable.id)
    val provinceId = reference("province_id", ProvincesTable.id).nullable()
    val cityId = reference("city_id", CitiesTable.id).nullable()

    val createdById = reference("created_by_id", UsersTable.id)
    val defaultCleanerId = reference("default_cleaner_id", UsersTable.id)
    val cleaningDurationMinutes = integer("cleaning_duration_minutes").default(120)

    val checkinTime = time("checkin_time").default(LocalTime.of(17, 0))
    val checkoutTime = time("checkout_time").default(LocalTime.of(11, 0))

    val sesEstablishmentCode = varchar("ses_establishment_code", length = 10).nullable()

    // The SES integration that files for this property (one integration per property; strict —
    // an unassigned property is ineligible for SES). Set/cleared together with the establishment
    // code; DB FK has ON DELETE SET NULL so deleting an integration unassigns its properties.
    val sesIntegrationId = uuid("ses_integration_id").nullable()

    val sesEstablishmentType = varchar("ses_establishment_type", length = 10).nullable()

    // SES property-config error state (wrong establishment code — see SesPropertyConfigGuard).
    // While set, alta/anulación discovery skips this property; polls continue. Cleared when the
    // establishment code is re-saved or a later SES estado for the property succeeds.
    // Both columns are always set and cleared together; message NULL = config believed valid.
    val sesConfigErrorMessage = text("ses_config_error_message").nullable()
    val sesConfigErrorAt = timestampWithTimeZone("ses_config_error_at").nullable()

    // NIF/CIF of the actual arrendador — property-level because the Kasify account may be
    // a manager operating properties owned by different arrendadores.
    val arrendadorNif = text("arrendador_nif").nullable()
    val hasInternet = bool("has_internet").default(true).nullable()
    val roomsCount = integer("rooms_count").default(1)

    val coverImageUrl = text("cover_image_url").nullable()

    val deletedAt = timestampWithTimeZone("deleted_at").nullable()
    val createdAt = timestampWithTimeZone("created_at")
    val updatedAt = timestampWithTimeZone("updated_at")

    override val primaryKey = PrimaryKey(id)
}
