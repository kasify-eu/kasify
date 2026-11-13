package eu.kasify.services

import eu.kasify.db.ApiTokensRepository
import eu.kasify.db.UsersRepository
import eu.kasify.model.ApiToken
import eu.kasify.model.User
import eu.kasify.routes.exceptions.NotAuthorizedException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class ApiTokenServiceTest {
    private val userId = Uuid.parse("10000000-0000-0000-0000-000000000001")
    private val accountId = Uuid.parse("00000000-0000-0000-0000-000000000001")
    private val now = OffsetDateTime.parse("2026-04-27T12:00:00Z")

    private fun service(
        repository: ApiTokensRepository,
        users: UsersRepository = stubUsersRepository(),
        clock: () -> OffsetDateTime = { now },
    ): ApiTokenService =
        ApiTokenService(
            apiTokensRepository = repository,
            usersRepository = users,
            backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
            clock = clock,
        )

    private fun stubUsersRepository(user: User = sampleUser()): UsersRepository =
        object : UsersRepository() {
            override suspend fun getUserById(id: Uuid): User? = if (id == user.id) user else null
        }

    private fun sampleUser() =
        User(
            id = userId,
            accountId = accountId,
            name = "Test",
            lastName = "User",
            email = "test@example.com",
        )

    @Test
    fun `generated token uses kas_pat_ prefix and writes prefix to repository`() =
        runBlocking {
            val captured = CapturingApiTokensRepository()
            val s = service(captured)

            val generated = s.generate(userId, accountId, "ci", expiresAt = null)

            assertTrue(generated.rawToken.startsWith("kas_pat_"))
            assertTrue(generated.token.tokenPrefix.startsWith("kas_pat_"))
            assertEquals(12, generated.token.tokenPrefix.length)
            assertEquals(generated.rawToken.substring(0, 12), generated.token.tokenPrefix)
            assertEquals(captured.created!!.tokenHash, ApiTokenService.sha256Hex(generated.rawToken))
        }

    @Test
    fun `validate returns principal for active token`() =
        runBlocking {
            val captured = CapturingApiTokensRepository()
            val s = service(captured)
            val generated = s.generate(userId, accountId, "ci", expiresAt = null)

            val principal = s.validate(generated.rawToken)

            assertEquals(userId, principal.userId)
            assertEquals(accountId, principal.accountId)
        }

    @Test
    fun `validate throws auth_token_invalid when hash unknown`() =
        runBlocking {
            val s = service(CapturingApiTokensRepository())
            val ex =
                assertFails {
                    s.validate("kas_pat_unknownvalue")
                }
            assertTrue(ex is NotAuthorizedException)
            assertEquals("auth.token_invalid", ex.code)
        }

    @Test
    fun `validate throws auth_token_revoked when token revoked`() =
        runBlocking {
            val captured = CapturingApiTokensRepository()
            val s = service(captured)
            val generated = s.generate(userId, accountId, "ci", expiresAt = null)
            captured.revokeStored()

            val ex = assertFails { s.validate(generated.rawToken) }
            assertTrue(ex is NotAuthorizedException)
            assertEquals("auth.token_revoked", ex.code)
        }

    @Test
    fun `validate throws auth_token_expired when token past expiry`() =
        runBlocking {
            val captured = CapturingApiTokensRepository()
            val s = service(captured)
            val pastExpiry = OffsetDateTime.now(ZoneOffset.UTC).minusDays(1)
            val generated = s.generate(userId, accountId, "ci", expiresAt = pastExpiry)

            val ex = assertFails { s.validate(generated.rawToken) }
            assertTrue(ex is NotAuthorizedException)
            assertEquals("auth.token_expired", ex.code)
        }

    @Test
    fun `validate debounces last_used updates within 60s`() =
        runBlocking {
            val captured = CapturingApiTokensRepository()
            var clockNow = now
            val s = service(captured, clock = { clockNow })
            val generated = s.generate(userId, accountId, "ci", expiresAt = null)

            s.validate(generated.rawToken)
            // 30 seconds later — should not trigger another update
            clockNow = clockNow.plusSeconds(30)
            s.validate(generated.rawToken)
            // 70 seconds total later — should trigger another update
            clockNow = now.plusSeconds(70)
            s.validate(generated.rawToken)

            assertEquals(2, captured.lastUsedAtCalls.get())
        }
}

private class CapturingApiTokensRepository : ApiTokensRepository() {
    var created: ApiToken? = null
    val lastUsedAtCalls: AtomicInteger = AtomicInteger(0)

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
        created = token
        return token
    }

    override suspend fun findByHash(tokenHash: String): ApiToken? {
        val current = created ?: return null
        if (current.tokenHash != tokenHash) return null
        return current
    }

    override suspend fun updateLastUsedAt(
        id: Uuid,
        at: OffsetDateTime,
    ) {
        lastUsedAtCalls.incrementAndGet()
    }

    fun revokeStored() {
        created = created?.copy(revokedAt = OffsetDateTime.parse("2026-04-27T12:30:00Z"))
    }
}
