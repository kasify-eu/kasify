package eu.kasify.model.api.responses

import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
data class AcceptInvitationResponse(
    val propertyId: Uuid,
    val propertyName: String,
)
