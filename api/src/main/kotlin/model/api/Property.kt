package eu.kasify.model.api

import eu.kasify.model.SesIntegrationGateState
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

/**
 * Derived SES filing health of a property, computed from the property row and its *assigned*
 * integration's gate state. `paused_*` values mean the auto jobs are currently skipping the
 * property (see `SesCredentialsGuard` / `SesPropertyConfigGuard`).
 */
@Serializable
enum class SesPropertyStatus {
    @SerialName("not_configured")
    NotConfigured,

    @SerialName("disabled")
    Disabled,

    @SerialName("paused_credentials")
    PausedCredentials,

    @SerialName("paused_config")
    PausedConfig,

    @SerialName("ok")
    Ok,
}

@Serializable
data class Property(
    var id: Uuid? = null,
    @SerialName("account_id")
    val accountId: Uuid? = null,
    val name: String,
    val address: String? = null,
    val city: String? = null,
    @SerialName("zip_code")
    val zipcode: String? = null,
    val country: String? = null,
    @SerialName("country_id")
    val countryId: Uuid? = null,
    @SerialName("country_code")
    val countryCode: String? = null,
    @SerialName("province_id")
    val provinceId: Uuid? = null,
    @SerialName("city_id")
    val cityId: Uuid? = null,
    @SerialName("checkin_time")
    val checkinTime: String? = null,
    @SerialName("checkout_time")
    val checkoutTime: String? = null,
    @VisibleTo(Visibility.MANAGER)
    @SerialName("ses_establishment_code")
    val sesEstablishmentCode: String? = null,
    // The SES integration assigned to file for this property; null = not configured for SES.
    @VisibleTo(Visibility.MANAGER)
    @SerialName("ses_integration_id")
    val sesIntegrationId: Uuid? = null,
    @VisibleTo(Visibility.MANAGER)
    @SerialName("ses_establishment_type")
    val sesEstablishmentType: String? = null,
    @VisibleTo(Visibility.MANAGER)
    @SerialName("arrendador_nif")
    val arrendadorNif: String? = null,
    // True when the property's account has an enabled SES Hospedajes integration AND the
    // property has an establishment code — i.e. SES submissions can actually be filed.
    @VisibleTo(Visibility.MANAGER)
    @SerialName("ses_enabled")
    val sesEnabled: Boolean? = null,
    // Derived SES filing health (see SesPropertyStatus). Only present on responses that resolve
    // the property's assigned integration (list/detail); omitted elsewhere, like ses_enabled.
    @VisibleTo(Visibility.MANAGER)
    @SerialName("ses_status")
    val sesStatus: SesPropertyStatus? = null,
    // SES's own error text when ses_status is paused_config (wrong establishment code) — shown in
    // the dashboard banner next to the code-edit card. Cleared when the code is re-saved.
    @VisibleTo(Visibility.MANAGER)
    @SerialName("ses_config_error_message")
    val sesConfigErrorMessage: String? = null,
    @VisibleTo(Visibility.MANAGER)
    @SerialName("has_internet")
    val hasInternet: Boolean? = null,
    @VisibleTo(Visibility.MANAGER)
    @SerialName("rooms_count")
    val roomsCount: Int? = null,
    @SerialName("default_cleaner")
    @VisibleTo(Visibility.MANAGER)
    val defaultCleaner: PropertyMember? = null,
    @SerialName("cover_image_url")
    val coverImageUrl: String? = null,
    val role: String? = null,
    // Omitted from the JSON when null (explicitNulls = false) so empty collections
    // don't clutter embedded property payloads (e.g. inside tasks).
    @SerialName("calendars")
    @VisibleTo(Visibility.MANAGER)
    val calendars: List<Calendar>? = null,
    @SerialName("smart_locks")
    @VisibleTo(Visibility.MANAGER)
    val smartLocks: List<SmartLockInfo>? = null,
    @VisibleTo(Visibility.MANAGER)
    val team: List<PropertyMember>? = null,
) {
    companion object {
        fun from(
            property: eu.kasify.model.Property,
            calendars: List<Calendar> = emptyList(),
            smartLocks: List<SmartLockInfo> = emptyList(),
            team: List<PropertyMember> = emptyList(),
            role: String? = null,
            defaultCleaner: PropertyMember? = null,
            country: eu.kasify.model.Country? = null,
            sesIntegrationEnabled: Boolean? = null,
            // Gate state of the property's *assigned* integration; drives ses_status. Callers that
            // pass sesIntegrationEnabled should pass this too (null with an assigned code reads as
            // a missing/deleted integration → Disabled).
            assignedIntegrationState: SesIntegrationGateState? = null,
        ): Property =
            Property(
                id = property.id,
                name = property.name,
                address = property.address,
                city = property.city,
                zipcode = property.zipcode,
                country = country?.alpha2,
                countryId = property.countryId,
                countryCode = country?.alpha3,
                provinceId = property.provinceId,
                cityId = property.cityId,
                checkinTime = property.checkinTime.toString(),
                checkoutTime = property.checkoutTime.toString(),
                accountId = property.accountId,
                sesEstablishmentCode = property.sesEstablishmentCode,
                sesIntegrationId = property.sesIntegrationId,
                sesEstablishmentType = property.sesEstablishmentType,
                arrendadorNif = property.arrendadorNif,
                sesEnabled =
                    sesIntegrationEnabled?.let {
                        // Strict model: SES filings need the account-level integration, the
                        // establishment code AND the explicit property → integration assignment.
                        it && !property.sesEstablishmentCode.isNullOrBlank() && property.sesIntegrationId != null
                    },
                // Like sesEnabled, only derived when the caller resolved SES info for the response.
                sesStatus = sesIntegrationEnabled?.let { deriveSesStatus(property, assignedIntegrationState) },
                sesConfigErrorMessage = property.sesConfigErrorMessage,
                hasInternet = property.hasInternet,
                roomsCount = property.roomsCount,
                coverImageUrl = property.coverImageUrl,
                defaultCleaner = defaultCleaner,
                calendars = calendars.ifEmpty { null },
                smartLocks = smartLocks.ifEmpty { null },
                team = team.ifEmpty { null },
                role = role,
            )

        // Precedence mirrors what actually blocks a filing: no config at all → the integration
        // being unusable → the account-wide credential pause → the property-scoped config pause.
        private fun deriveSesStatus(
            property: eu.kasify.model.Property,
            state: SesIntegrationGateState?,
        ): SesPropertyStatus =
            when {
                property.sesEstablishmentCode.isNullOrBlank() || property.sesIntegrationId == null ->
                    SesPropertyStatus.NotConfigured
                state == null || !state.enabled -> SesPropertyStatus.Disabled
                state.credentialsFlagged -> SesPropertyStatus.PausedCredentials
                property.sesConfigErrorMessage != null -> SesPropertyStatus.PausedConfig
                else -> SesPropertyStatus.Ok
            }
    }
}
