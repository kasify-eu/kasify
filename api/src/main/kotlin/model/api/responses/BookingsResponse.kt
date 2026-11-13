package eu.kasify.model.api.responses

import eu.kasify.model.api.Booking
import io.perracodex.exposed.pagination.Page
import kotlinx.serialization.Serializable

@Serializable
data class BookingsResponse(
    val bookings: List<Booking>,
    override val page: Int?,
    override val size: Int?,
    override val total: Int?,
) : PaginatedResponse {
    constructor(data: Page<eu.kasify.model.Booking>?, role: String = "owner") : this(
        bookings = data?.content?.map { Booking.from(it, role = role) } ?: emptyList(),
        page = data?.details?.pageIndex ?: 0,
        size = data?.details?.elementsPerPage ?: 0,
        total = data?.details?.totalElements ?: 0,
    )
}
