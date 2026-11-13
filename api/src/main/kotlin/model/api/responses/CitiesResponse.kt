package eu.kasify.model.api.responses

import eu.kasify.model.api.City
import io.perracodex.exposed.pagination.Page
import kotlinx.serialization.Serializable

@Serializable
data class CitiesResponse(
    val cities: List<City>,
    override val page: Int?,
    override val size: Int?,
    override val total: Int?,
) : PaginatedResponse {
    constructor(data: Page<eu.kasify.model.City>?) : this(
        cities = data?.content?.map { City.from(it) } ?: emptyList(),
        page = data?.details?.pageIndex ?: 0,
        size = data?.details?.elementsPerPage ?: 0,
        total = data?.details?.totalElements ?: 0,
    )
}
