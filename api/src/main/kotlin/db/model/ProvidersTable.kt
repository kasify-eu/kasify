package eu.kasify.db.model
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.timestampWithTimeZone

object ProvidersTable : Table(name = "providers") {
    val id = uuid("id").autoGenerate()
    val name = varchar("name", length = 255)

    val createdAt = timestampWithTimeZone("created_at")
    val deletedAt = timestampWithTimeZone("deleted_at").nullable()

    override val primaryKey = PrimaryKey(AccountsTable.id)
}
