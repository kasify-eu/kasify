package eu.kasify.model.api.responses

import eu.kasify.model.api.PropertyInvitation
import kotlinx.serialization.Serializable

@Serializable
data class PropertyInvitationsResponse(
    val invitations: List<PropertyInvitation>,
)
