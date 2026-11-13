package eu.kasify.model
import eu.kasify.db.model.BookingsTable
import eu.kasify.db.model.CountriesTable
import eu.kasify.db.model.DocumentType
import eu.kasify.db.model.GuestsTable
import eu.kasify.db.model.PropertiesTable
import eu.kasify.db.model.UsersTable
import io.perracodex.exposed.pagination.MapModel
import org.jetbrains.exposed.v1.core.ResultRow
import java.time.LocalDate
import kotlin.uuid.Uuid

/**
 * A guest plus the property it belongs to and that property's owner email, for the admin
 * guests list. Built from a `guests INNER JOIN properties INNER JOIN bookings INNER JOIN
 * users LEFT JOIN countries` (on nationality) row, so the companion reads all five tables'
 * columns — countries via [ResultRow.getOrNull] because the nationality join is optional.
 * `channelId` is the booking's external reservation reference.
 */
data class AdminGuestListItem(
    val id: Uuid,
    val bookingId: Uuid,
    val propertyId: Uuid,
    val propertyName: String,
    val propertyCity: String,
    val channelId: String,
    val ownerName: String,
    val ownerLastName: String,
    val ownerEmail: String,
    val firstName: String,
    val lastName: String,
    val secondLastName: String?,
    val nationalityCountryId: Uuid?,
    val nationalityAlpha2: String?,
    val nationalityName: String?,
    val dateOfBirth: LocalDate?,
    val documentType: DocumentType?,
    val documentNumber: String?,
    val email: String?,
    val phone: String?,
    val phonePrefix: String?,
) {
    companion object : MapModel<AdminGuestListItem> {
        override fun from(row: ResultRow): AdminGuestListItem =
            AdminGuestListItem(
                id = row[GuestsTable.id],
                bookingId = row[GuestsTable.bookingId],
                propertyId = row[GuestsTable.propertyId],
                propertyName = row[PropertiesTable.name],
                propertyCity = row[PropertiesTable.city],
                channelId = row[BookingsTable.channelId],
                ownerName = row[UsersTable.name],
                ownerLastName = row[UsersTable.lastName],
                ownerEmail = row[UsersTable.email],
                firstName = row[GuestsTable.firstName],
                lastName = row[GuestsTable.lastName],
                secondLastName = row[GuestsTable.secondLastName],
                nationalityCountryId = row[GuestsTable.nationalityCountryId],
                nationalityAlpha2 = row.getOrNull(CountriesTable.alpha2)?.trim(),
                nationalityName = row.getOrNull(CountriesTable.name),
                dateOfBirth = row[GuestsTable.dateOfBirth],
                documentType = row[GuestsTable.documentType],
                documentNumber = row[GuestsTable.documentNumber],
                email = row[GuestsTable.email],
                phone = row[GuestsTable.phone],
                phonePrefix = row[GuestsTable.phonePrefix],
            )
    }
}
