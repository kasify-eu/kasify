package eu.kasify.db.model
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.timestampWithTimeZone

object PropertyUnifiSmartlocksTable : Table(name = "property_unifi_smartlocks") {
    val id = uuid("id").autoGenerate()
    val propertySmartlockId = reference("property_smartlock_id", PropertySmartlocksTable.id)
    val floorId = text("floor_id")
    val doorId = text("door_id")

    val createdAt = timestampWithTimeZone("created_at")
    val updatedAt = timestampWithTimeZone("updated_at")

    override val primaryKey = PrimaryKey(id)
}
