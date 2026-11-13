package eu.kasify.model.api

import eu.kasify.db.model.SmartlockVendor
import eu.kasify.model.AdminPropertyListItem
import eu.kasify.serialization.JavaOffsetDateTimeSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.OffsetDateTime
import kotlin.uuid.Uuid

@Serializable
data class AdminPropertyOwner(
    val id: Uuid,
    val name: String,
    @SerialName("last_name")
    val lastName: String,
    val email: String,
)

/**
 * Admin-only property list item exposing the property plus the user that created it.
 * Admin routes bypass `SecuritySerializer`, so every field here is always serialized.
 */
@Serializable
data class AdminProperty(
    val id: Uuid,
    @SerialName("account_id")
    val accountId: Uuid,
    val name: String,
    val address: String,
    val city: String,
    @SerialName("zip_code")
    val zipcode: String,
    @SerialName("rooms_count")
    val roomsCount: Int,
    @SerialName("has_internet")
    val hasInternet: Boolean?,
    @SerialName("ses_establishment_code")
    val sesEstablishmentCode: String?,
    @SerialName("cover_image_url")
    val coverImageUrl: String?,
    @Serializable(with = JavaOffsetDateTimeSerializer::class)
    @SerialName("created_at")
    val createdAt: OffsetDateTime,
    val owner: AdminPropertyOwner,
    val calendars: List<Calendar>,
    @SerialName("smartlock_vendors")
    val smartlockVendors: List<SmartlockVendor>,
) {
    companion object {
        fun from(
            item: AdminPropertyListItem,
            calendars: List<Calendar>,
            smartlockVendors: Set<SmartlockVendor> = emptySet(),
        ): AdminProperty =
            AdminProperty(
                id = item.id,
                accountId = item.accountId,
                name = item.name,
                address = item.address,
                city = item.city,
                zipcode = item.zipcode,
                roomsCount = item.roomsCount,
                hasInternet = item.hasInternet,
                sesEstablishmentCode = item.sesEstablishmentCode,
                coverImageUrl = item.coverImageUrl,
                createdAt = item.createdAt,
                owner =
                    AdminPropertyOwner(
                        id = item.ownerId,
                        name = item.ownerName,
                        lastName = item.ownerLastName,
                        email = item.ownerEmail,
                    ),
                calendars = calendars,
                smartlockVendors = smartlockVendors.sortedBy { it.name },
            )
    }
}
