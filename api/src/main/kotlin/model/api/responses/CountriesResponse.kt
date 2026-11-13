package eu.kasify.model.api.responses

import eu.kasify.model.api.Country
import io.perracodex.exposed.pagination.Page
import kotlinx.serialization.Serializable

@Serializable
data class CountriesResponse(
    val countries: List<Country>,
    override val page: Int?,
    override val size: Int?,
    override val total: Int?,
) : PaginatedResponse {
    constructor(data: Page<eu.kasify.model.Country>?) : this(
        countries = data?.content?.map { Country.from(it) } ?: emptyList(),
        page = data?.details?.pageIndex ?: 0,
        size = data?.details?.elementsPerPage ?: 0,
        total = data?.details?.totalElements ?: 0,
    )
}
