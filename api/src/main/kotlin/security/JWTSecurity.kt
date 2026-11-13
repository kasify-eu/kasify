package eu.kasify.security
import com.auth0.jwk.JwkProvider
import com.auth0.jwk.JwkProviderBuilder
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.JWTVerificationException
import com.auth0.jwt.interfaces.DecodedJWT
import eu.kasify.model.JWTVUserInfo
import eu.kasify.model.UserRole
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.security.interfaces.RSAPublicKey
import java.util.UUID
import java.util.concurrent.TimeUnit

open class JWTSecurity(
    skipInit: Boolean = false,
) {
    private val logger = KotlinLogging.logger {}
    private var provider: JwkProvider? = null

    init {
        if (!skipInit) {
            val kindeDomain = System.getenv("KINDE_DOMAIN")
            logger.info { "Initializing JWT provider with Kinde domain: $kindeDomain" }

            provider =
                JwkProviderBuilder(kindeDomain)
                    .cached(100, 24, TimeUnit.HOURS)
                    .rateLimited(20, 1, TimeUnit.MINUTES)
                    .build()
        }
    }

    /**
     * Validates a JWT token by verifying its signature and structure.
     *
     * @param token The JWT token string to validate
     * @return DecodedJWT if validation succeeds
     * @throws JWTValidationException if validation fails for any reason
     */
    open fun validateToken(token: String): DecodedJWT {
        // Get token prefix for logging (first 10 chars, safe to log)
        val tokenPrefix = token.take(10)

        try {
            // Decode JWT header to get keyId
            val decodedHeader = JWT.decode(token)
            val keyId = decodedHeader.keyId

            if (keyId == null) {
                logger.warn { "JWT validation failed: Missing keyId in token header [token_prefix=$tokenPrefix]" }
                throw JWTValidationException("Missing keyId in token header")
            }

            val jwk = provider?.get(keyId)
            val publicKey = jwk?.publicKey as? RSAPublicKey

            if (publicKey == null) {
                logger.warn { "JWT validation failed: Unable to retrieve or cast public key [keyId=$keyId, token_prefix=$tokenPrefix]" }
                throw JWTValidationException("Unable to retrieve or cast public key for keyId: $keyId")
            }

            val algorithm = Algorithm.RSA256(publicKey, null)
            val verifier = JWT.require(algorithm).build()
            val decodedJWT = verifier.verify(token)

            logger.debug { "JWT validation successful [keyId=$keyId, token_prefix=$tokenPrefix]" }

            return decodedJWT
        } catch (e: JWTValidationException) {
            // Re-throw our custom exception
            throw e
        } catch (e: JWTVerificationException) {
            logger.warn(e) { "JWT verification failed: ${e.message} [token_prefix=$tokenPrefix]" }
            throw JWTValidationException("JWT verification failed: ${e.message}", e)
        } catch (e: Exception) {
            logger.error(e) { "Unexpected error during JWT validation: ${e.message} [token_prefix=$tokenPrefix]" }
            throw JWTValidationException("Unexpected error during JWT validation: ${e.message}", e)
        }
    }

    /**
     * Extracts user information from a validated JWT token.
     *
     * @param decodedJWT The decoded and validated JWT
     * @return JWTValidationResult containing user info, or null if extraction fails
     */
    fun getUserInfo(decodedJWT: DecodedJWT): JWTVUserInfo? {
        try {
            // Extract user properties (k_aid and k_uid)
            val userProperties = extractUserProperties(decodedJWT)
            if (userProperties == null) {
                logger.warn { "Failed to extract user properties from JWT" }
                return null
            }

            val roles = extractRoles(decodedJWT)

            logger.debug { "User info extracted successfully [userId=${userProperties.userId}, accountId=${userProperties.accountId}, roles=${roles.size}]" }

            return JWTVUserInfo(
                userId = userProperties.userId,
                accountId = userProperties.accountId,
                roles = roles,
            )
        } catch (e: Exception) {
            logger.error(e) { "Unexpected error extracting user info from JWT: ${e.message}" }
            return null
        }
    }

    private fun extractRoles(decodedJWT: DecodedJWT): List<UserRole> {
        return try {
            val rolesClaim = decodedJWT.getClaim("roles")

            // Try to get as array first (direct array in JWT)
            val rolesArray = rolesClaim?.asList(Map::class.java)

            if (rolesArray != null) {
                rolesArray.mapNotNull { roleMap ->
                    @Suppress("UNCHECKED_CAST")
                    val map = roleMap as? Map<*, *>
                    map?.let {
                        UserRole(
                            id = it["id"]?.toString() ?: "",
                            key = it["key"]?.toString() ?: "",
                            name = it["name"]?.toString() ?: "",
                        )
                    }
                }
            } else {
                // Fallback: try to parse as JSON string
                val rolesString = rolesClaim?.asString() ?: return emptyList()
                val json = Json.parseToJsonElement(rolesString)
                val jsonArray = json.jsonArray

                jsonArray.map { roleElement ->
                    val roleObject = roleElement.jsonObject
                    UserRole(
                        id = roleObject["id"]?.jsonPrimitive?.content ?: "",
                        key = roleObject["key"]?.jsonPrimitive?.content ?: "",
                        name = roleObject["name"]?.jsonPrimitive?.content ?: "",
                    )
                }
            }
        } catch (e: Exception) {
            logger.warn(e) { "Failed to extract roles from JWT: ${e.message}" }
            emptyList()
        }
    }

    private data class UserPropertiesResult(
        val userId: UUID,
        val accountId: UUID,
    )

    private fun extractUserProperties(decodedJWT: DecodedJWT): UserPropertiesResult? {
        return try {
            val userPropertiesClaim = decodedJWT.getClaim("user_properties")
            if (userPropertiesClaim == null || userPropertiesClaim.isNull) {
                logger.warn { "user_properties claim is missing or null" }
                return null
            }

            // Access the claim as a Map (it's a JSON object)
            // Structure: {"k_aid":{"v":"uuid"},"k_uid":{"v":"uuid"}}
            @Suppress("UNCHECKED_CAST")
            val userPropertiesMap = userPropertiesClaim.asMap() as? Map<String, Map<String, String>>

            if (userPropertiesMap == null) {
                logger.warn { "user_properties claim could not be parsed as Map" }
                return null
            }

            // Extract k_aid (accountId) and k_uid (userId)
            val accountIdStr = userPropertiesMap["k_aid"]?.get("v")
            val userIdStr = userPropertiesMap["k_uid"]?.get("v")

            // TODO: Token validation should not check for the user properties
            // this makes the signup to fail
            if (accountIdStr == null || userIdStr == null) {
                logger.warn { "Missing k_aid or k_uid in user_properties" }
                return null
            }

            UserPropertiesResult(
                userId = UUID.fromString(userIdStr),
                accountId = UUID.fromString(accountIdStr),
            )
        } catch (e: Exception) {
            logger.warn(e) { "Failed to extract user properties from JWT: ${e.message}" }
            null
        }
    }
}
