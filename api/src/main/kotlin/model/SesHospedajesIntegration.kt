package eu.kasify.model
import eu.kasify.db.model.AccountIntegrationsTable
import eu.kasify.db.model.AccountSesHospedajesIntegrationsTable
import eu.kasify.security.EncryptionService
import org.jetbrains.exposed.v1.core.ResultRow
import java.time.LocalDateTime
import kotlin.uuid.Uuid

/**
 * The gate-relevant state of a SES integration, without credentials: whether it is enabled and
 * whether it is flagged with a credential error. Used to derive the API property's `ses_status`
 * for the property's *assigned* integration (batched — see
 * `IntegrationsRepository.getSesIntegrationGateStates`).
 */
data class SesIntegrationGateState(
    val enabled: Boolean,
    val credentialsFlagged: Boolean,
)

data class SesHospedajesIntegration(
    var id: Uuid? = null,
    val accountIntegrationId: Uuid,
    val accountId: Uuid,
    val arrendadorCode: String,
    val sesUsername: String,
    val sesPassword: String,
    val enabled: Boolean,
    val rhDirectBookingsEnabled: Boolean,
    // Set when SES rejected a submission with a credential-family código (10103/10107/10119/10120).
    // While set, the submission/poll jobs skip this integration. Null = credentials believed valid.
    val credentialsErrorCode: Int? = null,
    val credentialsErrorMessage: String? = null,
    val credentialsErrorAt: LocalDateTime? = null,
    val createdAt: LocalDateTime? = null,
    val updatedAt: LocalDateTime? = null,
) {
    companion object {
        fun from(
            row: ResultRow,
            encryptionService: EncryptionService,
        ): SesHospedajesIntegration =
            SesHospedajesIntegration(
                id = row[AccountSesHospedajesIntegrationsTable.id],
                accountIntegrationId = row[AccountSesHospedajesIntegrationsTable.accountIntegrationId],
                accountId = row[AccountIntegrationsTable.accountId],
                arrendadorCode = row[AccountSesHospedajesIntegrationsTable.arrendadorCode],
                sesUsername = row[AccountSesHospedajesIntegrationsTable.sesUsername],
                sesPassword = encryptionService.decrypt(row[AccountSesHospedajesIntegrationsTable.sesPassword]),
                enabled = row[AccountSesHospedajesIntegrationsTable.enabled],
                rhDirectBookingsEnabled = row[AccountSesHospedajesIntegrationsTable.rhDirectBookingsEnabled],
                credentialsErrorCode = row[AccountSesHospedajesIntegrationsTable.credentialsErrorCode],
                credentialsErrorMessage = row[AccountSesHospedajesIntegrationsTable.credentialsErrorMessage],
                credentialsErrorAt = row[AccountSesHospedajesIntegrationsTable.credentialsErrorAt]?.toLocalDateTime(),
                createdAt = row[AccountSesHospedajesIntegrationsTable.createdAt].toLocalDateTime(),
                updatedAt = row[AccountSesHospedajesIntegrationsTable.updatedAt].toLocalDateTime(),
            )
    }
}
