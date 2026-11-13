package eu.kasify.services

import eu.kasify.db.ApiTokensRepository
import eu.kasify.db.UsersRepository
import eu.kasify.model.ApiToken
import eu.kasify.routes.exceptions.NotAuthorizedException
import eu.kasify.security.AuthMethod
import eu.kasify.security.KasifyUserPrincipal
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import kotlin.uuid.Uuid

class GeneratedToken(
    val token: ApiToken,
    val rawToken: String,
)

open class ApiTokenService(
    private val apiTokensRepository: ApiTokensRepository,
    private val usersRepository: UsersRepository,
    private val backgroundScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val clock: () -> OffsetDateTime = { OffsetDateTime.now(ZoneOffset.UTC) },
) {
    private val logger = KotlinLogging.logger {}

    private val secureRandom = SecureRandom()
    private val lastUsedDebounce = ConcurrentHashMap<Uuid, OffsetDateTime>()

    open suspend fun generate(
        userId: Uuid,
        accountId: Uuid,
        name: String,
        expiresAt: OffsetDateTime?,
    ): GeneratedToken {
        val randomBytes = ByteArray(RANDOM_BYTES)
        secureRandom.nextBytes(randomBytes)
        val raw = TOKEN_PREFIX + base64Url.encodeToString(randomBytes)
        val hash = sha256Hex(raw)
        val prefix = raw.substring(0, PREFIX_LENGTH)

        val token =
            apiTokensRepository.create(
                userId = userId,
                accountId = accountId,
                name = name,
                tokenHash = hash,
                tokenPrefix = prefix,
                expiresAt = expiresAt,
            )
        return GeneratedToken(token = token, rawToken = raw)
    }

    open suspend fun validate(rawToken: String): KasifyUserPrincipal {
        val hash = sha256Hex(rawToken)
        val token =
            apiTokensRepository.findByHash(hash)
                ?: throw NotAuthorizedException(
                    "API token is invalid",
                    code = "auth.token_invalid",
                )

        if (token.isRevoked) {
            throw NotAuthorizedException(
                "API token has been revoked",
                code = "auth.token_revoked",
            )
        }
        if (token.isExpired) {
            throw NotAuthorizedException(
                "API token has expired",
                code = "auth.token_expired",
            )
        }

        val user =
            usersRepository.getUserById(token.userId)
                ?: throw NotAuthorizedException(
                    "API token user not found",
                    code = "auth.token_invalid",
                )

        scheduleLastUsedUpdate(token.id)

        logger.debug {
            "PAT authentication successful [tokenId=${token.id}, userId=${user.id}]"
        }

        return KasifyUserPrincipal(
            userId = user.id,
            accountId = user.accountId,
            roles = emptyList(),
            authMethod = AuthMethod.ApiToken,
            tokenId = token.id,
        )
    }

    private fun scheduleLastUsedUpdate(tokenId: Uuid) {
        val now = clock()
        val previous = lastUsedDebounce[tokenId]
        if (previous != null && previous.plusSeconds(DEBOUNCE_SECONDS).isAfter(now)) {
            return
        }
        lastUsedDebounce[tokenId] = now
        backgroundScope.launch {
            try {
                apiTokensRepository.updateLastUsedAt(tokenId, now)
            } catch (e: Exception) {
                logger.warn(e) { "Failed to update last_used_at for PAT [tokenId=$tokenId]" }
            }
        }
    }

    companion object {
        const val TOKEN_PREFIX: String = "kas_pat_"
        const val PREFIX_LENGTH: Int = 12
        private const val RANDOM_BYTES: Int = 32
        private const val DEBOUNCE_SECONDS: Long = 60

        private val base64Url: Base64.Encoder = Base64.getUrlEncoder().withoutPadding()

        fun sha256Hex(value: String): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
            return digest.joinToString("") { "%02x".format(it) }
        }
    }
}
