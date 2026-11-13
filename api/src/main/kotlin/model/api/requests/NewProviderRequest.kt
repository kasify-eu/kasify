package eu.kasify.model.api.requests

import io.ktor.server.plugins.requestvalidation.ValidationResult
import kotlinx.serialization.Serializable

@Serializable
data class NewProviderRequest(
    val name: String,
)

const val PROVIDER_MAX_NAME_LENGTH = 50

fun NewProviderRequest.validate(): ValidationResult {
    val errors = mutableListOf<String>()
    if (this.name.isBlank()) {
        errors.add("Provider name must not be blank")
    }

    if (this.name.length > PROVIDER_MAX_NAME_LENGTH) {
        errors.add("Provider name must not exceed $PROVIDER_MAX_NAME_LENGTH characters")
    }

    if (errors.isNotEmpty()) {
        return ValidationResult.Invalid(errors)
    }

    return ValidationResult.Valid
}
