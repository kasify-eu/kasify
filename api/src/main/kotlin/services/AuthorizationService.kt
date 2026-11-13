package eu.kasify.services

import eu.kasify.db.PropertiesRepository
import eu.kasify.db.PropertyAccessRepository
import eu.kasify.db.model.PropertyAccessLevel
import eu.kasify.model.Property
import eu.kasify.routes.exceptions.ForbiddenException
import eu.kasify.security.KasifyUserPrincipal
import kotlin.uuid.Uuid

/**
 * Access information for a user's access to a property.
 */
data class AccessInfo(
    val hasAccess: Boolean,
    val role: String, // 'owner' | 'staff' | 'contractor' | 'manager'
    val isAccountMember: Boolean,
    val accessLevel: PropertyAccessLevel? = null,
)

open class AuthorizationService(
    private val propertyAccessRepository: PropertyAccessRepository,
    private val propertiesRepository: PropertiesRepository,
) {
    /**
     * Get access information for a user and property.
     * - Account members (user.accountId == property.accountId) are owners
     * - Users with property_access records are crew
     */
    suspend fun getAccess(
        user: KasifyUserPrincipal,
        property: Property,
    ): AccessInfo {
        // Account member = owner (full access)
        if (user.accountId == property.accountId) {
            return AccessInfo(hasAccess = true, role = "owner", isAccountMember = true, accessLevel = null)
        }

        // Check member access via property_access table
        val grant = propertyAccessRepository.hasAccess(user.userId, property.id)
        return if (grant != null) {
            AccessInfo(hasAccess = true, role = grant.accessLevel.dbValue, isAccountMember = false, accessLevel = grant.accessLevel)
        } else {
            AccessInfo(hasAccess = false, role = "none", isAccountMember = false, accessLevel = null)
        }
    }

    /**
     * Require any access to the property (owner or crew).
     * Throws ForbiddenException if user has no access.
     */
    suspend fun requireAccess(
        user: KasifyUserPrincipal,
        property: Property,
    ) {
        val access = getAccess(user, property)
        if (!access.hasAccess) {
            throw ForbiddenException("Access to property denied.")
        }
    }

    /**
     * Require owner access to the property.
     * Throws ForbiddenException if user is not an account member (owner).
     */
    suspend fun requireOwner(
        user: KasifyUserPrincipal,
        property: Property,
    ) {
        val access = getAccess(user, property)
        if (!access.isAccountMember) {
            throw ForbiddenException("Owner access required.")
        }
    }

    /**
     * Whether the caller may see a cleaning task on a booking payload.
     * Owners and managers always can; staff can only see tasks assigned to them.
     */
    fun canSeeCleaningTask(
        access: AccessInfo,
        userId: Uuid,
        assignedTo: Uuid,
    ): Boolean =
        access.isAccountMember ||
            access.accessLevel == PropertyAccessLevel.Manager ||
            (access.accessLevel == PropertyAccessLevel.Staff && assignedTo == userId)

    /**
     * Access map for properties that are already loaded, avoiding the per-id refetch
     * done by [getAccessForProperties]. Use for list endpoints that hold the domain rows.
     */
    suspend fun getAccessForLoadedProperties(
        user: KasifyUserPrincipal,
        properties: List<Property>,
    ): Map<Uuid, AccessInfo> {
        if (properties.isEmpty()) return emptyMap()

        val accessByProperty =
            propertyAccessRepository
                .findByUserId(user.userId)
                .associateBy { it.propertyId }

        return properties.associate { property ->
            property.id to
                when {
                    property.accountId == user.accountId ->
                        AccessInfo(hasAccess = true, role = "owner", isAccountMember = true)

                    property.id in accessByProperty -> {
                        val grant = accessByProperty.getValue(property.id)
                        AccessInfo(
                            hasAccess = true,
                            role = grant.accessLevel.dbValue,
                            isAccountMember = false,
                            accessLevel = grant.accessLevel,
                        )
                    }

                    else -> AccessInfo(hasAccess = false, role = "none", isAccountMember = false)
                }
        }
    }

    /**
     * Get access information for a user across multiple properties.
     * Efficiently fetches access for all properties in a single batch.
     *
     * Use this for endpoints like /calendar that return items from multiple properties.
     *
     * @param user The user principal
     * @param propertyIds List of property IDs to check access for
     * @return Map of property ID to AccessInfo
     */
    suspend fun getAccessForProperties(
        user: KasifyUserPrincipal,
        propertyIds: List<Uuid>,
    ): Map<Uuid, AccessInfo> {
        if (propertyIds.isEmpty()) return emptyMap()

        val uniquePropertyIds = propertyIds.distinct()

        // 1. Get all properties to check account ownership
        val properties = uniquePropertyIds.mapNotNull { propertiesRepository.getProperty(it) }
        val ownedPropertyIds =
            properties
                .filter { it.accountId == user.accountId }
                .mapNotNull { it.id }
                .toSet()

        // 2. Get member access for user
        val memberAccessList = propertyAccessRepository.findByUserId(user.userId)
        val accessByProperty = memberAccessList.associateBy { it.propertyId }

        // 3. Build access map
        return uniquePropertyIds.associateWith { propertyId ->
            when {
                propertyId in ownedPropertyIds ->
                    AccessInfo(
                        hasAccess = true,
                        role = "owner",
                        isAccountMember = true,
                        accessLevel = null,
                    )

                propertyId in accessByProperty ->
                    AccessInfo(
                        hasAccess = true,
                        role = accessByProperty[propertyId]!!.accessLevel.dbValue,
                        isAccountMember = false,
                        accessLevel = accessByProperty[propertyId]!!.accessLevel,
                    )

                else ->
                    AccessInfo(
                        hasAccess = false,
                        role = "none",
                        isAccountMember = false,
                        accessLevel = null,
                    )
            }
        }
    }
}
