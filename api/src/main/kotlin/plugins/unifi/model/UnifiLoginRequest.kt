package eu.kasify.plugins.unifi.model

import kotlinx.serialization.Serializable

@Serializable
data class UnifiLoginRequest(
    val username: String,
    val password: String,
    val remember: Boolean = true,
)
