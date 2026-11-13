package eu.kasify.db.model
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.timestampWithTimeZone

object PropertyNukiSmartlocksTable : Table(name = "property_nuki_smartlocks") {
    val id = uuid("id").autoGenerate()
    val propertySmartlockId = reference("property_smartlock_id", PropertySmartlocksTable.id)
    val nukiIntegrationId = reference("nuki_integration_id", AccountNukiIntegrationsTable.id)
    val nukiSmartlockId = text("nuki_smartlock_id")

    val createdAt = timestampWithTimeZone("created_at")
    val updatedAt = timestampWithTimeZone("updated_at")

    override val primaryKey = PrimaryKey(id)
}
