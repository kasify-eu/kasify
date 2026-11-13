package eu.kasify.security

import eu.kasify.db.ApiTokensRepository
import eu.kasify.db.UsersRepository
import eu.kasify.model.ApiToken
import eu.kasify.model.User
import eu.kasify.services.ApiTokenService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.time.OffsetDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.uuid.Uuid

class AuthenticationServiceTest {
    private val userId = Uuid.parse("10000000-0000-0000-0000-000000000001")
    private val accountId = Uuid.parse("00000000-0000-0000-0000-000000000001")

    private fun usersRepository(): UsersRepository =
        object : UsersRepository() {
            override suspend fun getUserById(id: Uuid): User? =
                if (id == userId) {
                    User(
                        id = userId,
                        accountId = accountId,
                        name = "Test",
                        lastName = "User",
                        email = "test@example.com",
                    )
                } else {
                    null
                }
        }

    private fun apiTokenService(repo: ApiTokensRepository): ApiTokenService =
        ApiTokenService(
            apiTokensRepository = repo,
            usersRepository = usersRepository(),
            backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
        )

    private fun authService(apiTokenService: ApiTokenService): AuthenticationService =
        AuthenticationService(
            jwtSecurity = StubJWTSecurity(),
            usersRepository = usersRepository(),
            apiTokenService = apiTokenService,
        )

    @Test
    fun `kas_pat prefix with unknown hash returns null`() =
        runBlocking {
            val repo = StubRepo()
            val auth = authService(apiTokenService(repo))

            val principal = auth.authenticateWithToken("kas_pat_doesnotexist")

            assertNull(principal)
        }

    @Test
    fun `revoked PAT returns null`() =
        runBlocking {
            val repo = StubRepo()
            val service = apiTokenService(repo)
            val generated = service.generate(userId, accountId, "test", expiresAt = null)
            repo.revokeStored()

            val auth = authService(service)
            val principal = auth.authenticateWithToken(generated.rawToken)

            assertNull(principal)
        }

    @Test
    fun `valid PAT returns principal with ApiToken auth method`() =
        runBlocking {
            val repo = StubRepo()
            val service = apiTokenService(repo)
            val generated = service.generate(userId, accountId, "test", expiresAt = null)
            val auth = authService(service)

            val principal = auth.authenticateWithToken(generated.rawToken)

            assertNotNull(principal)
            assertEquals(userId, principal.userId)
            assertEquals(accountId, principal.accountId)
            assertEquals(AuthMethod.ApiToken, principal.authMethod)
        }

    @Test
    fun `non-PAT token does not call ApiTokenService and returns null when JWT invalid`() =
        runBlocking {
            val repo = StubRepo()
            val auth = authService(apiTokenService(repo))

            val principal = auth.authenticateWithToken("eyJ.invalid.jwt.token")

            assertNull(principal)
        }
}

private class StubJWTSecurity : JWTSecurity(skipInit = true) {
    override fun validateToken(token: String) = throw JWTValidationException("stub: not valid")
}

private class StubRepo : ApiTokensRepository() {
    private var stored: ApiToken? = null

    override suspend fun create(
        userId: Uuid,
        accountId: Uuid,
        name: String,
        tokenHash: String,
        tokenPrefix: String,
        expiresAt: OffsetDateTime?,
    ): ApiToken {
        val token =
            ApiToken(
                id = Uuid.random(),
                userId = userId,
                accountId = accountId,
                name = name,
                tokenHash = tokenHash,
                tokenPrefix = tokenPrefix,
                expiresAt = expiresAt,
                lastUsedAt = null,
                revokedAt = null,
                createdAt = OffsetDateTime.parse("2026-04-27T12:00:00Z"),
            )
        stored = token
        return token
    }

    override suspend fun findByHash(tokenHash: String): ApiToken? {
        val s = stored ?: return null
        return if (s.tokenHash == tokenHash) s else null
    }

    override suspend fun updateLastUsedAt(
        id: Uuid,
        at: OffsetDateTime,
    ) {}

    fun revokeStored() {
        stored = stored?.copy(revokedAt = OffsetDateTime.parse("2026-04-27T12:30:00Z"))
    }
}
