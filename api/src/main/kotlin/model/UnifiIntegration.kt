package eu.kasify.model
import eu.kasify.db.model.AccountIntegrationsTable
import eu.kasify.db.model.AccountUnifiIntegrationsTable
import eu.kasify.security.EncryptionService
import org.jetbrains.exposed.v1.core.ResultRow
import java.time.LocalDateTime
import java.time.OffsetDateTime
import kotlin.uuid.Uuid

data class UnifiIntegration(
    var id: Uuid? = null,
    val accountIntegrationId: Uuid,
    val accountId: Uuid,
    val unifiUsername: String,
    val unifiPassword: String,
    val unifiConsoleUrl: String,
    // Session caching fields (encrypted in DB)
    val sessionCsrfToken: String? = null,
    val sessionCookies: String? = null,
    val sessionExpiresAt: OffsetDateTime? = null,
    val createdAt: LocalDateTime? = null,
    val updatedAt: LocalDateTime? = null,
) {
    companion object {
        fun from(
            row: ResultRow,
            encryptionService: EncryptionService,
        ): UnifiIntegration =
            UnifiIntegration(
                id = row[AccountUnifiIntegrationsTable.id],
                accountIntegrationId = row[AccountUnifiIntegrationsTable.accountIntegrationId],
                accountId = row[AccountIntegrationsTable.accountId],
                unifiUsername = row[AccountUnifiIntegrationsTable.unifiUsername],
                unifiPassword = encryptionService.decrypt(row[AccountUnifiIntegrationsTable.unifiPassword]),
                unifiConsoleUrl = row[AccountUnifiIntegrationsTable.unifiConsoleUrl],
                sessionCsrfToken = row[AccountUnifiIntegrationsTable.sessionCsrfToken],
                sessionCookies = row[AccountUnifiIntegrationsTable.sessionCookies],
                sessionExpiresAt = row[AccountUnifiIntegrationsTable.sessionExpiresAt],
                createdAt = row[AccountUnifiIntegrationsTable.createdAt].toLocalDateTime(),
                updatedAt = row[AccountUnifiIntegrationsTable.updatedAt].toLocalDateTime(),
            )
    }
}
