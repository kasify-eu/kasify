package eu.kasify.model.api.responses

import eu.kasify.model.api.AdminGuest
import kotlinx.serialization.Serializable

@Serializable
data class AdminGuestsPageResponse(
    val items: List<AdminGuest>,
    override val page: Int?,
    override val size: Int?,
    override val total: Int?,
) : PaginatedResponse
