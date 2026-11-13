package eu.kasify.model.api.requests

import io.ktor.server.plugins.requestvalidation.ValidationResult
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Request body for creating or updating a SES Hospedajes integration.
 *
 * The SES `environment` is intentionally not part of this DTO — the route always targets the
 * production endpoint. If a sandbox integration is ever needed it can be set via a separate
 * admin-only path.
 */
@Serializable
data class SesHospedajesIntegrationRequest(
    @SerialName("arrendador_code")
    val arrendadorCode: String,
    @SerialName("ses_username")
    val sesUsername: String,
    @SerialName("ses_password")
    val sesPassword: String,
    // Whether to send the Reserva de Hospedaje (RH) communication for Direct bookings.
    // Optional: when omitted on update, the stored value is left untouched; on create it defaults to true.
    @SerialName("rh_direct_bookings_enabled")
    val rhDirectBookingsEnabled: Boolean? = null,
)

const val ARRENDADOR_CODE_MAX_LENGTH = 10
const val SES_USERNAME_MAX_LENGTH = 200
const val SES_PASSWORD_MAX_LENGTH = 500

fun SesHospedajesIntegrationRequest.validate(): ValidationResult {
    val errors = mutableListOf<String>()

    if (this.arrendadorCode.isBlank()) {
        errors.add("Arrendador code must not be blank")
    }

    if (this.arrendadorCode.length > ARRENDADOR_CODE_MAX_LENGTH) {
        errors.add("Arrendador code must not exceed $ARRENDADOR_CODE_MAX_LENGTH characters")
    }

    if (this.sesUsername.isBlank()) {
        errors.add("SES username must not be blank")
    }

    if (this.sesUsername.length > SES_USERNAME_MAX_LENGTH) {
        errors.add("SES username must not exceed $SES_USERNAME_MAX_LENGTH characters")
    }

    if (this.sesPassword.isBlank()) {
        errors.add("SES password must not be blank")
    }

    if (this.sesPassword.length > SES_PASSWORD_MAX_LENGTH) {
        errors.add("SES password must not exceed $SES_PASSWORD_MAX_LENGTH characters")
    }

    if (errors.isNotEmpty()) {
        return ValidationResult.Invalid(errors)
    }

    return ValidationResult.Valid
}
