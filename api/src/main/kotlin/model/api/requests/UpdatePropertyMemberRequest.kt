package eu.kasify.model.api.requests

import eu.kasify.db.model.PropertyAccessLevel
import kotlinx.serialization.Serializable

@Serializable
data class UpdatePropertyMemberRequest(
    val accessLevel: PropertyAccessLevel,
)
