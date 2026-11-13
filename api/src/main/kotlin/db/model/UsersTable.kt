package eu.kasify.db.model
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.timestampWithTimeZone

object UsersTable : Table(name = "users") {
    val id = uuid("id").autoGenerate()
    val accountId = reference("account_id", AccountsTable.id)
    val externalUserId = varchar("external_user_id", length = 50)

    val name = varchar("name", length = 50)
    val lastName = varchar("last_name", length = 50)
    val email = varchar("email", length = 50)
    val locale = varchar("locale", length = 5)

    val createdAt = timestampWithTimeZone("created_at")

    override val primaryKey = PrimaryKey(id)
}
