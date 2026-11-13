package eu.kasify.services

import eu.kasify.cli.TokenResponse
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.forms.submitForm
import io.ktor.client.statement.bodyAsText
import io.ktor.http.parameters
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import java.time.Instant

/**
 * Service that manages Kinde M2M access tokens with automatic caching and refresh.
 * Tokens are cached and only refreshed when they expire or are about to expire.
 */
open class KindeM2MTokenService(
    private val kindeDomain: String,
    private val clientId: String,
    private val clientSecret: String,
) {
    private val logger = KotlinLogging.logger {}
    private val mutex = Mutex()

    private var cachedToken: String? = null
    private var tokenExpiresAt: Instant? = null

    private val httpClient =
        HttpClient(CIO) {
            install(ContentNegotiation) {
                json(
                    Json {
                        ignoreUnknownKeys = true
                        prettyPrint = true
                    },
                )
            }
            install(HttpTimeout) {
                requestTimeoutMillis = 15_000
                connectTimeoutMillis = 10_000
                socketTimeoutMillis = 15_000
            }
        }

    /**
     * Gets a valid M2M token, fetching a new one only if the cached token is expired or about to expire.
     * Thread-safe for concurrent requests.
     *
     * @param refreshBufferSeconds Number of seconds before expiration to proactively refresh (default: 300 = 5 minutes)
     * @return Valid M2M access token
     */
    open suspend fun getToken(refreshBufferSeconds: Long = 300): String {
        mutex.withLock {
            val now = Instant.now()

            // Check if we have a valid cached token
            if (cachedToken != null && tokenExpiresAt != null) {
                val expiresWithBuffer = tokenExpiresAt!!.minusSeconds(refreshBufferSeconds)
                if (now.isBefore(expiresWithBuffer)) {
                    logger.debug { "Using cached M2M token [expires_at=$tokenExpiresAt]" }
                    return cachedToken!!
                } else {
                    logger.info { "Cached M2M token expired or about to expire [expires_at=$tokenExpiresAt], refreshing..." }
                }
            }

            // Fetch new token
            return fetchNewToken()
        }
    }

    /**
     * Forces a refresh of the M2M token, even if the cached token is still valid.
     * Useful for error recovery scenarios.
     */
    suspend fun forceRefresh(): String {
        mutex.withLock {
            logger.info { "Force refreshing M2M token" }
            return fetchNewToken()
        }
    }

    private suspend fun fetchNewToken(): String {
        try {
            logger.debug { "Fetching new M2M token from Kinde [domain=$kindeDomain]" }

            val tokenResponse =
                httpClient.submitForm(
                    url = "$kindeDomain/oauth2/token",
                    formParameters =
                        parameters {
                            append("grant_type", "client_credentials")
                            append("client_id", clientId)
                            append("client_secret", clientSecret)
                            append("audience", "$kindeDomain/api")
                        },
                )

            val tokenBody = tokenResponse.bodyAsText()
            val json = Json { ignoreUnknownKeys = true }
            val token = json.decodeFromString<TokenResponse>(tokenBody)

            // Cache the token and calculate expiration
            cachedToken = token.accessToken
            tokenExpiresAt = Instant.now().plusSeconds(token.expiresIn!!.toLong())

            logger.info { "Successfully fetched new M2M token [expires_in=${token.expiresIn}s, expires_at=$tokenExpiresAt]" }

            return cachedToken!!
        } catch (e: Exception) {
            logger.error(e) { "Failed to fetch M2M token from Kinde: ${e.message}" }
            throw Exception("Failed to obtain Kinde M2M token", e)
        }
    }

    /**
     * Clears the cached token. Useful for testing or error recovery.
     */
    fun clearCache() {
        cachedToken = null
        tokenExpiresAt = null
        logger.debug { "M2M token cache cleared" }
    }
}
