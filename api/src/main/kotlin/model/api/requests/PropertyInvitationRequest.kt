package eu.kasify.model.api.requests

import eu.kasify.db.model.PropertyAccessLevel
import eu.kasify.model.email.Locale
import eu.kasify.serialization.JavaLocalDateTimeSerializer
import kotlinx.serialization.Serializable
import java.time.LocalDateTime

@Serializable
data class CreatePropertyInvitationRequest(
    val email: String? = null,
    val maxUses: Int? = 1,
    @Serializable(with = JavaLocalDateTimeSerializer::class)
    val expiresAt: LocalDateTime? = null,
    val accessLevel: PropertyAccessLevel = PropertyAccessLevel.Contractor,
    val locale: Locale = Locale.EN,
)

@Serializable
data class AcceptInvitationRequest(
    val token: String,
)
