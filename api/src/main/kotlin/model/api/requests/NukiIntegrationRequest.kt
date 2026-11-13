package eu.kasify.model.api.requests

import io.ktor.server.plugins.requestvalidation.ValidationResult
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class NukiIntegrationRequest(
    @SerialName("nuki_token")
    val nukiToken: String,
)

const val NUKI_TOKEN_MAX_LENGTH = 500

fun NukiIntegrationRequest.validate(): ValidationResult {
    val errors = mutableListOf<String>()

    if (this.nukiToken.isBlank()) {
        errors.add("Nuki token must not be blank")
    }

    if (this.nukiToken.length > NUKI_TOKEN_MAX_LENGTH) {
        errors.add("Nuki token must not exceed $NUKI_TOKEN_MAX_LENGTH characters")
    }

    if (errors.isNotEmpty()) {
        return ValidationResult.Invalid(errors)
    }

    return ValidationResult.Valid
}
