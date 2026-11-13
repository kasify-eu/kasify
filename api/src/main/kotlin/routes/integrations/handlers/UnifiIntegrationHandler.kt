package eu.kasify.routes.integrations.handlers
import eu.kasify.db.IntegrationsRepository
import eu.kasify.model.api.UnifiIntegration
import eu.kasify.model.api.requests.UnifiIntegrationRequest
import eu.kasify.plugins.unifi.UnifiAccessPluginV2
import eu.kasify.routes.Errors
import eu.kasify.routes.exceptions.ForbiddenException
import eu.kasify.routes.exceptions.NotFoundException
import eu.kasify.routes.integrations.IntegrationHandler
import eu.kasify.security.KasifyUserPrincipal
import eu.kasify.services.UnifiSessionManager
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import kotlin.uuid.Uuid

private val logger = KotlinLogging.logger {}

class UnifiIntegrationHandler(
    private val repository: IntegrationsRepository,
    private val unifiSessionManager: UnifiSessionManager,
) : IntegrationHandler {
    override val pathSegment: String = "unifi"

    override suspend fun list(
        call: ApplicationCall,
        user: KasifyUserPrincipal,
    ) {
        val integrations = repository.getUnifiIntegrationsByAccountId(user.accountId)
        call.respond(integrations.map { UnifiIntegration.from(it) })
    }

    override suspend fun create(
        call: ApplicationCall,
        user: KasifyUserPrincipal,
    ) {
        val request = call.receive<UnifiIntegrationRequest>()

        val plugin = UnifiAccessPluginV2(request.unifiConsoleUrl)
        try {
            plugin.login(request.unifiUsername, request.unifiPassword)
        } catch (e: Exception) {
            plugin.close()
            logger.warn(e) {
                "Unifi login failed for account=${user.accountId}, console=${request.unifiConsoleUrl}"
            }
            throw ForbiddenException("Unifi credentials are not valid.", code = "integration.invalid_credentials")
        } finally {
            plugin.close()
        }

        val newIntegration =
            repository.createUnifiIntegration(
                accountId = user.accountId,
                unifiConsoleUrl = request.unifiConsoleUrl,
                unifiUsername = request.unifiUsername,
                unifiPassword = request.unifiPassword,
            )
        call.respond(HttpStatusCode.Created, UnifiIntegration.from(newIntegration))
    }

    override suspend fun update(
        call: ApplicationCall,
        user: KasifyUserPrincipal,
        id: Uuid,
    ) {
        val request = call.receive<UnifiIntegrationRequest>()

        repository.getUnifiIntegrationById(user.accountId, id)
            ?: throw NotFoundException(Errors.ERROR_UNIFI_NOT_CONFIGURED)

        val plugin = UnifiAccessPluginV2(request.unifiConsoleUrl)
        try {
            plugin.login(request.unifiUsername, request.unifiPassword)
        } catch (e: Exception) {
            plugin.close()
            logger.warn(e) {
                "Unifi login failed during update for account=${user.accountId}, console=${request.unifiConsoleUrl}"
            }
            throw ForbiddenException("Unifi credentials are not valid.", code = "integration.invalid_credentials")
        } finally {
            plugin.close()
        }

        unifiSessionManager.clearSession(user.accountId)

        val updated =
            repository.updateUnifiIntegration(
                accountId = user.accountId,
                integrationId = id,
                unifiConsoleUrl = request.unifiConsoleUrl,
                unifiUsername = request.unifiUsername,
                unifiPassword = request.unifiPassword,
            )

        if (!updated) {
            throw NotFoundException(Errors.ERROR_INTEGRATION_NOT_FOUND)
        }

        val updatedIntegration =
            repository.getUnifiIntegrationById(user.accountId, id)
                ?: throw NotFoundException(Errors.ERROR_INTEGRATION_NOT_FOUND)

        call.respond(UnifiIntegration.from(updatedIntegration))
    }

    override suspend fun delete(
        call: ApplicationCall,
        user: KasifyUserPrincipal,
        id: Uuid,
    ) {
        val deleted = repository.deleteUnifiIntegration(user.accountId, id)
        if (!deleted) {
            throw NotFoundException(Errors.ERROR_INTEGRATION_NOT_FOUND)
        }
        call.respond(HttpStatusCode.NoContent)
    }
}
