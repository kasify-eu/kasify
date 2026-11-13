package eu.kasify.model

import eu.kasify.db.model.UsersTable
import eu.kasify.model.email.Locale
import io.perracodex.exposed.pagination.MapModel
import org.jetbrains.exposed.v1.core.ResultRow
import java.time.LocalDateTime
import kotlin.uuid.Uuid

data class User(
    val id: Uuid,
    val accountId: Uuid,
    val name: String,
    val lastName: String,
    val email: String,
    val locale: Locale = Locale.ES,
    val createdAt: LocalDateTime? = null,
) {
    companion object : MapModel<User> {
        override fun from(row: ResultRow): User =
            User(
                id = row[UsersTable.id],
                accountId = row[UsersTable.accountId],
                name = row[UsersTable.name],
                lastName = row[UsersTable.lastName],
                email = row[UsersTable.email],
                locale = Locale.fromTag(row[UsersTable.locale]),
                createdAt = row[UsersTable.createdAt].toLocalDateTime(),
            )
    }
}
