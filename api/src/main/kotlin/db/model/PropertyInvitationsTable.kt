package eu.kasify.db.model
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.timestampWithTimeZone

object PropertyInvitationsTable : Table(name = "crew_invitations") {
    val id = uuid("id").autoGenerate()
    val propertyId = reference("property_id", PropertiesTable.id)
    val email = text("email").nullable()
    val token = varchar("token", length = 64).uniqueIndex()
    val maxUses = integer("max_uses").nullable().default(1)
    val uses = integer("uses").default(0)
    val expiresAt = timestampWithTimeZone("expires_at").nullable()
    val createdAt = timestampWithTimeZone("created_at")
    val acceptedAt = timestampWithTimeZone("accepted_at").nullable()
    val accessLevel =
        customEnumeration(
            name = "access_level",
            sql = "property_access_level",
            fromDb = { value -> PropertyAccessLevel.fromDb(value as String) },
            toDb = { PGEnum("property_access_level", it) },
        ).default(PropertyAccessLevel.Contractor)
    val locale = varchar("locale", length = 5).default("en")

    override val primaryKey = PrimaryKey(id)
}
