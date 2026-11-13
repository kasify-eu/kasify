package eu.kasify.plugins.nuki.model

import kotlinx.serialization.Serializable

@Serializable
data class NukiLockState(
    val state: Int,
    val batteryCritical: Boolean,
    val batteryCharging: Boolean? = null,
    val batteryCharge: Int? = null,
    val keypadBatteryCritical: Boolean? = null,
)
