package eu.kasify.model.api

import eu.kasify.serialization.JavaLocalDateTimeSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.LocalDateTime
import kotlin.uuid.Uuid
import eu.kasify.model.User as DomainUser

@Serializable
data class User(
    val id: Uuid,
    @SerialName("account_id")
    val accountId: Uuid,
    val name: String,
    @SerialName("last_name")
    val lastName: String,
    val email: String,
    @Serializable(with = JavaLocalDateTimeSerializer::class)
    @SerialName("created_at")
    val createdAt: LocalDateTime?,
) {
    companion object {
        fun from(user: DomainUser) =
            User(
                id = user.id,
                accountId = user.accountId,
                name = user.name,
                lastName = user.lastName,
                email = user.email,
                createdAt = user.createdAt,
            )
    }
}
