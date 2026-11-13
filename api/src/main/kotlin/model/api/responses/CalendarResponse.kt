package eu.kasify.model.api.responses

import eu.kasify.model.api.CalendarEvent
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

/**
 * One row in the property-grouped calendar response: the property identity
 * alongside its events for the requested window.
 */
@Serializable
data class PropertyCalendar(
    val id: Uuid,
    val name: String,
    val events: List<CalendarEvent>,
)

/**
 * Response for the account-level calendar endpoint, grouped by property.
 * The UI renders one row per property; events for each property fall inside
 * that property's row, scoped to the [from]..[to] window (inclusive).
 */
@Serializable
data class CalendarResponse(
    val properties: List<PropertyCalendar>,
    val from: LocalDate,
    val to: LocalDate,
)

/**
 * Response for the per-property calendar endpoint (`/properties/{id}/calendar`).
 * Returns the events for the property within the [from, to] window (inclusive).
 */
@Serializable
data class PropertyCalendarEventsResponse(
    val events: List<CalendarEvent>,
    val from: LocalDate,
    val to: LocalDate,
)
