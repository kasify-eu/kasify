package eu.kasify.model.api

import eu.kasify.db.model.BookingChannel
import eu.kasify.db.model.DocumentType
import eu.kasify.db.model.Gender
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid
import eu.kasify.model.Guest as DomainGuest

@Serializable
class Guest(
    val id: Uuid,
    @SerialName("booking_id")
    val bookingId: Uuid,
    @SerialName("property_id")
    val propertyId: Uuid,
    @SerialName("first_name")
    val firstName: String,
    @SerialName("last_name")
    val lastName: String,
    @SerialName("second_last_name")
    val secondLastName: String? = null,
    val nationality: Country? = null,
    @SerialName("date_of_birth")
    val dateOfBirth: String? = null,
    val sex: Gender? = null,
    @SerialName("residence_country")
    val residenceCountry: Country? = null,
    @SerialName("residence_province")
    val residenceProvince: Province? = null,
    @SerialName("residence_city")
    val residenceCity: City? = null,
    @SerialName("residence_city_name")
    val residenceCityName: String? = null,
    @SerialName("residence_address")
    val residenceAddress: String? = null,
    @SerialName("residence_postal_code")
    val residencePostalCode: String? = null,
    @SerialName("document_type")
    val documentType: DocumentType? = null,
    @SerialName("document_number")
    val documentNumber: String? = null,
    @SerialName("document_support_number")
    val documentSupportNumber: String? = null,
    val email: String? = null,
    val phone: String? = null,
    @SerialName("phone_prefix")
    val phonePrefix: String? = null,
    @SerialName("relationship_code")
    val relationshipCode: String? = null,
    // Booking-level context, populated only on the guests list endpoint (null elsewhere).
    val channel: BookingChannel? = null,
    @SerialName("channel_id")
    val channelId: String? = null,
    // Owner-private per-stay review; withheld (null) from managers of shared properties.
    @SerialName("would_host_again")
    val wouldHostAgain: Boolean? = null,
    // Effective email-marketing permission: the guest opted in at check-in AND the address
    // has not unsubscribed since that opt-in (marketing_suppressions.suppressed_at is
    // before marketing_consent_at). False when either condition fails.
    @SerialName("marketing_consent")
    val marketingConsent: Boolean = false,
    // Raw opt-in timestamp; non-null with marketing_consent=false means the guest
    // later unsubscribed, letting clients distinguish that from "never opted in".
    @SerialName("marketing_consent_at")
    val marketingConsentAt: String? = null,
) {
    companion object {
        fun from(
            domainGuest: DomainGuest,
            nationality: Country? = null,
            residenceCountry: Country? = null,
            residenceProvince: Province? = null,
            residenceCity: City? = null,
            channel: BookingChannel? = null,
            channelId: String? = null,
            wouldHostAgain: Boolean? = null,
            marketingConsent: Boolean = false,
        ): Guest =
            Guest(
                id = domainGuest.id,
                bookingId = domainGuest.bookingId,
                propertyId = domainGuest.propertyId,
                firstName = domainGuest.firstName,
                lastName = domainGuest.lastName,
                secondLastName = domainGuest.secondLastName,
                nationality = nationality,
                dateOfBirth = domainGuest.dateOfBirth?.toString(),
                sex = domainGuest.sex,
                residenceCountry = residenceCountry,
                residenceProvince = residenceProvince,
                residenceCity = residenceCity,
                residenceCityName = domainGuest.residenceCity,
                residenceAddress = domainGuest.residenceAddress,
                residencePostalCode = domainGuest.residencePostalCode,
                documentType = domainGuest.documentType,
                documentNumber = domainGuest.documentNumber,
                documentSupportNumber = domainGuest.documentSupportNumber,
                email = domainGuest.email,
                phone = domainGuest.phone,
                phonePrefix = domainGuest.phonePrefix,
                relationshipCode = domainGuest.relationshipCode,
                channel = channel,
                channelId = channelId,
                wouldHostAgain = wouldHostAgain,
                marketingConsent = marketingConsent,
                marketingConsentAt = domainGuest.marketingConsentAt?.toString(),
            )
    }
}
