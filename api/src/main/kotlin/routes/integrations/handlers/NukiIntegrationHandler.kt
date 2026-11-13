package eu.kasify.routes.integrations.handlers
import eu.kasify.db.IntegrationsRepository
import eu.kasify.model.api.NukiIntegration
import eu.kasify.model.api.requests.NukiIntegrationRequest
import eu.kasify.plugins.PluginFactory
import eu.kasify.plugins.nuki.NukiException
import eu.kasify.routes.Errors
import eu.kasify.routes.exceptions.ForbiddenException
import eu.kasify.routes.exceptions.NotFoundException
import eu.kasify.routes.integrations.IntegrationHandler
import eu.kasify.security.KasifyUserPrincipal
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import kotlin.uuid.Uuid

class NukiIntegrationHandler(
    private val repository: IntegrationsRepository,
    private val pluginFactory: PluginFactory,
) : IntegrationHandler {
    override val pathSegment: String = "nuki"

    override suspend fun list(
        call: ApplicationCall,
        user: KasifyUserPrincipal,
    ) {
        val integrations = repository.getNukiIntegrationsByAccountId(user.accountId)
        call.respond(integrations.map { NukiIntegration.from(it) })
    }

    override suspend fun create(
        call: ApplicationCall,
        user: KasifyUserPrincipal,
    ) {
        val request = call.receive<NukiIntegrationRequest>()

        val plugin = pluginFactory.createNukiPlugin(request.nukiToken)
        try {
            plugin.getSmartLocks()
        } catch (_: NukiException) {
            throw ForbiddenException("Nuki Token is not valid", code = "integration.invalid_credentials")
        }

        val newIntegration =
            repository.createNukiIntegration(
                accountId = user.accountId,
                nukiToken = request.nukiToken,
            )
        call.respond(HttpStatusCode.Created, NukiIntegration.from(newIntegration))
    }

    override suspend fun update(
        call: ApplicationCall,
        user: KasifyUserPrincipal,
        id: Uuid,
    ) {
        val request = call.receive<NukiIntegrationRequest>()

        val updated =
            repository.updateNukiIntegration(
                accountId = user.accountId,
                integrationId = id,
                nukiToken = request.nukiToken,
            )

        if (!updated) {
            throw NotFoundException(Errors.ERROR_INTEGRATION_NOT_FOUND)
        }

        val updatedIntegration =
            repository.getNukiIntegrationById(user.accountId, id)
                ?: throw NotFoundException(Errors.ERROR_INTEGRATION_NOT_FOUND)

        call.respond(NukiIntegration.from(updatedIntegration))
    }

    override suspend fun delete(
        call: ApplicationCall,
        user: KasifyUserPrincipal,
        id: Uuid,
    ) {
        val deleted = repository.deleteNukiIntegration(user.accountId, id)
        if (!deleted) {
            throw NotFoundException(Errors.ERROR_INTEGRATION_NOT_FOUND)
        }
        call.respond(HttpStatusCode.NoContent)
    }
}
