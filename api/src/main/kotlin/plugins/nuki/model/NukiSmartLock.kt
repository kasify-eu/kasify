package eu.kasify.plugins.nuki.model

import kotlinx.serialization.Serializable

@Serializable
data class NukiSmartLock(
    val smartlockId: Long,
    val accountId: Long,
    val type: Int,
    val authId: Long,
    val name: String,
    val state: NukiLockState,
    val config: NukiConfig,
    val serverState: Int,
)
