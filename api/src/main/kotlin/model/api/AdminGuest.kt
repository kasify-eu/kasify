package eu.kasify.model.api

import eu.kasify.db.model.DocumentType
import eu.kasify.model.AdminGuestListItem
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

/**
 * Admin-only guest list item: the guest plus the property it belongs to and that
 * property owner's email. Admin routes bypass `SecuritySerializer`, so all PII here is
 * always serialized.
 */
@Serializable
data class AdminGuest(
    val id: Uuid,
    @SerialName("booking_id")
    val bookingId: Uuid,
    @SerialName("property_id")
    val propertyId: Uuid,
    @SerialName("property_name")
    val propertyName: String,
    @SerialName("property_city")
    val propertyCity: String,
    @SerialName("channel_id")
    val channelId: String? = null,
    @SerialName("owner_name")
    val ownerName: String,
    @SerialName("first_name")
    val firstName: String,
    @SerialName("last_name")
    val lastName: String,
    @SerialName("second_last_name")
    val secondLastName: String? = null,
    @SerialName("nationality_alpha_2")
    val nationalityAlpha2: String? = null,
    @SerialName("nationality_name")
    val nationalityName: String? = null,
    @SerialName("date_of_birth")
    val dateOfBirth: String? = null,
    @SerialName("document_type")
    val documentType: DocumentType? = null,
    @SerialName("document_number")
    val documentNumber: String? = null,
    val email: String? = null,
    val phone: String? = null,
    @SerialName("phone_prefix")
    val phonePrefix: String? = null,
) {
    companion object {
        fun from(item: AdminGuestListItem): AdminGuest =
            AdminGuest(
                id = item.id,
                bookingId = item.bookingId,
                propertyId = item.propertyId,
                propertyName = item.propertyName,
                propertyCity = item.propertyCity,
                channelId = item.channelId,
                ownerName = "${item.ownerName} ${item.ownerLastName}".trim(),
                firstName = item.firstName,
                lastName = item.lastName,
                secondLastName = item.secondLastName,
                nationalityAlpha2 = item.nationalityAlpha2,
                nationalityName = item.nationalityName,
                dateOfBirth = item.dateOfBirth?.toString(),
                documentType = item.documentType,
                documentNumber = item.documentNumber,
                email = item.email,
                phone = item.phone,
                phonePrefix = item.phonePrefix,
            )
    }
}
