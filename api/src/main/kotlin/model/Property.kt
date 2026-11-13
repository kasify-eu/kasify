package eu.kasify.model
import eu.kasify.db.model.PropertiesTable
import io.perracodex.exposed.pagination.MapModel
import org.jetbrains.exposed.v1.core.ResultRow
import java.time.LocalTime
import kotlin.uuid.Uuid

data class Property(
    var id: Uuid,
    val accountId: Uuid,
    val name: String,
    val address: String,
    val city: String,
    val zipcode: String,
    val countryId: Uuid,
    val tzone: String,
    val createdById: Uuid,
    val defaultCleanerId: Uuid,
    val cleaningDurationMinutes: Int = 120,
    val checkinTime: LocalTime = LocalTime.of(17, 0),
    val checkoutTime: LocalTime = LocalTime.of(11, 0),
    val sesEstablishmentCode: String? = null,
    // The SES integration assigned to file for this property; null = not configured for SES.
    val sesIntegrationId: Uuid? = null,
    val sesEstablishmentType: String? = null,
    // Non-null when SES reported the establishment code as invalid (see SesPropertyConfigGuard):
    // alta/anulación discovery skips the property until the code is re-saved or a filing succeeds.
    val sesConfigErrorMessage: String? = null,
    val sesConfigErrorAt: java.time.OffsetDateTime? = null,
    val arrendadorNif: String? = null,
    val hasInternet: Boolean? = true,
    val roomsCount: Int = 1,
    val coverImageUrl: String? = null,
    val provinceId: Uuid? = null,
    val cityId: Uuid? = null,
) {
    companion object : MapModel<Property> {
        override fun from(row: ResultRow): Property =
            Property(
                id = row[PropertiesTable.id],
                name = row[PropertiesTable.name],
                accountId = row[PropertiesTable.accountId],
                address = row[PropertiesTable.address],
                city = row[PropertiesTable.city],
                zipcode = row[PropertiesTable.zipcode],
                countryId = row[PropertiesTable.countryId],
                tzone = row[PropertiesTable.tzone],
                createdById = row[PropertiesTable.createdById],
                defaultCleanerId = row[PropertiesTable.defaultCleanerId],
                cleaningDurationMinutes = row[PropertiesTable.cleaningDurationMinutes],
                checkinTime = row[PropertiesTable.checkinTime],
                checkoutTime = row[PropertiesTable.checkoutTime],
                sesEstablishmentCode = row[PropertiesTable.sesEstablishmentCode],
                sesIntegrationId = row[PropertiesTable.sesIntegrationId],
                sesEstablishmentType = row[PropertiesTable.sesEstablishmentType],
                sesConfigErrorMessage = row[PropertiesTable.sesConfigErrorMessage],
                sesConfigErrorAt = row[PropertiesTable.sesConfigErrorAt],
                arrendadorNif = row[PropertiesTable.arrendadorNif],
                hasInternet = row[PropertiesTable.hasInternet],
                roomsCount = row[PropertiesTable.roomsCount],
                coverImageUrl = row[PropertiesTable.coverImageUrl],
                provinceId = row[PropertiesTable.provinceId],
                cityId = row[PropertiesTable.cityId],
            )
    }
}
