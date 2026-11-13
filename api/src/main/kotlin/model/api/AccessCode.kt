package eu.kasify.model.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
class AccessCode(
    val id: Uuid,
    @SerialName("access_code")
    val accessCode: String,
    @SerialName("smartlock_id")
    val smartlockId: Uuid,
    @SerialName("smartlock_name")
    val smartlockName: String,
)
