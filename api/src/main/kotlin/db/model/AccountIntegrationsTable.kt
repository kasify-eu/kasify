package eu.kasify.db.model
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.timestampWithTimeZone

object AccountIntegrationsTable : Table(name = "account_integrations") {
    val id = uuid("id").autoGenerate()
    val accountId = reference("account_id", AccountsTable.id)

    val vendor =
        customEnumeration(
            name = "vendor",
            sql = "integration_vendor",
            fromDb = { value ->
                IntegrationVendor.entries.find { it.name.equals(value as String, ignoreCase = true) }
                    ?: throw IllegalArgumentException("Invalid IntegrationVendor: $value")
            },
            toDb = { PGEnum("integration_vendor", it) },
        )

    val createdAt = timestampWithTimeZone("created_at")
    val updatedAt = timestampWithTimeZone("updated_at")

    override val primaryKey = PrimaryKey(id)
}
