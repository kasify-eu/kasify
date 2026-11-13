package eu.kasify.db.model
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.timestampWithTimeZone

object ApiTokensTable : Table(name = "api_tokens") {
    val id = uuid("id").autoGenerate()
    val userId = reference("user_id", UsersTable.id)
    val accountId = reference("account_id", AccountsTable.id)
    val name = text("name")
    val tokenHash = text("token_hash").uniqueIndex()
    val tokenPrefix = text("token_prefix")
    val expiresAt = timestampWithTimeZone("expires_at").nullable()
    val lastUsedAt = timestampWithTimeZone("last_used_at").nullable()
    val revokedAt = timestampWithTimeZone("revoked_at").nullable()
    val createdAt = timestampWithTimeZone("created_at")

    override val primaryKey = PrimaryKey(id)
}
