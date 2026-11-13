package eu.kasify.model.api

import eu.kasify.db.model.PropertyAccessLevel
import eu.kasify.model.email.Locale
import eu.kasify.serialization.JavaLocalDateTimeSerializer
import kotlinx.serialization.Serializable
import java.time.LocalDateTime
import kotlin.uuid.Uuid
import eu.kasify.model.PropertyInvitation as DomainPropertyInvitation

@Serializable
data class PropertyInvitation(
    val id: Uuid,
    val propertyId: Uuid,
    val email: String?,
    val token: String,
    val maxUses: Int?,
    val uses: Int,
    @Serializable(with = JavaLocalDateTimeSerializer::class)
    val expiresAt: LocalDateTime?,
    @Serializable(with = JavaLocalDateTimeSerializer::class)
    val createdAt: LocalDateTime,
    @Serializable(with = JavaLocalDateTimeSerializer::class)
    val acceptedAt: LocalDateTime?,
    val accessLevel: PropertyAccessLevel,
    val locale: Locale,
) {
    companion object {
        fun from(inv: DomainPropertyInvitation) =
            PropertyInvitation(
                id = inv.id,
                propertyId = inv.propertyId,
                email = inv.email,
                token = inv.token,
                maxUses = inv.maxUses,
                uses = inv.uses,
                expiresAt = inv.expiresAt,
                createdAt = inv.createdAt,
                acceptedAt = inv.acceptedAt,
                accessLevel = inv.accessLevel,
                locale = inv.locale,
            )
    }
}
