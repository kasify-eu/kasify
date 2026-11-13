package eu.kasify.db
import eu.kasify.db.model.AccountsTable
import eu.kasify.db.model.PropertyAccessLevel
import eu.kasify.db.model.UsersTable
import eu.kasify.model.User
import eu.kasify.model.api.PropertyMember
import eu.kasify.model.email.Locale
import io.perracodex.exposed.pagination.Page
import io.perracodex.exposed.pagination.Pageable
import io.perracodex.exposed.pagination.paginate
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.core.lowerCase
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.insertReturning
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.uuid.Uuid

open class UsersRepository {
    open suspend fun getUserById(id: Uuid): User? =
        dbQuery {
            UsersTable
                .selectAll()
                .where { UsersTable.id eq id }
                .singleOrNull()
                ?.let { User.from(it) }
        }

    /** Ids of every user in an account (its owners). Selects only the id column — callers that also
     *  need locale/name resolve the rows via [getUsersByIds]. */
    open suspend fun getUserIdsByAccountId(accountId: Uuid): List<Uuid> =
        dbQuery {
            UsersTable
                .select(UsersTable.id)
                .where { UsersTable.accountId eq accountId }
                .map { it[UsersTable.id] }
        }

    /** Fetch users by id. Used to resolve notification recipients (with their preferred locale). */
    open suspend fun getUsersByIds(ids: List<Uuid>): List<User> =
        dbQuery {
            if (ids.isEmpty()) {
                emptyList()
            } else {
                UsersTable
                    .selectAll()
                    .where { UsersTable.id inList ids }
                    .map { User.from(it) }
            }
        }

    suspend fun getUserByExternalId(externalUserId: String): User? =
        dbQuery {
            UsersTable
                .selectAll()
                .where { UsersTable.externalUserId eq externalUserId }
                .singleOrNull()
                ?.let { User.from(it) }
        }

    suspend fun createUserWithAccount(
        externalUserId: String,
        name: String,
        lastName: String,
        email: String,
        locale: Locale,
    ): User {
        lateinit var result: User

        dbQuery {
            // Create account first
            val account = AccountsTable.insertReturning {}

            account.firstOrNull()?.let { it ->
                val accountId = it[AccountsTable.id]

                // Create user with the new account
                val user =
                    UsersTable.insertReturning {
                        it[UsersTable.accountId] = accountId
                        it[UsersTable.externalUserId] = externalUserId
                        it[UsersTable.name] = name
                        it[UsersTable.lastName] = lastName
                        it[UsersTable.email] = email
                        it[UsersTable.locale] = locale.tag
                    }

                user.firstOrNull()?.let { it ->
                    result = User.from(it)
                }
            }
        }

        return result
    }

    suspend fun updateUser(
        id: Uuid,
        name: String,
        lastName: String,
        locale: Locale? = null,
    ): Unit =
        dbQuery {
            UsersTable.update({ UsersTable.id eq id }) {
                it[UsersTable.name] = name
                it[UsersTable.lastName] = lastName
                if (locale != null) it[UsersTable.locale] = locale.tag
            }
        }

    /**
     * Cross-account users list for the admin users view. Optional free-text `query` matches
     * the user's name, last name or email. Newest first.
     */
    suspend fun getAllUsers(
        query: String?,
        pageable: Pageable?,
    ): Page<User> =
        dbQuery {
            var where: Op<Boolean> = Op.TRUE

            if (!query.isNullOrBlank()) {
                val pattern = "%${escapeLike(query.trim().lowercase())}%"
                where =
                    where and (
                        (UsersTable.name.lowerCase() like pattern) or
                            (UsersTable.lastName.lowerCase() like pattern) or
                            (UsersTable.email.lowerCase() like pattern)
                    )
            }

            UsersTable
                .selectAll()
                .where(where)
                .orderBy(UsersTable.createdAt to SortOrder.DESC)
                .paginate(
                    pageable = pageable,
                    map = User.Companion,
                )
        }

    suspend fun getUsersByAccountId(accountId: Uuid): List<PropertyMember> =
        dbQuery {
            UsersTable
                .selectAll()
                .where { UsersTable.accountId eq accountId }
                .map {
                    PropertyMember(
                        id = it[UsersTable.id],
                        name = it[UsersTable.name],
                        lastName = it[UsersTable.lastName],
                        email = it[UsersTable.email],
                        accessLevel = PropertyAccessLevel.Owner,
                    )
                }
        }
}
