package eu.kasify.model.api.requests

import eu.kasify.db.model.BookingChannel
import kotlinx.datetime.LocalDate
import kotlin.uuid.Uuid

data class BookingsFilters(
    val from: LocalDate? = null,
    val to: LocalDate? = null,
    val propertyId: Uuid? = null,
    val query: String? = null,
    val channel: BookingChannel? = null,
)
