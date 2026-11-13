package eu.kasify.routes.integrations

import eu.kasify.authenticatedAndRateLimited
import eu.kasify.db.IntegrationsRepository
import eu.kasify.db.SmartlocksRepository
import eu.kasify.model.api.responses.AssignedProperty
import eu.kasify.model.api.responses.UnifiSmartlock
import eu.kasify.model.api.responses.UnifiStatus
import eu.kasify.model.api.responses.UnifilocksResponse
import eu.kasify.plugins.unifi.UnifiAuthenticationException
import eu.kasify.routes.Errors
import eu.kasify.routes.exceptions.BadParamRequestException
import eu.kasify.routes.exceptions.NotFoundException
import eu.kasify.security.getKasifyUser
import eu.kasify.services.UnifiSessionManager
import eu.kasify.versionedRouting
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.HttpStatusCode
import io.ktor.openapi.jsonSchema
import io.ktor.server.application.Application
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.openapi.describe
import io.ktor.utils.io.ExperimentalKtorApi
import org.koin.ktor.ext.inject
import kotlin.uuid.Uuid

private val logger = KotlinLogging.logger {}

@OptIn(ExperimentalKtorApi::class)
fun Application.configureUnifiSmartlockRoutes() {
    val service by inject<IntegrationsRepository>()
    val smartlocksRepository by inject<SmartlocksRepository>()
    val unifiSessionManager by inject<UnifiSessionManager>()

    versionedRouting {
        authenticatedAndRateLimited {
            get("/integrations/unifi/{id}/smartlocks") {
                val user = call.getKasifyUser()
                val integrationId =
                    call.parameters["id"]?.let {
                        try {
                            Uuid.parse(it)
                        } catch (_: Exception) {
                            throw BadParamRequestException("Integration ID has an incorrect format")
                        }
                    } ?: throw NotFoundException(Errors.ERROR_INTEGRATION_NOT_FOUND)

                val unifiIntegration =
                    service.getUnifiIntegrationById(user.accountId, integrationId)
                        ?: throw NotFoundException(Errors.ERROR_UNIFI_NOT_CONFIGURED)

                // Mirror Nuki: report credential/connectivity problems as a status instead of failing
                // the request, so the dashboard can tell "bad credentials" from "no locks".
                val doors =
                    try {
                        unifiSessionManager.executeWithRetry(user.accountId) { plugin ->
                            plugin.getDoors()
                        }
                    } catch (e: UnifiAuthenticationException) {
                        logger.warn(e) { "Unifi credentials rejected for account=${user.accountId}" }
                        return@get call.respond(
                            UnifilocksResponse(smartlocks = emptyList(), unifiStatus = UnifiStatus.Unauthorized),
                        )
                    } catch (e: Exception) {
                        logger.warn(e) { "Unifi unreachable for account=${user.accountId}" }
                        return@get call.respond(
                            UnifilocksResponse(smartlocks = emptyList(), unifiStatus = UnifiStatus.Unreachable),
                        )
                    }

                val assignments = smartlocksRepository.getUnifiSmartlocksByAccount(user.accountId)
                val assignmentsByDoorId = assignments.associateBy { it.doorId }

                val smartLocks =
                    doors.map { door ->
                        val assignment = assignmentsByDoorId[door.resourceId]
                        UnifiSmartlock(
                            floorId = door.floorId,
                            doorId = door.resourceId,
                            name = door.resourceName,
                            assignedProperty =
                                assignment?.let {
                                    AssignedProperty(
                                        propertyId = it.propertyId,
                                        propertyName = it.propertyName,
                                        smartlockRecordId = it.smartlockRecordId,
                                    )
                                },
                        )
                    }

                call.respond(UnifilocksResponse(smartlocks = smartLocks))
            }.describe {
                tag("Integrations")
                summary = "List Unifi doors"
                description = "Fetches every door exposed by the Unifi controller for this integration and annotates each with the Kasify property it is already assigned to, if any."
                parameters {
                    path("id") { description = "Unifi integration id (UUID)" }
                }
                responses {
                    HttpStatusCode.OK {
                        description = "Doors exposed by the Unifi integration"
                        schema = jsonSchema<UnifilocksResponse>()
                    }
                    HttpStatusCode.BadRequest { description = "Malformed integration id" }
                    HttpStatusCode.Unauthorized { description = "The request is not authenticated" }
                    HttpStatusCode.NotFound { description = "Integration not found or not owned by the caller's account" }
                }
            }
        }
    }
}
