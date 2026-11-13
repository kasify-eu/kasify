package eu.kasify.routes

import eu.kasify.authenticatedAndRateLimited
import eu.kasify.db.IntegrationsRepository
import eu.kasify.db.PropertiesRepository
import eu.kasify.db.SmartlocksRepository
import eu.kasify.db.model.PropertyAccessLevel
import eu.kasify.model.api.SmartLock
import eu.kasify.model.api.requests.AddSmartlockRequest
import eu.kasify.routes.exceptions.BadParamRequestException
import eu.kasify.routes.exceptions.ConflictException
import eu.kasify.routes.exceptions.ForbiddenException
import eu.kasify.routes.exceptions.InternalServerException
import eu.kasify.routes.exceptions.NotFoundException
import eu.kasify.routes.extensions.getPathProperty
import eu.kasify.routes.extensions.getSmartlockIdParam
import eu.kasify.security.getKasifyUser
import eu.kasify.services.AuthorizationService
import eu.kasify.services.SmartlocksService
import eu.kasify.versionedRouting
import io.ktor.http.HttpStatusCode
import io.ktor.openapi.jsonSchema
import io.ktor.server.application.Application
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.openapi.describe
import io.ktor.server.routing.post
import io.ktor.utils.io.ExperimentalKtorApi
import org.koin.ktor.ext.inject

@OptIn(ExperimentalKtorApi::class)
fun Application.configureSmartLocksRouting() {
    val smartlocksRepository by inject<SmartlocksRepository>()
    val propertiesRepository by inject<PropertiesRepository>()
    val authorizationService by inject<AuthorizationService>()
    val smartlocksService by inject<SmartlocksService>()
    val integrationsRepository by inject<IntegrationsRepository>()

    versionedRouting {
        authenticatedAndRateLimited {
            get("/properties/{id}/smartlocks") {
                val user = call.getKasifyUser()
                val property = call.getPathProperty(propertiesRepository)

                authorizationService.requireAccess(user, property)

                val smartlocks = smartlocksService.getSmartLocksStatusByProperty(property.id)
                call.respond(smartlocks)
            }.describe {
                tag("SmartLocks")
                summary = "List property smart locks"
                description = "Returns every smart lock assigned to the property together with its current lock/battery status fetched from the underlying provider (Nuki or Unifi)."
                parameters {
                    path("id") { description = "Property id" }
                }
                responses {
                    HttpStatusCode.OK {
                        description = "Smart locks with their live status"
                        schema = jsonSchema<List<SmartLock>>()
                    }
                    HttpStatusCode.Forbidden { description = "Caller lacks access to the property" }
                    HttpStatusCode.NotFound { description = "Property not found" }
                }
            }

            // POST /properties/{id}/smartlocks - Add a smart lock (Nuki or Unifi) to a property
            post("/properties/{id}/smartlocks") {
                val user = call.getKasifyUser()
                val property = call.getPathProperty(propertiesRepository)

                val access = authorizationService.getAccess(user, property)
                if (!access.isAccountMember && access.accessLevel != PropertyAccessLevel.Manager) {
                    throw ForbiddenException("Manager access required.")
                }

                val request = call.receive<AddSmartlockRequest>()

                val smartlockId =
                    when {
                        request.nukiSmartlockId != null -> {
                            // Nuki smart lock flow. Integration lookup and duplicate checks are
                            // scoped to the property's account: a crew manager operates on the
                            // owner's integrations, not their own account's.
                            val nukiIntegration =
                                integrationsRepository.getNukiIntegrationById(
                                    property.accountId,
                                    requireNotNull(request.nukiIntegrationId),
                                ) ?: throw NotFoundException(Errors.ERROR_NUKI_NOT_CONFIGURED)

                            if (smartlocksRepository.isNukiSmartlockAssignedToAnyProperty(
                                    property.accountId,
                                    request.nukiSmartlockId,
                                )
                            ) {
                                throw ConflictException(Errors.ERROR_SMARTLOCK_ALREADY_ASSIGNED, code = "smartlock.already_assigned")
                            }

                            smartlocksRepository.createNukiSmartlock(
                                propertyId = property.id,
                                name = request.name,
                                nukiSmartlockId = request.nukiSmartlockId,
                                nukiIntegrationId = requireNotNull(nukiIntegration.id),
                            )
                        }

                        request.unifiDoorId != null && request.unifiFloorId != null -> {
                            // Unifi smart lock flow
                            if (smartlocksRepository.isUnifiSmartlockAssignedToAnyProperty(
                                    property.accountId,
                                    request.unifiDoorId,
                                )
                            ) {
                                throw ConflictException(Errors.ERROR_SMARTLOCK_ALREADY_ASSIGNED, code = "smartlock.already_assigned")
                            }

                            smartlocksRepository.createUnifiSmartlock(
                                propertyId = property.id,
                                name = request.name,
                                floorId = request.unifiFloorId,
                                doorId = request.unifiDoorId,
                            )
                        }

                        else -> {
                            throw BadParamRequestException(
                                "Must provide either nuki_smartlock_id or unifi_floor_id/unifi_door_id",
                                code = "smartlock.invalid_params",
                            )
                        }
                    }

                val smartlock =
                    smartlocksRepository.getSmartlockById(smartlockId)
                        ?: throw InternalServerException("Failed to retrieve created smartlock")

                call.respond(HttpStatusCode.Created, SmartLock.from(smartlock))
            }.describe {
                tag("SmartLocks")
                summary = "Assign smart lock"
                description = "Attaches either a Nuki smart lock (requires `nuki_smartlock_id` and `nuki_integration_id`) or a Unifi smart lock (requires `unifi_floor_id` and `unifi_door_id`) to the property. The Nuki integration must belong to the property's account."
                parameters {
                    path("id") { description = "Property id" }
                }
                requestBody {
                    description = "Smart lock details. Provide either the Nuki fields or the Unifi fields."
                    required = true
                    schema = jsonSchema<AddSmartlockRequest>()
                }
                responses {
                    HttpStatusCode.Created {
                        description = "The created smart lock"
                        schema = jsonSchema<SmartLock>()
                    }
                    HttpStatusCode.BadRequest {
                        description = "Neither Nuki nor Unifi fields were supplied. Returns `smartlock.invalid_params`"
                    }
                    HttpStatusCode.Forbidden { description = "Caller is not Owner or Manager of the property" }
                    HttpStatusCode.NotFound { description = "Property not found, or the referenced Nuki integration does not belong to the property's account" }
                    HttpStatusCode.Conflict {
                        description = "The smart lock is already assigned to another property. Returns `smartlock.already_assigned`"
                    }
                }
            }

            // DELETE /properties/{id}/smartlocks/{smartlockId} - Remove a smart lock from a property
            delete("/properties/{id}/smartlocks/{smartlockId}") {
                val user = call.getKasifyUser()
                val property = call.getPathProperty(propertiesRepository)

                val access = authorizationService.getAccess(user, property)
                if (!access.isAccountMember && access.accessLevel != PropertyAccessLevel.Manager) {
                    throw ForbiddenException("Manager access required.")
                }

                val smartlockId = call.getSmartlockIdParam()

                // Verify the smartlock exists and belongs to the property
                val smartlock =
                    smartlocksRepository.getSmartlockById(smartlockId)
                        ?: throw NotFoundException(Errors.ERROR_SMARTLOCK_NOT_FOUND)

                if (smartlock.propertyId != property.id) {
                    throw NotFoundException("Smartlock not found in this property")
                }

                val deleted = smartlocksRepository.deleteSmartlock(smartlockId)
                if (!deleted) {
                    throw NotFoundException(Errors.ERROR_SMARTLOCK_NOT_FOUND)
                }

                call.respond(HttpStatusCode.NoContent)
            }.describe {
                tag("SmartLocks")
                summary = "Remove smart lock"
                description = "Removes a smart lock from a property."
                parameters {
                    path("id") { description = "Property id" }
                    path("smartlockId") { description = "Smart lock id" }
                }
                responses {
                    HttpStatusCode.NoContent { description = "Smart lock removed" }
                    HttpStatusCode.Forbidden { description = "Caller is not Owner or Manager of the property" }
                    HttpStatusCode.NotFound { description = "Property or smart lock not found, or the smart lock does not belong to this property" }
                }
            }
        }
    }
}
