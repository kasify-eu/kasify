package eu.kasify.db.model
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.timestampWithTimeZone
import org.jetbrains.exposed.v1.javatime.date

object GuestsTable : Table(name = "guests") {
    val id = uuid("id").autoGenerate()
    val bookingId = reference("booking_id", BookingsTable.id)
    val propertyId = reference("property_id", PropertiesTable.id)

    val firstName = text("first_name")
    val lastName = text("last_name")
    val secondLastName = text("second_last_name").nullable()
    val nationalityCountryId = reference("nationality_country_id", CountriesTable.id).nullable()
    val dateOfBirth = date("date_of_birth").nullable()

    val sex =
        customEnumeration(
            name = "sex",
            sql = "gender",
            fromDb = { value ->
                Gender.entries.find { it.name.equals(value as String, ignoreCase = true) }
                    ?: throw IllegalArgumentException("Invalid Gender: $value")
            },
            toDb = { PGEnum("gender", it) },
        ).nullable()

    val residenceCountryId = reference("residence_country_id", CountriesTable.id).nullable()
    val residenceProvinceId = reference("residence_province_id", ProvincesTable.id).nullable()
    val residenceCityId = reference("residence_city_id", CitiesTable.id).nullable()
    val residenceCity = text("residence_city").nullable()
    val residenceAddress = text("residence_address").nullable()
    val residencePostalCode = text("residence_postal_code").nullable()

    val documentType =
        customEnumeration(
            name = "document_type",
            sql = "document_type",
            fromDb = { value ->
                DocumentType.entries.find { it.name.equals(value as String, ignoreCase = true) }
                    ?: throw IllegalArgumentException("Invalid DocumentType: $value")
            },
            toDb = { PGEnum("document_type", it) },
        ).nullable()

    val documentNumber = text("document_number").nullable()
    val documentSupportNumber = text("document_support_number").nullable()
    val email = text("email").nullable()
    val phone = text("phone").nullable()
    val phonePrefix = text("phone_prefix").nullable()
    val relationshipCode = varchar("relationship_code", 5).nullable()

    // Marketing opt-in (GDPR Art. 6(1)(a)): all three stamped together when an
    // adult guest with an email ticks the optional checkbox, null otherwise.
    val marketingConsentAt = timestampWithTimeZone("marketing_consent_at").nullable()
    val marketingConsentVersion = text("marketing_consent_version").nullable()
    val marketingUnsubscribeToken = uuid("marketing_unsubscribe_token").nullable()

    val createdAt = timestampWithTimeZone("created_at")
    val updatedAt = timestampWithTimeZone("updated_at")

    override val primaryKey = PrimaryKey(id)
}
