package eu.kasify.routes.integrations

import eu.kasify.routes.exceptions.NotFoundException
import eu.kasify.security.KasifyUserPrincipal
import io.ktor.server.application.ApplicationCall
import kotlin.uuid.Uuid

interface IntegrationHandler {
    val pathSegment: String

    suspend fun list(
        call: ApplicationCall,
        user: KasifyUserPrincipal,
    )

    suspend fun create(
        call: ApplicationCall,
        user: KasifyUserPrincipal,
    )

    suspend fun update(
        call: ApplicationCall,
        user: KasifyUserPrincipal,
        id: Uuid,
    )

    suspend fun delete(
        call: ApplicationCall,
        user: KasifyUserPrincipal,
        id: Uuid,
    )

    /**
     * Partial update of provider-specific settings that don't require re-supplying or
     * re-verifying credentials (e.g. feature toggles). Providers that have no such
     * settings inherit the default, which reports the sub-resource as not found.
     */
    suspend fun updateSettings(
        call: ApplicationCall,
        user: KasifyUserPrincipal,
        id: Uuid,
    ): Unit = throw NotFoundException("This integration does not support settings updates")
}
