package eu.kasify.model.api.responses

import eu.kasify.model.api.Guest
import io.perracodex.exposed.pagination.Page
import kotlinx.serialization.Serializable

@Serializable
data class GuestsResponse(
    val guests: List<Guest>,
    override val page: Int?,
    override val size: Int?,
    override val total: Int?,
) : PaginatedResponse {
    companion object {
        fun from(
            guests: List<Guest>,
            page: Page<*>?,
        ): GuestsResponse =
            GuestsResponse(
                guests = guests,
                page = page?.details?.pageIndex ?: 0,
                size = page?.details?.elementsPerPage ?: 0,
                total = page?.details?.totalElements ?: 0,
            )
    }
}
