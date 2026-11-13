package eu.kasify.model.api

import eu.kasify.model.email.Locale
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
data class UpdateAccountRequest(
    val name: String,
    @SerialName("last_name")
    val lastName: String,
    // Preferred language for the UI and notifications. Optional so older clients stay compatible.
    val locale: Locale? = null,
)

@Serializable
data class Account(
    var id: Uuid,
    @SerialName("account_id")
    val accountId: Uuid,
    val name: String,
    @SerialName("last_name")
    val lastName: String,
    val email: String,
    val locale: Locale,
) {
    companion object {
        fun from(user: eu.kasify.model.User) =
            Account(
                id = user.id,
                accountId = user.accountId,
                name = user.name,
                lastName = user.lastName,
                email = user.email,
                locale = user.locale,
            )
    }
}
