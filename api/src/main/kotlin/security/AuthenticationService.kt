package eu.kasify.security

import eu.kasify.db.UsersRepository
import eu.kasify.model.JWTVUserInfo
import eu.kasify.routes.exceptions.NotAuthorizedException
import eu.kasify.services.ApiTokenService
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlin.uuid.toKotlinUuid

class AuthenticationService(
    private val jwtSecurity: JWTSecurity,
    private val usersRepository: UsersRepository,
    private val apiTokenService: ApiTokenService,
) {
    private val logger = KotlinLogging.logger {}

    suspend fun authenticateWithToken(token: String): KasifyUserPrincipal? {
        if (token.startsWith(ApiTokenService.TOKEN_PREFIX)) {
            return try {
                apiTokenService.validate(token)
            } catch (e: NotAuthorizedException) {
                logger.debug { "PAT authentication failed: ${e.message} [code=${e.code}]" }
                null
            }
        }
        val userInfo = getUserInfoFromToken(token) ?: return null
        return createPrincipalFromValidation(userInfo)
    }

    private fun getUserInfoFromToken(token: String): JWTVUserInfo? =
        try {
            // Step 1: Validate the token signature and structure
            val decodedJWT = jwtSecurity.validateToken(token)

            // Step 2: Extract user information from the validated token
            val result = jwtSecurity.getUserInfo(decodedJWT)
            if (result == null) {
                logger.debug { "Failed to extract user info from JWT" }
            }
            result
        } catch (e: JWTValidationException) {
            logger.debug { "JWT validation failed: ${e.message}" }
            null
        }

    private suspend fun createPrincipalFromValidation(jwtUserInfo: JWTVUserInfo): KasifyUserPrincipal? {
        val userInfo = usersRepository.getUserById(jwtUserInfo.userId.toKotlinUuid())

        if (userInfo == null) {
            logger.warn {
                "Authentication failed: User not found [userId=${jwtUserInfo.userId}]"
            }
            return null
        }

        logger.debug {
            "Authentication successful [userId=${userInfo.id}, accountId=${userInfo.accountId}]"
        }

        return KasifyUserPrincipal(
            userId = userInfo.id,
            accountId = userInfo.accountId,
            roles = jwtUserInfo.roles,
            authMethod = AuthMethod.Jwt,
        )
    }
}
