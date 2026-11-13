package eu.kasify.cli

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TokenResponse(
    @SerialName("access_token")
    val accessToken: String,
    @SerialName("expires_in")
    val expiresIn: Int?,
    @SerialName("token_type")
    val tokenType: String? = null,
    @SerialName("scope")
    val scope: String? = null,
)
