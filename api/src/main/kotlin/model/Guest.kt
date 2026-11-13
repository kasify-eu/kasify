package eu.kasify.model
import eu.kasify.db.model.DocumentType
import eu.kasify.db.model.Gender
import eu.kasify.db.model.GuestsTable
import io.perracodex.exposed.pagination.MapModel
import org.jetbrains.exposed.v1.core.ResultRow
import java.time.LocalDate
import java.time.OffsetDateTime
import kotlin.uuid.Uuid

class Guest(
    val id: Uuid,
    val bookingId: Uuid,
    val propertyId: Uuid,
    val firstName: String,
    val lastName: String,
    val secondLastName: String? = null,
    val nationalityCountryId: Uuid? = null,
    val dateOfBirth: LocalDate? = null,
    val sex: Gender? = null,
    val residenceCountryId: Uuid? = null,
    val residenceProvinceId: Uuid? = null,
    val residenceCityId: Uuid? = null,
    val residenceCity: String? = null,
    val residenceAddress: String? = null,
    val residencePostalCode: String? = null,
    val documentType: DocumentType? = null,
    val documentNumber: String? = null,
    val documentSupportNumber: String? = null,
    val email: String? = null,
    val phone: String? = null,
    val phonePrefix: String? = null,
    val relationshipCode: String? = null,
    val marketingConsentAt: OffsetDateTime? = null,
) {
    companion object : MapModel<Guest> {
        override fun from(row: ResultRow): Guest =
            Guest(
                id = row[GuestsTable.id],
                bookingId = row[GuestsTable.bookingId],
                propertyId = row[GuestsTable.propertyId],
                firstName = row[GuestsTable.firstName],
                lastName = row[GuestsTable.lastName],
                secondLastName = row[GuestsTable.secondLastName],
                nationalityCountryId = row[GuestsTable.nationalityCountryId],
                dateOfBirth = row[GuestsTable.dateOfBirth],
                sex = row[GuestsTable.sex],
                residenceCountryId = row[GuestsTable.residenceCountryId],
                residenceProvinceId = row[GuestsTable.residenceProvinceId],
                residenceCityId = row[GuestsTable.residenceCityId],
                residenceCity = row[GuestsTable.residenceCity],
                residenceAddress = row[GuestsTable.residenceAddress],
                residencePostalCode = row[GuestsTable.residencePostalCode],
                documentType = row[GuestsTable.documentType],
                documentNumber = row[GuestsTable.documentNumber],
                documentSupportNumber = row[GuestsTable.documentSupportNumber],
                email = row[GuestsTable.email],
                phone = row[GuestsTable.phone],
                phonePrefix = row[GuestsTable.phonePrefix],
                relationshipCode = row[GuestsTable.relationshipCode],
                marketingConsentAt = row[GuestsTable.marketingConsentAt],
            )
    }
}
