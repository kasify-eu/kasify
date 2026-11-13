package eu.kasify.routes.integrations
import eu.kasify.authenticatedAndRateLimited
import eu.kasify.db.IntegrationsRepository
import eu.kasify.model.api.responses.IntegrationItem
import eu.kasify.model.api.responses.IntegrationsResponse
import eu.kasify.model.api.responses.NukiIntegrationItem
import eu.kasify.model.api.responses.SesHospedajesIntegrationItem
import eu.kasify.model.api.responses.UnifiIntegrationItem
import eu.kasify.routes.exceptions.BadParamRequestException
import eu.kasify.routes.exceptions.NotFoundException
import eu.kasify.security.getKasifyUser
import eu.kasify.versionedRouting
import io.ktor.http.HttpStatusCode
import io.ktor.openapi.jsonSchema
import io.ktor.server.application.Application
import io.ktor.server.response.respond
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.openapi.describe
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import io.ktor.utils.io.ExperimentalKtorApi
import org.koin.ktor.ext.inject
import kotlin.uuid.Uuid

@OptIn(ExperimentalKtorApi::class)
fun Application.configureIntegrationRoutes() {
    val registry by inject<IntegrationRegistry>()
    val repository by inject<IntegrationsRepository>()

    versionedRouting {
        authenticatedAndRateLimited {
            get("/integrations") {
                val user = call.getKasifyUser()
                val integrations = repository.getAllIntegrationsByAccountId(user.accountId)

                val items: List<IntegrationItem> =
                    buildList {
                        addAll(integrations.nuki.map { NukiIntegrationItem.from(it) })
                        addAll(integrations.unifi.map { UnifiIntegrationItem.from(it) })
                        addAll(integrations.sesHospedajes.map { SesHospedajesIntegrationItem.from(it) })
                    }

                call.respond(IntegrationsResponse(integrations = items))
            }.describe {
                tag("Integrations")
                summary = "List all integrations"
                description = "Returns Nuki, Unifi, and SES Hospedajes integrations in a single envelope, distinguishable by the `type` discriminator on each item."
                responses {
                    HttpStatusCode.OK {
                        description = "All integrations across providers"
                        schema = jsonSchema<IntegrationsResponse>()
                    }
                    HttpStatusCode.Unauthorized { description = "The request is not authenticated" }
                }
            }

            route("/integrations/{integration}") {
                get {
                    val handler = call.resolveHandler(registry)
                    val user = call.getKasifyUser()
                    handler.list(call, user)
                }.describe {
                    tag("Integrations")
                    summary = "List integrations by type"
                    description = "Delegates to the integration handler registered for the `{integration}` slug (e.g. `nuki`, `unifi`, `ses`)."
                    parameters {
                        path("integration") { description = "Integration type slug (e.g. `nuki`, `unifi`, `ses_hospedajes`)" }
                    }
                    responses {
                        HttpStatusCode.OK { description = "Integrations of the requested type" }
                        HttpStatusCode.Unauthorized { description = "The request is not authenticated" }
                        HttpStatusCode.NotFound { description = "Unknown integration slug" }
                    }
                }

                post {
                    val handler = call.resolveHandler(registry)
                    val user = call.getKasifyUser()
                    handler.create(call, user)
                }.describe {
                    tag("Integrations")
                    summary = "Create integration"
                    description = "Delegates to the integration handler; see the provider-specific endpoints for request shapes."
                    parameters {
                        path("integration") { description = "Integration type slug" }
                    }
                    responses {
                        HttpStatusCode.Created { description = "The created integration" }
                        HttpStatusCode.BadRequest { description = "Provider-specific validation failed" }
                        HttpStatusCode.Unauthorized { description = "The request is not authenticated" }
                        HttpStatusCode.NotFound { description = "Unknown integration slug" }
                    }
                }

                route("/{id}") {
                    put {
                        val handler = call.resolveHandler(registry)
                        val user = call.getKasifyUser()
                        val id = call.parseIntegrationId()
                        handler.update(call, user, id)
                    }.describe {
                        tag("Integrations")
                        summary = "Update integration"
                        parameters {
                            path("integration") { description = "Integration type slug" }
                            path("id") { description = "Integration id (UUID)" }
                        }
                        responses {
                            HttpStatusCode.OK { description = "The updated integration" }
                            HttpStatusCode.BadRequest { description = "Malformed id or provider-specific validation failed" }
                            HttpStatusCode.Unauthorized { description = "The request is not authenticated" }
                            HttpStatusCode.NotFound { description = "Unknown integration slug or integration not found" }
                        }
                    }

                    patch("/settings") {
                        val handler = call.resolveHandler(registry)
                        val user = call.getKasifyUser()
                        val id = call.parseIntegrationId()
                        handler.updateSettings(call, user, id)
                    }.describe {
                        tag("Integrations")
                        summary = "Update integration settings"
                        description = "Partial update of provider-specific settings (e.g. feature toggles) that do not require re-supplying credentials. Only supported by providers that expose such settings (currently `ses-hospedajes`)."
                        parameters {
                            path("integration") { description = "Integration type slug" }
                            path("id") { description = "Integration id (UUID)" }
                        }
                        responses {
                            HttpStatusCode.OK { description = "The updated integration" }
                            HttpStatusCode.BadRequest { description = "Malformed id or request body" }
                            HttpStatusCode.Unauthorized { description = "The request is not authenticated" }
                            HttpStatusCode.NotFound { description = "Unknown integration slug, integration not found, or settings unsupported for this provider" }
                        }
                    }

                    delete {
                        val handler = call.resolveHandler(registry)
                        val user = call.getKasifyUser()
                        val id = call.parseIntegrationId()
                        handler.delete(call, user, id)
                    }.describe {
                        tag("Integrations")
                        summary = "Delete integration"
                        parameters {
                            path("integration") { description = "Integration type slug" }
                            path("id") { description = "Integration id (UUID)" }
                        }
                        responses {
                            HttpStatusCode.NoContent { description = "Integration deleted" }
                            HttpStatusCode.Unauthorized { description = "The request is not authenticated" }
                            HttpStatusCode.NotFound { description = "Unknown integration slug or integration not found" }
                        }
                    }
                }
            }
        }
    }
}

private fun io.ktor.server.application.ApplicationCall.resolveHandler(registry: IntegrationRegistry): IntegrationHandler {
    val integration =
        parameters["integration"]
            ?: throw NotFoundException("Integration not specified")
    return registry.get(integration)
        ?: throw NotFoundException("Unknown integration: $integration")
}

private fun io.ktor.server.application.ApplicationCall.parseIntegrationId(): Uuid =
    parameters["id"]?.let {
        try {
            Uuid.parse(it)
        } catch (_: Exception) {
            throw BadParamRequestException("Integration ID has an incorrect format")
        }
    } ?: throw NotFoundException("Integration ID not specified")
