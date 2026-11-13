package eu.kasify.db
import eu.kasify.db.model.PropertyAccessLevel
import eu.kasify.db.model.PropertyAccessTable
import eu.kasify.db.model.PropertyInvitationsTable
import eu.kasify.model.InvitationRedemption
import eu.kasify.model.PropertyInvitation
import eu.kasify.model.email.Locale
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.plus
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import java.security.SecureRandom
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.Base64
import kotlin.uuid.Uuid

open class PropertyInvitationsRepository {
    private val secureRandom = SecureRandom()

    /**
     * Create a new crew invitation
     */
    open suspend fun create(
        propertyId: Uuid,
        email: String? = null,
        maxUses: Int? = 1,
        expiresAt: LocalDateTime? = null,
        accessLevel: PropertyAccessLevel = PropertyAccessLevel.Contractor,
        locale: Locale = Locale.EN,
    ): PropertyInvitation {
        val token = generateToken()
        val now = OffsetDateTime.now(ZoneOffset.UTC)

        dbQuery {
            PropertyInvitationsTable.insert {
                it[PropertyInvitationsTable.propertyId] = propertyId
                it[PropertyInvitationsTable.email] = email
                it[PropertyInvitationsTable.token] = token
                it[PropertyInvitationsTable.maxUses] = maxUses
                it[PropertyInvitationsTable.uses] = 0
                it[PropertyInvitationsTable.expiresAt] =
                    expiresAt?.atOffset(ZoneOffset.UTC)
                it[PropertyInvitationsTable.createdAt] = now
                it[PropertyInvitationsTable.acceptedAt] = null
                it[PropertyInvitationsTable.accessLevel] = accessLevel
                it[PropertyInvitationsTable.locale] = locale.tag
            }
        }

        return findByToken(token)!!
    }

    /**
     * Find an invitation by its token
     */
    open suspend fun findByToken(token: String): PropertyInvitation? =
        dbQuery {
            PropertyInvitationsTable
                .selectAll()
                .where { PropertyInvitationsTable.token eq token }
                .singleOrNull()
                ?.let { PropertyInvitation.from(it) }
        }

    /**
     * Find an invitation by its ID
     */
    open suspend fun findById(id: Uuid): PropertyInvitation? =
        dbQuery {
            PropertyInvitationsTable
                .selectAll()
                .where { PropertyInvitationsTable.id eq id }
                .singleOrNull()
                ?.let { PropertyInvitation.from(it) }
        }

    /**
     * Find all invitations for a property
     */
    open suspend fun findByPropertyId(propertyId: Uuid): List<PropertyInvitation> =
        dbQuery {
            PropertyInvitationsTable
                .selectAll()
                .where { PropertyInvitationsTable.propertyId eq propertyId }
                .map { PropertyInvitation.from(it) }
        }

    /**
     * Atomically redeems one use of the invitation for [userId]: in a single transaction it locks
     * the invitation row (`SELECT ... FOR UPDATE`), re-checks that it is still valid, grants the
     * user access to the invitation's property at the invitation's access level, increments the
     * usage count and sets acceptedAt on first use.
     *
     * Concurrent redemptions of the same token serialize on the row lock, so at most `max_uses`
     * of them can ever succeed. If the grant fails, the transaction rolls back and no use is consumed.
     */
    open suspend fun redeem(
        token: String,
        userId: Uuid,
    ): InvitationRedemption =
        dbQuery {
            val invitation =
                PropertyInvitationsTable
                    .selectAll()
                    .where { PropertyInvitationsTable.token eq token }
                    .forUpdate()
                    .singleOrNull()
                    ?.let { PropertyInvitation.from(it) }
                    ?: return@dbQuery InvitationRedemption.NotFound

            if (!invitation.isValid) {
                return@dbQuery InvitationRedemption.Invalid
            }

            PropertyAccessTable.insert {
                it[PropertyAccessTable.userId] = userId
                it[PropertyAccessTable.propertyId] = invitation.propertyId
                it[PropertyAccessTable.service] = null
                it[PropertyAccessTable.accessLevel] = invitation.accessLevel
            }

            PropertyInvitationsTable.update({ PropertyInvitationsTable.id eq invitation.id }) {
                it[uses] = uses + 1
                if (invitation.acceptedAt == null) {
                    it[acceptedAt] = OffsetDateTime.now(ZoneOffset.UTC)
                }
            }

            InvitationRedemption.Redeemed
        }

    /**
     * Delete an invitation by ID
     */
    open suspend fun delete(id: Uuid): Boolean =
        dbQuery {
            PropertyInvitationsTable.deleteWhere {
                PropertyInvitationsTable.id eq id
            } > 0
        }

    /**
     * Delete an invitation by token
     */
    open suspend fun deleteByToken(token: String): Boolean =
        dbQuery {
            PropertyInvitationsTable.deleteWhere {
                PropertyInvitationsTable.token eq token
            } > 0
        }

    /**
     * Generate a secure random token for the invitation link
     */
    private fun generateToken(): String {
        val bytes = ByteArray(16)
        secureRandom.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}
