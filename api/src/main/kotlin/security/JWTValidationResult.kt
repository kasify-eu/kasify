package eu.kasify.security

import java.util.UUID

data class JWTValidationResult(
    val userId: UUID,
    val accountId: UUID,
    val roles: List<UserRole>,
)

data class UserRole(
    val id: String,
    val key: String,
    val name: String,
)
