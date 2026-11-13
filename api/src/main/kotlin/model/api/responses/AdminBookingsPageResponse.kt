package eu.kasify.model.api.responses

import eu.kasify.model.api.Booking
import kotlinx.serialization.Serializable

@Serializable
data class AdminBookingsPageResponse(
    val items: List<Booking>,
    override val page: Int?,
    override val size: Int?,
    override val total: Int?,
) : PaginatedResponse
