package eu.kasify.routes.extensions

import eu.kasify.db.PropertiesRepository
import eu.kasify.db.PropertyAccessRepository
import eu.kasify.db.UsersRepository
import eu.kasify.model.Property
import eu.kasify.routes.exceptions.NotFoundException
import io.ktor.server.application.ApplicationCall
import kotlin.uuid.Uuid

suspend fun ApplicationCall.getPathProperty(propertiesService: PropertiesRepository): Property {
    val propertyId = getPropertyId()

    val property = propertiesService.getProperty(propertyId) ?: throw NotFoundException("Property not found")

    return property
}

suspend fun ApplicationCall.getProperty(
    propertiesService: PropertiesRepository,
    propertyId: Uuid,
): Property {
    val property = propertiesService.getProperty(propertyId) ?: throw NotFoundException("Property not found")

    return property
}

/**
 * Whether [userId] can be given work on [property] (task assignee, default cleaner): an owner —
 * a user of the property's account — or a crew member holding a `property_access` row for it.
 * An unknown user id is not assignable.
 */
suspend fun isAssignableToProperty(
    userId: Uuid,
    property: Property,
    usersRepository: UsersRepository,
    propertyAccessRepository: PropertyAccessRepository,
): Boolean =
    usersRepository.getUserById(userId)?.accountId == property.accountId ||
        propertyAccessRepository.hasAccess(userId, property.id) != null
