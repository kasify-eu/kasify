package eu.kasify.plugins.nuki.model

import kotlinx.serialization.Serializable

@Serializable
data class NukiAccessCode(
    val id: String,
    val smartlockId: Long,
    val authId: Long,
    val code: Long?,
    val type: Int,
    val name: String,
    val enabled: Boolean,
    val remoteAllowed: Boolean,
    val lockCount: Int,
    val lastActiveDate: String?,
    val creationDate: String,
    val updateDate: String?,
)
