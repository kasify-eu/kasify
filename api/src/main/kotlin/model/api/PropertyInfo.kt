package eu.kasify.model.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PropertyInfo(
    val name: String,
    val address: String,
    val city: String,
    @SerialName("zip_code")
    val zipcode: String,
    @SerialName("cover_image_url")
    val coverImageUrl: String? = null,
)
