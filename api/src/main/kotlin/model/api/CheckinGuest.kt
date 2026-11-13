package eu.kasify.model.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid
import eu.kasify.model.Guest as DomainGuest

/**
 * Redacted guest shape for the public (unauthenticated) check-in surface.
 * The check-in form only renders a registered-guest checklist, so document
 * numbers, date of birth, residence and contact data must never appear here —
 * use [Guest] on authenticated endpoints instead.
 */
@Serializable
class CheckinGuest(
    val id: Uuid,
    @SerialName("first_name")
    val firstName: String,
    @SerialName("last_name")
    val lastName: String,
) {
    companion object {
        fun from(guest: DomainGuest): CheckinGuest =
            CheckinGuest(
                id = guest.id,
                firstName = guest.firstName,
                lastName = guest.lastName,
            )
    }
}
