package eu.kasify.model.api.requests

import io.ktor.server.plugins.requestvalidation.ValidationResult
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

private const val MAX_NAME_LENGTH = 100

@Serializable
data class SignupRequest(
    val name: String,
    @SerialName("last_name")
    val lastname: String,
)

fun SignupRequest.validate(): ValidationResult {
    val errors = mutableListOf<String>()

    if (name.isBlank()) {
        errors.add("Name must not be blank")
    }
    if (name.length > MAX_NAME_LENGTH) {
        errors.add("Name must not exceed $MAX_NAME_LENGTH characters")
    }
    if (lastname.isBlank()) {
        errors.add("Last name must not be blank")
    }
    if (lastname.length > MAX_NAME_LENGTH) {
        errors.add("Last name must not exceed $MAX_NAME_LENGTH characters")
    }

    return if (errors.isEmpty()) ValidationResult.Valid else ValidationResult.Invalid(errors)
}
