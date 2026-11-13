package eu.kasify.model
import eu.kasify.db.model.AccountIntegrationsTable
import eu.kasify.db.model.AccountNukiIntegrationsTable
import eu.kasify.security.EncryptionService
import org.jetbrains.exposed.v1.core.ResultRow
import java.time.LocalDateTime
import kotlin.uuid.Uuid

data class NukiIntegration(
    var id: Uuid? = null,
    val accountIntegrationId: Uuid,
    val accountId: Uuid,
    val nukiToken: String,
    val createdAt: LocalDateTime? = null,
    val updatedAt: LocalDateTime? = null,
) {
    companion object {
        fun from(
            row: ResultRow,
            encryptionService: EncryptionService,
        ): NukiIntegration =
            NukiIntegration(
                id = row[AccountNukiIntegrationsTable.id],
                accountIntegrationId = row[AccountNukiIntegrationsTable.accountIntegrationId],
                accountId = row[AccountIntegrationsTable.accountId],
                nukiToken = encryptionService.decrypt(row[AccountNukiIntegrationsTable.nukiToken]),
                createdAt = row[AccountNukiIntegrationsTable.createdAt].toLocalDateTime(),
                updatedAt = row[AccountNukiIntegrationsTable.updatedAt].toLocalDateTime(),
            )
    }
}
