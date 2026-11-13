package eu.kasify.model.api.responses

import eu.kasify.model.Country
import eu.kasify.model.api.Property
import io.perracodex.exposed.pagination.Page
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
data class PropertiesResponse(
    val properties: List<Property>,
    override val page: Int?,
    override val size: Int?,
    override val total: Int?,
) : PaginatedResponse {
    constructor(
        data: Page<eu.kasify.model.Property>?,
        countriesById: Map<Uuid, Country> = emptyMap(),
    ) : this(
        properties =
            data?.content?.map { Property.from(it, country = countriesById[it.countryId]) }
                ?: emptyList(),
        page = data?.details?.pageIndex ?: 0,
        size = data?.details?.elementsPerPage ?: 0,
        total = data?.details?.totalElements ?: 0,
    )
}
