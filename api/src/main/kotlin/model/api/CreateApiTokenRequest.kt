package eu.kasify.model.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CreateApiTokenRequest(
    val name: String,
    @SerialName("expires_in_days")
    val expiresInDays: Int? = DEFAULT_EXPIRATION_DAYS,
) {
    companion object {
        const val DEFAULT_EXPIRATION_DAYS: Int = 365
        const val MAX_EXPIRATION_DAYS: Int = 730
        const val MIN_EXPIRATION_DAYS: Int = 1
        const val MAX_NAME_LENGTH: Int = 80
    }
}
