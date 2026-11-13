package eu.kasify.db.model
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.timestampWithTimeZone

object AccountSesHospedajesIntegrationsTable : Table(name = "account_ses_hospedajes_integrations") {
    val id = uuid("id").autoGenerate()
    val accountIntegrationId = reference("account_integration_id", AccountIntegrationsTable.id).uniqueIndex()

    val arrendadorCode = varchar("arrendador_code", 10)
    val sesUsername = text("ses_username")
    val sesPassword = text("ses_password")
    val enabled = bool("enabled").default(true)
    val rhDirectBookingsEnabled = bool("rh_direct_bookings_enabled").default(true)

    // Credential-error state (SES códigos 10103/10107/10119/10120). NULL code = credentials
    // believed valid. The three columns are always set and cleared together.
    val credentialsErrorCode = integer("credentials_error_code").nullable()
    val credentialsErrorMessage = text("credentials_error_message").nullable()
    val credentialsErrorAt = timestampWithTimeZone("credentials_error_at").nullable()

    val createdAt = timestampWithTimeZone("created_at")
    val updatedAt = timestampWithTimeZone("updated_at")

    override val primaryKey = PrimaryKey(id)
}
