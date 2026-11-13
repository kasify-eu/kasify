package eu.kasify.model.api.requests

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Partial-update body for SES Hospedajes integration settings that don't touch credentials.
 * Used by `PATCH /integrations/ses-hospedajes/{id}/settings` so feature toggles can be flipped
 * without re-supplying or re-verifying the SES password.
 */
@Serializable
data class SesHospedajesSettingsRequest(
    @SerialName("rh_direct_bookings_enabled")
    val rhDirectBookingsEnabled: Boolean,
)
