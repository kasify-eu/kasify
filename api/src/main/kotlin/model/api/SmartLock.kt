package eu.kasify.model.api

import eu.kasify.db.model.SmartlockVendor
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
data class SmartLock(
    val id: Uuid,
    val vendor: SmartlockVendor,
    val name: String,
    @SerialName("property_id")
    val propertyId: Uuid,
    @SerialName("nuki_smartlock")
    val nukiSmartlock: NukiSmartlock? = null,
    @SerialName("unifi_smartlock")
    val unifiSmartlock: UnifiSmartlock? = null,
    @SerialName("lock_state")
    val lockState: Int? = null,
    @SerialName("battery_level")
    val batteryLevel: Int? = null,
    @SerialName("battery_charging")
    val batteryCharging: Boolean? = null,
    val offline: Boolean = false,
) {
    companion object {
        fun from(smartlock: eu.kasify.model.PropertySmartlock): SmartLock =
            SmartLock(
                id = smartlock.id,
                propertyId = smartlock.propertyId,
                vendor = smartlock.vendor,
                name = smartlock.name,
                nukiSmartlock = smartlock.nukiSmartlock?.let { NukiSmartlock.from(it) },
                unifiSmartlock = smartlock.unifiSmartlock?.let { UnifiSmartlock.from(it) },
                lockState = smartlock.lockState,
                batteryLevel = smartlock.batteryLevel,
                batteryCharging = smartlock.batteryCharging,
            )
    }
}

@Serializable
data class NukiSmartlock(
    val id: Uuid,
    @SerialName("nuki_smartlock_id")
    val nukiSmartlockId: String,
) {
    companion object {
        fun from(nukiSmartlock: eu.kasify.model.NukiSmartlock): NukiSmartlock =
            NukiSmartlock(
                id = nukiSmartlock.id,
                nukiSmartlockId = nukiSmartlock.nukiSmartlockId,
            )
    }
}

@Serializable
data class UnifiSmartlock(
    val id: Uuid,
    @SerialName("floor_id")
    val floorId: String,
    @SerialName("door_id")
    val doorId: String,
) {
    companion object {
        fun from(unifiSmartlock: eu.kasify.model.UnifiSmartlock): UnifiSmartlock =
            UnifiSmartlock(
                id = unifiSmartlock.id,
                floorId = unifiSmartlock.floorId,
                doorId = unifiSmartlock.doorId,
            )
    }
}
