package eu.kasify.model.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class NukiStatus {
    @SerialName("online")
    Online,

    @SerialName("unauthorized")
    Unauthorized,

    @SerialName("unreachable")
    Unreachable,
}
