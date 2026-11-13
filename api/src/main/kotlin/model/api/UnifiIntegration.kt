package eu.kasify.model.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
data class UnifiIntegration(
    var id: Uuid? = null,
    @SerialName("unifi_username")
    val unifiUsername: String,
    @SerialName("unifi_console_url")
    val unifiConsoleUrl: String,
) {
    companion object {
        fun from(data: eu.kasify.model.UnifiIntegration): UnifiIntegration =
            UnifiIntegration(
                id = data.id,
                unifiUsername = data.unifiUsername,
                unifiConsoleUrl = data.unifiConsoleUrl,
            )
    }
}
