package eu.kasify.model.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
data class SesHospedajesIntegration(
    var id: Uuid? = null,
    @SerialName("arrendador_code")
    val arrendadorCode: String,
    @SerialName("ses_username")
    val sesUsername: String,
    val enabled: Boolean,
    @SerialName("rh_direct_bookings_enabled")
    val rhDirectBookingsEnabled: Boolean,
    // Set when SES rejected a call with a credential-family código (10103/10107/10119/10120).
    // While set, guest-registry submissions for the account are paused; re-saving verified
    // credentials clears it. Null = credentials believed valid.
    @SerialName("credentials_error_code")
    val credentialsErrorCode: Int? = null,
    @SerialName("credentials_error_message")
    val credentialsErrorMessage: String? = null,
    @SerialName("credentials_error_at")
    val credentialsErrorAt: String? = null,
) {
    companion object {
        fun from(data: eu.kasify.model.SesHospedajesIntegration): SesHospedajesIntegration =
            SesHospedajesIntegration(
                id = data.id,
                arrendadorCode = data.arrendadorCode,
                sesUsername = data.sesUsername,
                enabled = data.enabled,
                rhDirectBookingsEnabled = data.rhDirectBookingsEnabled,
                credentialsErrorCode = data.credentialsErrorCode,
                credentialsErrorMessage = data.credentialsErrorMessage,
                credentialsErrorAt = data.credentialsErrorAt?.toString(),
            )
    }
}
