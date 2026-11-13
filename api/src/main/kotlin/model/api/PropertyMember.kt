package eu.kasify.model.api

import eu.kasify.db.model.PropertyAccessLevel
import eu.kasify.db.model.PropertyService
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
data class PropertyMember(
    val id: Uuid,
    val name: String,
    @SerialName("last_name")
    val lastName: String,
    val email: String,
    @SerialName("access_level")
    val accessLevel: PropertyAccessLevel,
    val service: PropertyService? = null,
)
