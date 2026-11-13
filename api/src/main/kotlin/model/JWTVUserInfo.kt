package eu.kasify.model

import java.util.UUID

data class JWTVUserInfo(
    val userId: UUID,
    val accountId: UUID,
    val roles: List<UserRole>,
)
