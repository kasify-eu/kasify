package eu.kasify.model
import eu.kasify.db.model.PropertyAccessLevel
import eu.kasify.db.model.PropertyInvitationsTable
import eu.kasify.model.email.Locale
import eu.kasify.serialization.JavaLocalDateTimeSerializer
import io.perracodex.exposed.pagination.MapModel
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.ResultRow
import java.time.LocalDateTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** Outcome of atomically redeeming one use of an invitation (`PropertyInvitationsRepository.redeem`). */
enum class InvitationRedemption {
    /** Access was granted and one use of the invitation was consumed. */
    Redeemed,

    /** No invitation exists for the token (e.g. it was revoked concurrently). Nothing was granted. */
    NotFound,

    /** The invitation is expired or has no uses left (e.g. a concurrent accept took the last use). Nothing was granted. */
    Invalid,
}

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
    val isExpired: Boolean
        get() =
            expiresAt != null &&
                expiresAt < LocalDateTime.now()

    val isExhausted: Boolean
        get() = maxUses != null && uses >= maxUses

    val isValid: Boolean
        get() = !isExpired && !isExhausted

    @OptIn(ExperimentalUuidApi::class)
    companion object : MapModel<PropertyInvitation> {
        override fun from(row: ResultRow): PropertyInvitation =
            PropertyInvitation(
                id = row[PropertyInvitationsTable.id],
                propertyId = row[PropertyInvitationsTable.propertyId],
                email = row[PropertyInvitationsTable.email],
                token = row[PropertyInvitationsTable.token],
                maxUses = row[PropertyInvitationsTable.maxUses],
                uses = row[PropertyInvitationsTable.uses],
                expiresAt = row[PropertyInvitationsTable.expiresAt]?.toLocalDateTime(),
                createdAt = row[PropertyInvitationsTable.createdAt].toLocalDateTime(),
                acceptedAt = row[PropertyInvitationsTable.acceptedAt]?.toLocalDateTime(),
                accessLevel = row[PropertyInvitationsTable.accessLevel],
                locale = Locale.fromTag(row[PropertyInvitationsTable.locale]),
            )
    }
}
