package eu.kasify.model.api.responses

import eu.kasify.model.api.PropertyMember
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
data class PropertyMembersResponse(
    val crew: List<PropertyMember>,
    @SerialName("default_cleaner_id")
    val defaultCleanerId: Uuid? = null,
)
