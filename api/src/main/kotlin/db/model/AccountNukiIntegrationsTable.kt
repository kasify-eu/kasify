package eu.kasify.db.model
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.timestampWithTimeZone

object AccountNukiIntegrationsTable : Table(name = "account_nuki_integrations") {
    val id = uuid("id").autoGenerate()
    val accountIntegrationId = reference("account_integration_id", AccountIntegrationsTable.id).uniqueIndex()

    val nukiToken = text("nuki_token")

    val createdAt = timestampWithTimeZone("created_at")
    val updatedAt = timestampWithTimeZone("updated_at")

    override val primaryKey = PrimaryKey(id)
}
