package eu.kasify.model.api.responses

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator
import kotlin.uuid.Uuid

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("type")
sealed interface IntegrationItem {
    val id: Uuid?
}

@Serializable
@SerialName("nuki")
data class NukiIntegrationItem(
    override val id: Uuid? = null,
) : IntegrationItem {
    companion object {
        fun from(data: eu.kasify.model.NukiIntegration): NukiIntegrationItem = NukiIntegrationItem(id = data.id)
    }
}

@Serializable
@SerialName("unifi")
data class UnifiIntegrationItem(
    override val id: Uuid? = null,
    @SerialName("unifi_username")
    val unifiUsername: String,
    @SerialName("unifi_console_url")
    val unifiConsoleUrl: String,
) : IntegrationItem {
    companion object {
        fun from(data: eu.kasify.model.UnifiIntegration): UnifiIntegrationItem =
            UnifiIntegrationItem(
                id = data.id,
                unifiUsername = data.unifiUsername,
                unifiConsoleUrl = data.unifiConsoleUrl,
            )
    }
}

@Serializable
@SerialName("ses_hospedajes")
data class SesHospedajesIntegrationItem(
    override val id: Uuid? = null,
    @SerialName("arrendador_code")
    val arrendadorCode: String,
    @SerialName("ses_username")
    val sesUsername: String,
    val enabled: Boolean,
    @SerialName("rh_direct_bookings_enabled")
    val rhDirectBookingsEnabled: Boolean,
) : IntegrationItem {
    companion object {
        fun from(data: eu.kasify.model.SesHospedajesIntegration): SesHospedajesIntegrationItem =
            SesHospedajesIntegrationItem(
                id = data.id,
                arrendadorCode = data.arrendadorCode,
                sesUsername = data.sesUsername,
                enabled = data.enabled,
                rhDirectBookingsEnabled = data.rhDirectBookingsEnabled,
            )
    }
}

@Serializable
data class IntegrationsResponse(
    val integrations: List<IntegrationItem>,
)
