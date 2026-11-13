package eu.kasify.model

import eu.kasify.db.model.ApiTokensTable
import org.jetbrains.exposed.v1.core.ResultRow
import java.time.OffsetDateTime
import kotlin.uuid.Uuid

data class ApiToken(
    val id: Uuid,
    val userId: Uuid,
    val accountId: Uuid,
    val name: String,
    val tokenHash: String,
    val tokenPrefix: String,
    val expiresAt: OffsetDateTime?,
    val lastUsedAt: OffsetDateTime?,
    val revokedAt: OffsetDateTime?,
    val createdAt: OffsetDateTime,
) {
    val isExpired: Boolean
        get() = expiresAt != null && expiresAt.isBefore(OffsetDateTime.now())

    val isRevoked: Boolean
        get() = revokedAt != null

    companion object {
        fun from(row: ResultRow): ApiToken =
            ApiToken(
                id = row[ApiTokensTable.id],
                userId = row[ApiTokensTable.userId],
                accountId = row[ApiTokensTable.accountId],
                name = row[ApiTokensTable.name],
                tokenHash = row[ApiTokensTable.tokenHash],
                tokenPrefix = row[ApiTokensTable.tokenPrefix],
                expiresAt = row[ApiTokensTable.expiresAt],
                lastUsedAt = row[ApiTokensTable.lastUsedAt],
                revokedAt = row[ApiTokensTable.revokedAt],
                createdAt = row[ApiTokensTable.createdAt],
            )
    }
}
