package eu.kasify.plugins.unifi.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UnifiApiResponse<T>(
    val code: String,
    val msg: String,
    val data: T? = null,
)

@Serializable
data class UnifiApiResponseV2<T>(
    val code: Int,
    @SerialName("codeS")
    val codeS: String,
    val msg: String,
    val error: String = "",
    val data: T? = null,
)
