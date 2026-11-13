package eu.kasify.db.model
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.timestampWithTimeZone

object AccountUnifiIntegrationsTable : Table(name = "account_unifi_integrations") {
    val id = uuid("id").autoGenerate()
    val accountIntegrationId = reference("account_integration_id", AccountIntegrationsTable.id).uniqueIndex()

    val unifiUsername = text("unifi_username")
    val unifiPassword = text("unifi_password")
    val unifiConsoleUrl = text("unifi_console_url")

    // Session caching columns
    val sessionCsrfToken = text("session_csrf_token").nullable()
    val sessionCookies = text("session_cookies").nullable()
    val sessionExpiresAt = timestampWithTimeZone("session_expires_at").nullable()

    val createdAt = timestampWithTimeZone("created_at")
    val updatedAt = timestampWithTimeZone("updated_at")

    override val primaryKey = PrimaryKey(id)
}
