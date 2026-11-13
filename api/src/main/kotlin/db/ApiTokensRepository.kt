package eu.kasify.db

import eu.kasify.db.model.ApiTokensTable
import eu.kasify.model.ApiToken
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.jdbc.insertReturning
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.uuid.Uuid

open class ApiTokensRepository {
    open suspend fun create(
        userId: Uuid,
        accountId: Uuid,
        name: String,
        tokenHash: String,
        tokenPrefix: String,
        expiresAt: OffsetDateTime?,
    ): ApiToken =
        dbQuery {
            ApiTokensTable
                .insertReturning {
                    it[ApiTokensTable.userId] = userId
                    it[ApiTokensTable.accountId] = accountId
                    it[ApiTokensTable.name] = name
                    it[ApiTokensTable.tokenHash] = tokenHash
                    it[ApiTokensTable.tokenPrefix] = tokenPrefix
                    it[ApiTokensTable.expiresAt] = expiresAt
                    it[ApiTokensTable.createdAt] = OffsetDateTime.now(ZoneOffset.UTC)
                }.first()
                .let { ApiToken.from(it) }
        }

    open suspend fun findByHash(tokenHash: String): ApiToken? =
        dbQuery {
            ApiTokensTable
                .selectAll()
                .where { ApiTokensTable.tokenHash eq tokenHash }
                .singleOrNull()
                ?.let { ApiToken.from(it) }
        }

    open suspend fun findActiveById(
        id: Uuid,
        userId: Uuid,
    ): ApiToken? =
        dbQuery {
            ApiTokensTable
                .selectAll()
                .where {
                    (ApiTokensTable.id eq id) and
                        (ApiTokensTable.userId eq userId) and
                        ApiTokensTable.revokedAt.isNull()
                }.singleOrNull()
                ?.let { ApiToken.from(it) }
        }

    open suspend fun listActiveByUser(userId: Uuid): List<ApiToken> =
        dbQuery {
            ApiTokensTable
                .selectAll()
                .where {
                    (ApiTokensTable.userId eq userId) and
                        ApiTokensTable.revokedAt.isNull()
                }.orderBy(ApiTokensTable.createdAt to SortOrder.DESC)
                .map { ApiToken.from(it) }
        }

    open suspend fun countActiveByUser(userId: Uuid): Long =
        dbQuery {
            ApiTokensTable
                .selectAll()
                .where {
                    (ApiTokensTable.userId eq userId) and
                        ApiTokensTable.revokedAt.isNull()
                }.count()
        }

    open suspend fun activeNameExists(
        userId: Uuid,
        name: String,
    ): Boolean =
        dbQuery {
            ApiTokensTable
                .selectAll()
                .where {
                    (ApiTokensTable.userId eq userId) and
                        (ApiTokensTable.name eq name) and
                        ApiTokensTable.revokedAt.isNull()
                }.count() > 0
        }

    open suspend fun revoke(
        id: Uuid,
        userId: Uuid,
    ): Boolean =
        dbQuery {
            ApiTokensTable.update({
                (ApiTokensTable.id eq id) and
                    (ApiTokensTable.userId eq userId) and
                    ApiTokensTable.revokedAt.isNull()
            }) {
                it[revokedAt] = OffsetDateTime.now(ZoneOffset.UTC)
            } > 0
        }

    open suspend fun updateLastUsedAt(
        id: Uuid,
        at: OffsetDateTime,
    ): Unit =
        dbQuery {
            ApiTokensTable.update({ ApiTokensTable.id eq id }) {
                it[lastUsedAt] = at
            }
        }
}
