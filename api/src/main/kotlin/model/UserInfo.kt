package eu.kasify.model

import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
data class UserInfo(
    var id: Uuid,
    val accountId: Uuid,
)
