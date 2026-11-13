package eu.kasify.db.model
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.timestampWithTimeZone

object PropertySmartlocksTable : Table(name = "property_smartlocks") {
    val id = uuid("id").autoGenerate()
    val propertyId = reference("property_id", PropertiesTable.id)

    val vendor =
        customEnumeration(
            name = "vendor",
            sql = "smartlock_vendor",
            fromDb = { value ->
                SmartlockVendor.entries.find { it.name.equals(value as String, ignoreCase = true) }
                    ?: throw IllegalArgumentException("Invalid SmartlockVendor: $value")
            },
            toDb = { PGEnum("smartlock_vendor", it) },
        )

    val name = text("name")

    val createdAt = timestampWithTimeZone("created_at")
    val updatedAt = timestampWithTimeZone("updated_at")

    override val primaryKey = PrimaryKey(id)
}
