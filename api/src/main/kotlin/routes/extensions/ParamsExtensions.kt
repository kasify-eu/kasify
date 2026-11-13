package eu.kasify.routes.extensions

import eu.kasify.model.api.requests.BookingsFilters
import eu.kasify.model.api.requests.GuestsFilters
import eu.kasify.routes.exceptions.BadParamRequestException
import io.ktor.server.application.ApplicationCall
import io.ktor.server.plugins.MissingRequestParameterException
import kotlinx.datetime.LocalDate
import kotlin.uuid.Uuid

const val INVALID_PROPERTY_ID_FORMAT_ERROR = "Property ID has an incorrect format"

fun ApplicationCall.getPropertyId(): Uuid {
    val id = parameters["id"] ?: throw MissingRequestParameterException("Missing property ID")

    return try {
        Uuid.parse(id)
    } catch (_: Exception) {
        throw BadParamRequestException(INVALID_PROPERTY_ID_FORMAT_ERROR)
    }
}

const val INVALID_BOOKING_ID_FORMAT_ERROR = "Booking ID has an incorrect format"

fun ApplicationCall.getBookingId(): Uuid {
    val id = parameters["id"] ?: throw MissingRequestParameterException("Missing booking ID")

    return try {
        Uuid.parse(id)
    } catch (_: Exception) {
        throw BadParamRequestException(INVALID_BOOKING_ID_FORMAT_ERROR)
    }
}

fun ApplicationCall.getBookingCode(): String = parameters["code"] ?: throw MissingRequestParameterException("Missing booking code")

const val INVALID_BOOKING_ACCESS_CODE_ID_FORMAT_ERROR = "Booking Access Code ID has an incorrect format"

fun ApplicationCall.getBookingAccessCodeId(): Uuid {
    val id = parameters["id"] ?: throw MissingRequestParameterException("Missing booking access code ID")

    return try {
        Uuid.parse(id)
    } catch (_: Exception) {
        throw BadParamRequestException(INVALID_BOOKING_ACCESS_CODE_ID_FORMAT_ERROR)
    }
}

const val INVALID_SMARTLOCK_ID_FORMAT_ERROR = "Smartlock ID has an incorrect format"

fun ApplicationCall.getSmartlockIdParam(): Uuid {
    val id = parameters["smartlockId"] ?: throw MissingRequestParameterException("Smartlock ID is required")

    return try {
        Uuid.parse(id)
    } catch (_: Exception) {
        throw BadParamRequestException(INVALID_SMARTLOCK_ID_FORMAT_ERROR)
    }
}

fun ApplicationCall.getBookingsFilters(): BookingsFilters {
    val from =
        parameters["from"]?.let {
            try {
                LocalDate.parse(it)
            } catch (e: Exception) {
                throw BadParamRequestException("Invalid 'from' date format. Expected: YYYY-MM-DD")
            }
        }

    val to =
        parameters["to"]?.let {
            try {
                LocalDate.parse(it)
            } catch (e: Exception) {
                throw BadParamRequestException("Invalid 'to' date format. Expected: YYYY-MM-DD")
            }
        }

    val propertyId =
        parameters["property_id"]?.let {
            try {
                Uuid.parse(it)
            } catch (e: Exception) {
                throw BadParamRequestException("Invalid 'property_id' format")
            }
        }

    val query = parameters["query"]?.takeIf { it.isNotBlank() }

    val channel =
        parameters["channel"]?.let {
            try {
                eu.kasify.db.model.BookingChannel
                    .valueOf(it)
            } catch (e: Exception) {
                throw BadParamRequestException("Invalid 'channel' value: $it")
            }
        }

    return BookingsFilters(
        from = from,
        to = to,
        propertyId = propertyId,
        query = query,
        channel = channel,
    )
}

/**
 * Tri-state integration filter param: `configured` → true, `not_configured` → false,
 * absent → null (no filtering). Any other value is a 400.
 */
fun ApplicationCall.getConfiguredFilter(name: String): Boolean? =
    when (val value = parameters[name]) {
        null -> null
        "configured" -> true
        "not_configured" -> false
        else -> throw BadParamRequestException("Invalid '$name' value: $value. Expected 'configured' or 'not_configured'")
    }

fun ApplicationCall.getGuestsFilters(): GuestsFilters {
    val propertyId =
        parameters["property_id"]?.let {
            try {
                Uuid.parse(it)
            } catch (e: Exception) {
                throw BadParamRequestException("Invalid 'property_id' format")
            }
        }

    val channelId = parameters["channel_id"]?.takeIf { it.isNotBlank() }

    val query = parameters["query"]?.takeIf { it.isNotBlank() }

    return GuestsFilters(
        propertyId = propertyId,
        channelId = channelId,
        query = query,
    )
}
