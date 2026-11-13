package eu.kasify.model.api

import eu.kasify.db.model.SmartlockVendor
import eu.kasify.model.PropertySmartlock
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
data class SmartLockInfo(
    val id: Uuid,
    val vendor: SmartlockVendor,
    val name: String,
) {
    companion object {
        fun from(smartlock: PropertySmartlock): SmartLockInfo =
            SmartLockInfo(
                id = smartlock.id,
                vendor = smartlock.vendor,
                name = smartlock.name,
            )
    }
}
