package eu.kasify.services

import eu.kasify.db.GuestsRepository
import eu.kasify.db.MarketingSuppressionsRepository
import eu.kasify.model.api.responses.MarketingUnsubscribeResponse
import eu.kasify.routes.Errors
import eu.kasify.routes.exceptions.NotFoundException
import kotlin.uuid.Uuid

class MarketingService(
    private val guestsRepository: GuestsRepository,
    private val marketingSuppressionsRepository: MarketingSuppressionsRepository,
) {
    /**
     * Opt the guest holding [token] out of marketing emails (LSSI Art. 21 /
     * GDPR Art. 7(3) withdrawal). Suppression is keyed by email so it covers
     * every guest row sharing the address whose consent was recorded up to
     * now (each call refreshes the suppression time), and the operation is
     * idempotent — clicking the link twice is still a success.
     */
    suspend fun unsubscribe(token: Uuid): MarketingUnsubscribeResponse {
        val guest =
            guestsRepository.getGuestByMarketingUnsubscribeToken(token)
                ?: throw NotFoundException(
                    Errors.ERROR_MARKETING_UNSUBSCRIBE_NOT_FOUND,
                    code = Errors.CODE_MARKETING_UNSUBSCRIBE_NOT_FOUND,
                )
        val email =
            guest.email
                ?: throw NotFoundException(
                    Errors.ERROR_MARKETING_UNSUBSCRIBE_NOT_FOUND,
                    code = Errors.CODE_MARKETING_UNSUBSCRIBE_NOT_FOUND,
                )

        marketingSuppressionsRepository.suppress(email, guest.id)

        return MarketingUnsubscribeResponse(emailMasked = maskEmail(email))
    }

    private fun maskEmail(email: String): String {
        val at = email.indexOf('@')
        if (at <= 0) return "***"
        return "${email.first()}***${email.substring(at)}"
    }
}
