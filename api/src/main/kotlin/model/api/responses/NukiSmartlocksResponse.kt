package eu.kasify.model.api.responses

import eu.kasify.model.api.NukiStatus
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
@Serializable
data class AvailableNukiSmartlock(
    @SerialName("smartlock_id") val smartlockId: Long,
    val name: String,
    @SerialName("lock_state") val lockState: Int?,
    @SerialName("battery_charge") val batteryCharge: Int?,
    @SerialName("battery_critical") val batteryCritical: Boolean,
    val offline: Boolean = false,
    @SerialName("assigned_property") val assignedProperty: AssignedProperty?,
)

@OptIn(ExperimentalUuidApi::class)
@Serializable
data class AssignedProperty(
    @SerialName("property_id") val propertyId: Uuid,
    @SerialName("property_name") val propertyName: String,
    @SerialName("smartlock_record_id") val smartlockRecordId: Uuid,
)

@Serializable
data class NukiSmartlocksResponse(
    val smartlocks: List<AvailableNukiSmartlock>,
    @SerialName("nuki_status") val nukiStatus: NukiStatus = NukiStatus.Online,
)
