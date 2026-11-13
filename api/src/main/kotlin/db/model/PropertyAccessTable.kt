package eu.kasify.db.model
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.timestampWithTimeZone

object PropertyAccessTable : Table(name = "property_access") {
    val id = uuid("id").autoGenerate()
    val userId = reference("user_id", UsersTable.id)
    val propertyId = reference("property_id", PropertiesTable.id)
    val service =
        customEnumeration(
            name = "service",
            sql = "property_service",
            fromDb = { value ->
                PropertyService.entries.find { it.name.equals(value as String, ignoreCase = true) }
                    ?: throw IllegalArgumentException("Invalid PropertyService: $value")
            },
            toDb = { PGEnum("property_service", it) },
        ).nullable()
    val accessLevel =
        customEnumeration(
            name = "access_level",
            sql = "property_access_level",
            fromDb = { value -> PropertyAccessLevel.fromDb(value as String) },
            toDb = { PGEnum("property_access_level", it) },
        ).default(PropertyAccessLevel.Contractor)
    val createdAt = timestampWithTimeZone("created_at")

    override val primaryKey = PrimaryKey(id)
}
