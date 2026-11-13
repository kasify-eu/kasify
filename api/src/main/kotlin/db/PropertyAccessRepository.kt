package eu.kasify.db
import eu.kasify.db.model.PropertyAccessLevel
import eu.kasify.db.model.PropertyAccessTable
import eu.kasify.db.model.PropertyService
import eu.kasify.db.model.UsersTable
import eu.kasify.model.PropertyAccess
import eu.kasify.model.api.PropertyMember
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.uuid.Uuid

open class PropertyAccessRepository {
    /**
     * Get all property access grants for a user
     */
    open suspend fun findByUserId(userId: Uuid): List<PropertyAccess> =
        dbQuery {
            PropertyAccessTable
                .selectAll()
                .where { PropertyAccessTable.userId eq userId }
                .map { PropertyAccess.from(it) }
        }

    /**
     * Get all property members for a property
     */
    open suspend fun findByPropertyId(propertyId: Uuid): List<PropertyAccess> =
        dbQuery {
            PropertyAccessTable
                .selectAll()
                .where { PropertyAccessTable.propertyId eq propertyId }
                .map { PropertyAccess.from(it) }
        }

    /**
     * Check if a user has member access to a specific property
     */
    open suspend fun hasAccess(
        userId: Uuid,
        propertyId: Uuid,
    ): PropertyAccess? =
        dbQuery {
            PropertyAccessTable
                .selectAll()
                .where {
                    (PropertyAccessTable.userId eq userId) and
                        (PropertyAccessTable.propertyId eq propertyId)
                }.singleOrNull()
                ?.let { PropertyAccess.from(it) }
        }

    /**
     * Grant member access to a property
     */
    open suspend fun grant(
        userId: Uuid,
        propertyId: Uuid,
        service: PropertyService? = null,
        accessLevel: PropertyAccessLevel = PropertyAccessLevel.Contractor,
    ): PropertyAccess {
        dbQuery {
            PropertyAccessTable.insert {
                it[PropertyAccessTable.userId] = userId
                it[PropertyAccessTable.propertyId] = propertyId
                it[PropertyAccessTable.service] = service
                it[PropertyAccessTable.accessLevel] = accessLevel
            }[PropertyAccessTable.id]
        }

        return hasAccess(userId, propertyId)!!
    }

    /**
     * Update the access level for a property member
     */
    open suspend fun updateAccessLevel(
        userId: Uuid,
        propertyId: Uuid,
        accessLevel: PropertyAccessLevel,
    ): Boolean =
        dbQuery {
            PropertyAccessTable.update({
                (PropertyAccessTable.userId eq userId) and
                    (PropertyAccessTable.propertyId eq propertyId)
            }) {
                it[PropertyAccessTable.accessLevel] = accessLevel
            } > 0
        }

    /**
     * Revoke member access to a property
     */
    open suspend fun revoke(
        userId: Uuid,
        propertyId: Uuid,
    ): Boolean =
        dbQuery {
            PropertyAccessTable.deleteWhere {
                (PropertyAccessTable.userId eq userId) and
                    (PropertyAccessTable.propertyId eq propertyId)
            } > 0
        }

    /**
     * Get all property IDs a user has member access to
     */
    open suspend fun getAccessiblePropertyIds(userId: Uuid): List<Uuid> =
        dbQuery {
            PropertyAccessTable
                .select(PropertyAccessTable.propertyId)
                .where { PropertyAccessTable.userId eq userId }
                .map { it[PropertyAccessTable.propertyId] }
        }

    /**
     * Get all property members for a property with user details
     */
    open suspend fun getPropertyMembersByPropertyId(propertyId: Uuid): List<PropertyMember> =
        dbQuery {
            (PropertyAccessTable innerJoin UsersTable)
                .selectAll()
                .where { PropertyAccessTable.propertyId eq propertyId }
                .map {
                    PropertyMember(
                        id = it[UsersTable.id],
                        name = it[UsersTable.name],
                        lastName = it[UsersTable.lastName],
                        email = it[UsersTable.email],
                        service = it[PropertyAccessTable.service],
                        accessLevel = it[PropertyAccessTable.accessLevel],
                    )
                }
        }
}
