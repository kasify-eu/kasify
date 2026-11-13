package eu.kasify.model.api.requests

import io.ktor.server.plugins.requestvalidation.ValidationResult
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.net.URI

@Serializable
data class UnifiIntegrationRequest(
    @SerialName("unifi_console_url")
    val unifiConsoleUrl: String,
    @SerialName("unifi_username")
    val unifiUsername: String,
    @SerialName("unifi_password")
    val unifiPassword: String,
)

const val UNIFI_CONSOLE_URL_MAX_LENGTH = 500
const val UNIFI_USERNAME_MAX_LENGTH = 200
const val UNIFI_PASSWORD_MAX_LENGTH = 500

fun UnifiIntegrationRequest.validate(): ValidationResult {
    val errors = mutableListOf<String>()

    if (this.unifiConsoleUrl.isBlank()) {
        errors.add("Unifi console URL must not be blank")
    }

    if (this.unifiConsoleUrl.length > UNIFI_CONSOLE_URL_MAX_LENGTH) {
        errors.add("Unifi console URL must not exceed $UNIFI_CONSOLE_URL_MAX_LENGTH characters")
    }

    if (this.unifiConsoleUrl.isNotBlank()) {
        try {
            val uri = URI(this.unifiConsoleUrl)
            if (uri.scheme !in listOf("http", "https")) {
                errors.add("Unifi console URL must use http or https protocol")
            }
        } catch (_: Exception) {
            errors.add("Unifi console URL is not a valid URL")
        }
    }

    if (this.unifiUsername.isBlank()) {
        errors.add("Unifi username must not be blank")
    }

    if (this.unifiUsername.length > UNIFI_USERNAME_MAX_LENGTH) {
        errors.add("Unifi username must not exceed $UNIFI_USERNAME_MAX_LENGTH characters")
    }

    if (this.unifiPassword.isBlank()) {
        errors.add("Unifi password must not be blank")
    }

    if (this.unifiPassword.length > UNIFI_PASSWORD_MAX_LENGTH) {
        errors.add("Unifi password must not exceed $UNIFI_PASSWORD_MAX_LENGTH characters")
    }

    if (errors.isNotEmpty()) {
        return ValidationResult.Invalid(errors)
    }

    return ValidationResult.Valid
}
