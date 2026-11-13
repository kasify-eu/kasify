package eu.kasify.services
import eu.kasify.db.IntegrationsRepository
import eu.kasify.model.UnifiIntegration
import eu.kasify.plugins.unifi.UnifiAccessPluginV2
import eu.kasify.plugins.unifi.UnifiAuthenticationException
import eu.kasify.security.EncryptionService
import io.github.oshai.kotlinlogging.KotlinLogging
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.uuid.Uuid

private val logger = KotlinLogging.logger {}

/**
 * Manages Unifi session caching and authentication.
 *
 * This service stores Unifi session data (cookies + CSRF token) in the database,
 * encrypted. It reuses stored sessions until they expire, only making new login
 * calls when necessary.
 *
 * Benefits:
 * - Survives server restarts
 * - Works in distributed/clustered environments
 * - Prevents "too many calls" rate-limiting from Unifi console
 */
class UnifiSessionManager(
    private val integrationsRepository: IntegrationsRepository,
    private val encryptionService: EncryptionService,
) {
    companion object {
        // Unifi sessions last ~30 min, we use 25 min as buffer
        private const val SESSION_TTL_MINUTES = 25L
    }

    /**
     * Gets an authenticated UnifiAccessPluginV2, using stored session if valid.
     *
     * @param accountId The account ID to get the plugin for
     * @return An authenticated UnifiAccessPluginV2 ready to make API calls
     * @throws Exception if Unifi integration not found or login fails
     */
    suspend fun getAuthenticatedPlugin(accountId: Uuid): UnifiAccessPluginV2 {
        val integration =
            integrationsRepository.getUnifiIntegrationsByAccountId(accountId).firstOrNull()
                ?: throw Exception("Unifi integration not found for account $accountId")

        // Check if we have a valid stored session
        if (isSessionValid(integration)) {
            logger.info { "Restoring Unifi session from DB for account=$accountId" }
            return restoreSession(integration)
        }

        // Need fresh login
        logger.info { "No valid Unifi session for account=$accountId, logging in" }
        return loginAndStoreSession(integration)
    }

    /**
     * Executes a block with automatic session retry on auth failure.
     *
     * If the first attempt fails with an authentication error, the session
     * is cleared and a fresh login is performed before retrying.
     *
     * @param accountId The account ID
     * @param block The block to execute with the authenticated plugin
     * @return The result of the block execution
     */
    suspend fun <T> executeWithRetry(
        accountId: Uuid,
        block: suspend (UnifiAccessPluginV2) -> T,
    ): T {
        val plugin = getAuthenticatedPlugin(accountId)
        return try {
            block(plugin)
        } catch (e: UnifiAuthenticationException) {
            logger.warn { "Unifi session expired for account=$accountId, retrying with fresh login" }
            // Session expired, clear and retry
            clearSession(accountId)
            val freshPlugin = getAuthenticatedPlugin(accountId)
            block(freshPlugin)
        }
    }

    /**
     * Checks if the stored session is still valid (not expired).
     */
    private fun isSessionValid(integration: UnifiIntegration): Boolean {
        val expiresAt = integration.sessionExpiresAt ?: return false
        val csrfToken = integration.sessionCsrfToken ?: return false
        val cookies = integration.sessionCookies ?: return false

        // Session data must be present
        if (csrfToken.isBlank() || cookies.isBlank()) return false

        // Check expiration
        val now = Instant.now()
        return now.isBefore(expiresAt.toInstant())
    }

    /**
     * Restores a session from the database.
     */
    private fun restoreSession(integration: UnifiIntegration): UnifiAccessPluginV2 {
        val csrfToken = encryptionService.decrypt(integration.sessionCsrfToken!!)
        val cookiesJson = encryptionService.decrypt(integration.sessionCookies!!)

        val plugin = UnifiAccessPluginV2(integration.unifiConsoleUrl)
        plugin.restoreSession(csrfToken, cookiesJson)
        return plugin
    }

    /**
     * Performs a fresh login and stores the session in the database.
     */
    private suspend fun loginAndStoreSession(integration: UnifiIntegration): UnifiAccessPluginV2 {
        val plugin = UnifiAccessPluginV2(integration.unifiConsoleUrl)

        plugin.login(integration.unifiUsername, integration.unifiPassword)

        // Extract and store session
        val sessionData = plugin.extractSessionData()
        val expiresAt = OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(SESSION_TTL_MINUTES)

        integrationsRepository.updateUnifiSession(
            accountId = integration.accountId,
            csrfToken = encryptionService.encrypt(sessionData.csrfToken),
            cookies = encryptionService.encrypt(sessionData.cookiesJson),
            expiresAt = expiresAt,
        )

        logger.info { "Successfully logged in, storing session (expires in $SESSION_TTL_MINUTES min)" }

        return plugin
    }

    /**
     * Clears the stored session for an account.
     * Call this when credentials are updated or session is known to be invalid.
     */
    suspend fun clearSession(accountId: Uuid) {
        logger.info { "Clearing Unifi session for account=$accountId" }
        integrationsRepository.clearUnifiSession(accountId)
    }
}
