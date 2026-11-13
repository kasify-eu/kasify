package eu.kasify.routes.integrations.handlers
import eu.kasify.db.IntegrationsRepository
import eu.kasify.model.api.SesHospedajesIntegration
import eu.kasify.model.api.requests.SesHospedajesIntegrationRequest
import eu.kasify.model.api.requests.SesHospedajesSettingsRequest
import eu.kasify.plugins.ses.SesHospedajesException
import eu.kasify.plugins.ses.SesHospedajesPlugin
import eu.kasify.plugins.ses.SesHospedajesUnauthorizedException
import eu.kasify.routes.Errors
import eu.kasify.routes.exceptions.BadParamRequestException
import eu.kasify.routes.exceptions.NotFoundException
import eu.kasify.routes.integrations.IntegrationHandler
import eu.kasify.security.KasifyUserPrincipal
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import kotlin.uuid.Uuid

class SesHospedajesIntegrationHandler(
    private val repository: IntegrationsRepository,
) : IntegrationHandler {
    override val pathSegment: String = "ses-hospedajes"

    override suspend fun list(
        call: ApplicationCall,
        user: KasifyUserPrincipal,
    ) {
        val integrations = repository.getSesHospedajesIntegrationsByAccountId(user.accountId)
        call.respond(integrations.map { SesHospedajesIntegration.from(it) })
    }

    override suspend fun create(
        call: ApplicationCall,
        user: KasifyUserPrincipal,
    ) {
        val request = call.receive<SesHospedajesIntegrationRequest>()
        verifySesCredentials(request)

        val newIntegration =
            repository.createSesHospedajesIntegration(
                accountId = user.accountId,
                arrendadorCode = request.arrendadorCode,
                sesUsername = request.sesUsername,
                sesPassword = request.sesPassword,
                rhDirectBookingsEnabled = request.rhDirectBookingsEnabled,
            )
        call.respond(HttpStatusCode.Created, SesHospedajesIntegration.from(newIntegration))
    }

    override suspend fun update(
        call: ApplicationCall,
        user: KasifyUserPrincipal,
        id: Uuid,
    ) {
        val request = call.receive<SesHospedajesIntegrationRequest>()
        verifySesCredentials(request)

        val updated =
            repository.updateSesHospedajesIntegration(
                accountId = user.accountId,
                integrationId = id,
                arrendadorCode = request.arrendadorCode,
                sesUsername = request.sesUsername,
                sesPassword = request.sesPassword,
                rhDirectBookingsEnabled = request.rhDirectBookingsEnabled,
            )

        if (!updated) {
            throw NotFoundException(Errors.ERROR_INTEGRATION_NOT_FOUND)
        }

        val updatedIntegration =
            repository.getSesHospedajesIntegrationById(user.accountId, id)
                ?: throw NotFoundException(Errors.ERROR_INTEGRATION_NOT_FOUND)

        call.respond(SesHospedajesIntegration.from(updatedIntegration))
    }

    override suspend fun updateSettings(
        call: ApplicationCall,
        user: KasifyUserPrincipal,
        id: Uuid,
    ) {
        val request = call.receive<SesHospedajesSettingsRequest>()

        val updated =
            repository.updateSesHospedajesSettings(
                accountId = user.accountId,
                integrationId = id,
                rhDirectBookingsEnabled = request.rhDirectBookingsEnabled,
            )

        if (!updated) {
            throw NotFoundException(Errors.ERROR_INTEGRATION_NOT_FOUND)
        }

        val updatedIntegration =
            repository.getSesHospedajesIntegrationById(user.accountId, id)
                ?: throw NotFoundException(Errors.ERROR_INTEGRATION_NOT_FOUND)

        call.respond(SesHospedajesIntegration.from(updatedIntegration))
    }

    override suspend fun delete(
        call: ApplicationCall,
        user: KasifyUserPrincipal,
        id: Uuid,
    ) {
        val deleted = repository.deleteSesHospedajesIntegration(user.accountId, id)
        if (!deleted) {
            throw NotFoundException(Errors.ERROR_INTEGRATION_NOT_FOUND)
        }
        call.respond(HttpStatusCode.NoContent)
    }

    /**
     * Pings SES with the supplied credentials before we persist them. A 401 surfaces as a
     * dedicated `ses_hospedajes.invalid_credentials` error so the dashboard can show a specific
     * message; any other SES failure falls under `ses_hospedajes.verification_failed`.
     */
    private suspend fun verifySesCredentials(request: SesHospedajesIntegrationRequest) {
        val plugin =
            SesHospedajesPlugin(
                sesUsername = request.sesUsername,
                sesPassword = request.sesPassword,
            )
        try {
            plugin.verifyCredentials()
        } catch (e: SesHospedajesUnauthorizedException) {
            throw BadParamRequestException(
                Errors.ERROR_SES_HOSPEDAJES_INVALID_CREDENTIALS,
                code = Errors.CODE_SES_HOSPEDAJES_INVALID_CREDENTIALS,
                cause = e,
            )
        } catch (e: SesHospedajesException) {
            throw BadParamRequestException(
                Errors.ERROR_SES_HOSPEDAJES_VERIFICATION_FAILED,
                code = Errors.CODE_SES_HOSPEDAJES_VERIFICATION_FAILED,
                cause = e,
            )
        } finally {
            plugin.close()
        }
    }
}
