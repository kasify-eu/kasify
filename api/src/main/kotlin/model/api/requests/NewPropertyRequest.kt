package eu.kasify.model.api.requests

import eu.kasify.plugins.ical.IcalUrlGuard
import io.ktor.server.plugins.requestvalidation.ValidationResult
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.LocalTime
import java.time.format.DateTimeParseException
import kotlin.uuid.Uuid

@Serializable
data class NewPropertyRequest(
    val name: String,
    val address: String,
    val city: String? = null,
    @SerialName("zip_code")
    val zipcode: String,
    @SerialName("airbnb_ical_url")
    val airbnbIcalUrl: String? = null,
    @SerialName("booking_ical_url")
    val bookingIcalUrl: String? = null,
    @SerialName("default_cleaner_id")
    val defaultCleanerId: Uuid? = null,
    @SerialName("ses_establishment_code")
    val sesEstablishmentCode: String? = null,
    // Required alongside a non-blank establishment code: the SES integration that files for this
    // property. Only accepted together with ses_establishment_code.
    @SerialName("ses_integration_id")
    val sesIntegrationId: Uuid? = null,
    @SerialName("ses_establishment_type")
    val sesEstablishmentType: String? = null,
    @SerialName("arrendador_nif")
    val arrendadorNif: String? = null,
    @SerialName("has_internet")
    val hasInternet: Boolean? = null,
    @SerialName("rooms_count")
    val roomsCount: Int? = null,
    @SerialName("country_code")
    val countryCode: String? = null,
    @SerialName("province_id")
    val provinceId: Uuid? = null,
    @SerialName("city_id")
    val cityId: Uuid? = null,
    @SerialName("checkin_time")
    val checkinTime: String? = null,
    @SerialName("checkout_time")
    val checkoutTime: String? = null,
)

const val PROPERTY_MAX_NAME_LENGTH = 50
const val PROPERTY_DEFAULT_COUNTRY_ALPHA3 = "ESP"

fun NewPropertyRequest.validate(): ValidationResult {
    val errors = mutableListOf<String>()
    if (this.name.isBlank()) {
        errors.add("Property name must not be blank")
    }

    if (this.name.length > PROPERTY_MAX_NAME_LENGTH) {
        errors.add("Property name must not exceed $PROPERTY_MAX_NAME_LENGTH characters")
    }

    if (this.address.isBlank()) {
        errors.add("Property address must not be blank")
    }

    if (this.zipcode.isBlank()) {
        errors.add("Property zipcode must not be blank")
    }

    val resolvedCountryCode = this.countryCode?.uppercase() ?: PROPERTY_DEFAULT_COUNTRY_ALPHA3
    val isSpain = resolvedCountryCode == PROPERTY_DEFAULT_COUNTRY_ALPHA3
    if (isSpain) {
        if (this.provinceId == null) {
            errors.add("province_id is required for Spanish properties")
        }
        if (this.cityId == null) {
            errors.add("city_id is required for Spanish properties")
        }
    } else if (this.city.isNullOrBlank()) {
        errors.add("city is required for non-Spanish properties")
    }

    // The feeds are fetched server-side: refuse non-http(s) URLs and non-public hosts up front
    // (IcalUrlGuard re-checks the resolved addresses before every fetch).
    this.airbnbIcalUrl?.let { url ->
        if (!IcalUrlGuard.isAllowedUrl(url)) {
            errors.add("Airbnb iCal URL must be a valid URL")
        }
    }

    this.bookingIcalUrl?.let { url ->
        if (!IcalUrlGuard.isAllowedUrl(url)) {
            errors.add("Booking.com iCal URL must be a valid URL")
        }
    }

    this.checkinTime?.let { value ->
        if (parseTimeOfDay(value) == null) {
            errors.add("checkin_time must be a valid HH:mm or HH:mm:ss value")
        }
    }

    this.checkoutTime?.let { value ->
        if (parseTimeOfDay(value) == null) {
            errors.add("checkout_time must be a valid HH:mm or HH:mm:ss value")
        }
    }

    // Blank means "clear the NIF" (stored as NULL), so only non-blank values are validated.
    this.arrendadorNif?.let { nif ->
        if (nif.isNotBlank() && !isValidSpanishTaxId(nif)) {
            errors.add(ARRENDADOR_NIF_VALIDATION_ERROR)
        }
    }

    if (errors.isNotEmpty()) {
        return ValidationResult.Invalid(errors)
    }

    return ValidationResult.Valid
}

const val ARRENDADOR_NIF_VALIDATION_ERROR = "arrendador_nif must be a valid NIF, NIE or CIF"

private val DNI_REGEX = Regex("^[0-9]{8}[A-Z]$")
private val NIE_REGEX = Regex("^[XYZ][0-9]{7}[A-Z]$")

// K (minors under 14), L (Spaniards resident abroad) and M (foreigners without NIE) are personal
// NIFs with the same mod-23 control letter as a DNI, computed over the 7 digits.
private val KLM_NIF_REGEX = Regex("^[KLM][0-9]{7}[A-Z]$")

// CIF organization letters — K/L/M are personal NIF prefixes, not CIF prefixes.
private val CIF_REGEX = Regex("^[ABCDEFGHJNPQRSUVW][0-9]{7}[0-9A-J]$")
private const val DNI_CONTROL_LETTERS = "TRWAGMYFPDXBNJZSQVHLCKE"

/**
 * Canonical form of a Spanish tax id: uppercase with spaces and hyphens stripped. Persist this,
 * never the raw input — libro-registro entries denormalize the value and are append-only.
 */
fun normalizeSpanishTaxId(value: String): String = value.uppercase().replace(" ", "").replace("-", "")

/**
 * Spanish tax id (NIF/DNI incl. K/L/M, NIE, or CIF). Personal ids are checksum-validated (mod-23
 * control letter); CIF is format-only — its checksum varies by entity type and rejecting valid
 * edge cases would hurt more than accepting a rare typo.
 */
fun isValidSpanishTaxId(value: String): Boolean {
    val normalized = normalizeSpanishTaxId(value)
    return when {
        DNI_REGEX.matches(normalized) -> {
            val number = normalized.take(8).toInt()
            normalized.last() == DNI_CONTROL_LETTERS[number % 23]
        }
        NIE_REGEX.matches(normalized) -> {
            val prefixDigit = "XYZ".indexOf(normalized.first())
            val number = "$prefixDigit${normalized.substring(1, 8)}".toInt()
            normalized.last() == DNI_CONTROL_LETTERS[number % 23]
        }
        KLM_NIF_REGEX.matches(normalized) -> {
            val number = normalized.substring(1, 8).toInt()
            normalized.last() == DNI_CONTROL_LETTERS[number % 23]
        }
        else -> CIF_REGEX.matches(normalized)
    }
}

fun parseTimeOfDay(value: String): LocalTime? =
    try {
        LocalTime.parse(value)
    } catch (_: DateTimeParseException) {
        null
    }
