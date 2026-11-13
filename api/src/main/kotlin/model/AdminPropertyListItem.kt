package eu.kasify.model
import eu.kasify.db.model.PropertiesTable
import eu.kasify.db.model.UsersTable
import io.perracodex.exposed.pagination.MapModel
import org.jetbrains.exposed.v1.core.ResultRow
import java.time.OffsetDateTime
import kotlin.uuid.Uuid

/**
 * A property plus the user that created it, for the admin properties list. Built from a
 * `properties INNER JOIN users` row (join on `properties.created_by_id`), so the
 * companion reads both tables' columns.
 */
data class AdminPropertyListItem(
    val id: Uuid,
    val accountId: Uuid,
    val name: String,
    val address: String,
    val city: String,
    val zipcode: String,
    val roomsCount: Int,
    val hasInternet: Boolean?,
    val sesEstablishmentCode: String?,
    val coverImageUrl: String?,
    val createdAt: OffsetDateTime,
    val ownerId: Uuid,
    val ownerName: String,
    val ownerLastName: String,
    val ownerEmail: String,
) {
    companion object : MapModel<AdminPropertyListItem> {
        override fun from(row: ResultRow): AdminPropertyListItem =
            AdminPropertyListItem(
                id = row[PropertiesTable.id],
                accountId = row[PropertiesTable.accountId],
                name = row[PropertiesTable.name],
                address = row[PropertiesTable.address],
                city = row[PropertiesTable.city],
                zipcode = row[PropertiesTable.zipcode],
                roomsCount = row[PropertiesTable.roomsCount],
                hasInternet = row[PropertiesTable.hasInternet],
                sesEstablishmentCode = row[PropertiesTable.sesEstablishmentCode],
                coverImageUrl = row[PropertiesTable.coverImageUrl],
                createdAt = row[PropertiesTable.createdAt],
                ownerId = row[UsersTable.id],
                ownerName = row[UsersTable.name],
                ownerLastName = row[UsersTable.lastName],
                ownerEmail = row[UsersTable.email],
            )
    }
}
