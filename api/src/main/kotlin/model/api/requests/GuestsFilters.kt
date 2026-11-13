package eu.kasify.model.api.requests

import kotlin.uuid.Uuid

data class GuestsFilters(
    val propertyId: Uuid? = null,
    val channelId: String? = null,
    val query: String? = null,
)
