package eu.kasify.model.api.responses

import eu.kasify.model.api.AdminProperty
import kotlinx.serialization.Serializable

@Serializable
data class AdminPropertiesPageResponse(
    val items: List<AdminProperty>,
    override val page: Int?,
    override val size: Int?,
    override val total: Int?,
) : PaginatedResponse
