package eu.kasify.model.api.responses

import eu.kasify.model.api.Province
import io.perracodex.exposed.pagination.Page
import kotlinx.serialization.Serializable

@Serializable
data class ProvincesResponse(
    val provinces: List<Province>,
    override val page: Int?,
    override val size: Int?,
    override val total: Int?,
) : PaginatedResponse {
    constructor(data: Page<eu.kasify.model.Province>?) : this(
        provinces = data?.content?.map { Province.from(it) } ?: emptyList(),
        page = data?.details?.pageIndex ?: 0,
        size = data?.details?.elementsPerPage ?: 0,
        total = data?.details?.totalElements ?: 0,
    )
}
