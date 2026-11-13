package eu.kasify.routes.integrations

import eu.kasify.authenticatedAndRateLimited
import eu.kasify.db.IntegrationsRepository
import eu.kasify.db.SmartlocksRepository
import eu.kasify.model.api.NukiStatus
import eu.kasify.model.api.responses.AssignedProperty
import eu.kasify.model.api.responses.AvailableNukiSmartlock
import eu.kasify.model.api.responses.NukiSmartlocksResponse
import eu.kasify.plugins.PluginFactory
import eu.kasify.plugins.nuki.NukiException
import eu.kasify.plugins.nuki.NukiUnauthorizedException
import eu.kasify.routes.Errors
import eu.kasify.routes.exceptions.BadParamRequestException
import eu.kasify.routes.exceptions.NotFoundException
import eu.kasify.security.getKasifyUser
import eu.kasify.versionedRouting
import io.ktor.http.HttpStatusCode
import io.ktor.openapi.jsonSchema
import io.ktor.server.application.Application
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.openapi.describe
import io.ktor.utils.io.ExperimentalKtorApi
import org.koin.ktor.ext.inject
import kotlin.uuid.Uuid

@OptIn(ExperimentalKtorApi::class)
fun Application.configureNukiSmartlockRoutes() {
    val service by inject<IntegrationsRepository>()
    val smartlocksRepository by inject<SmartlocksRepository>()
    val pluginFactory by inject<PluginFactory>()

    versionedRouting {
        authenticatedAndRateLimited {
            get("/integrations/nuki/{id}/smartlocks") {
                val user = call.getKasifyUser()
                val integrationId =
                    call.parameters["id"]?.let {
                        try {
                            Uuid.parse(it)
                        } catch (_: Exception) {
                            throw BadParamRequestException("Integration ID has an incorrect format")
                        }
                    } ?: throw NotFoundException(Errors.ERROR_INTEGRATION_NOT_FOUND)

                val integration =
                    service.getNukiIntegrationById(user.accountId, integrationId)
                        ?: throw NotFoundException(Errors.ERROR_NUKI_NOT_CONFIGURED)

                val plugin = pluginFactory.createNukiPlugin(integration.nukiToken)

                val assignments = smartlocksRepository.getNukiSmartlocksByAccount(user.accountId)

                val offlineLocks by lazy {
                    assignments.map { assignment ->
                        AvailableNukiSmartlock(
                            smartlockId = assignment.nukiSmartlockId.toLong(),
                            name = assignment.name,
                            lockState = null,
                            batteryCharge = null,
                            batteryCritical = false,
                            offline = true,
                            assignedProperty =
                                AssignedProperty(
                                    propertyId = assignment.propertyId,
                                    propertyName = assignment.propertyName,
                                    smartlockRecordId = assignment.smartlockRecordId,
                                ),
                        )
                    }
                }

                val nukiSmartlocks =
                    try {
                        plugin.getSmartLocks()
                    } catch (_: NukiUnauthorizedException) {
                        return@get call.respond(
                            NukiSmartlocksResponse(smartlocks = offlineLocks, nukiStatus = NukiStatus.Unauthorized),
                        )
                    } catch (_: NukiException) {
                        return@get call.respond(
                            NukiSmartlocksResponse(smartlocks = offlineLocks, nukiStatus = NukiStatus.Unreachable),
                        )
                    }

                val assignmentsByNukiId = assignments.associateBy { it.nukiSmartlockId }

                val availableLocks =
                    nukiSmartlocks.map { lock ->
                        val assignment = assignmentsByNukiId[lock.smartlockId.toString()]
                        AvailableNukiSmartlock(
                            smartlockId = lock.smartlockId,
                            name = lock.name,
                            lockState = lock.state.state,
                            batteryCharge = lock.state.batteryCharge,
                            batteryCritical = lock.state.batteryCritical,
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

                call.respond(NukiSmartlocksResponse(smartlocks = availableLocks))
            }.describe {
                tag("Integrations")
                summary = "List Nuki smart locks"
                description = "Calls the Nuki Web API with the integration's token and returns every lock the account can see, annotated with which (if any) Kasify property each lock is already assigned to. Falls back to offline data with a `nuki_status` of `unauthorized` or `unreachable` when the API call fails."
                parameters {
                    path("id") { description = "Nuki integration id (UUID)" }
                }
                responses {
                    HttpStatusCode.OK {
                        description = "Smart locks exposed by the integration"
                        schema = jsonSchema<NukiSmartlocksResponse>()
                    }
                    HttpStatusCode.BadRequest { description = "Malformed integration id" }
                    HttpStatusCode.Unauthorized { description = "The request is not authenticated" }
                    HttpStatusCode.NotFound { description = "Integration not found or not owned by the caller's account" }
                }
            }
        }
    }
}
