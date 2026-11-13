package eu.kasify.security

import eu.kasify.model.UserRole
import kotlin.uuid.Uuid

enum class AuthMethod {
    Jwt,
    ApiToken,
}

data class KasifyUserPrincipal(
    val userId: Uuid,
    val accountId: Uuid,
    val roles: List<UserRole> = emptyList(),
    val authMethod: AuthMethod = AuthMethod.Jwt,
    // Populated only when authMethod == ApiToken; null for JWT. Used as the
    // rate-limit bucket key for PAT-authenticated requests so each token has
    // its own quota independent of the user's other tokens.
    val tokenId: Uuid? = null,
)
