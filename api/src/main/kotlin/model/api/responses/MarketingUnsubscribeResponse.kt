package eu.kasify.model.api.responses

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Confirmation returned by the public unsubscribe endpoint. The email is
 * masked (e.g. `j***@gmail.com`) because the link authenticates by token
 * only — anyone holding the URL can call it, so it must not leak the full
 * address.
 */
@Serializable
data class MarketingUnsubscribeResponse(
    @SerialName("email_masked")
    val emailMasked: String,
)
