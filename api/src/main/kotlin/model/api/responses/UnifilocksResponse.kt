package eu.kasify.model.api.responses

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class UnifiStatus {
    @SerialName("online")
    Online,

    @SerialName("unauthorized")
    Unauthorized,

    @SerialName("unreachable")
    Unreachable,
}

@Serializable
data class UnifilocksResponse(
    val smartlocks: List<UnifiSmartlock>,
    @SerialName("unifi_status") val unifiStatus: UnifiStatus = UnifiStatus.Online,
)

@Serializable
data class UnifiSmartlock(
    @SerialName("floor_id") val floorId: String,
    @SerialName("door_id") val doorId: String,
    val name: String,
    @SerialName("assigned_property") val assignedProperty: AssignedProperty?,
)
