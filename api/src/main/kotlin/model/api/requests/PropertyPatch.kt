package eu.kasify.model.api.requests

import io.ktor.server.plugins.requestvalidation.ValidationResult
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

/**
 * JSON Merge Patch–style partial update for a property.
 *
 * Only fields that are explicitly set on the wire are applied; missing fields are left untouched.
 * Because Kotlin's nullable + default mechanism cannot distinguish "absent" from "null", PATCH
 * cannot clear a nullable column to NULL — that case is out of scope for v1. Exception:
 * `arrendador_nif` accepts an explicit empty string as "clear to NULL".
 *
 * Geo fields (country/province/city) and `airbnb_ical_url` are intentionally excluded; they
 * require side effects that don't compose cleanly with partial updates and remain on PUT.
 */
@Serializable
data class PropertyPatch(
    val name: String? = null,
    val address: String? = null,
    @SerialName("zip_code")
    val zipcode: String? = null,
    @SerialName("default_cleaner_id")
    val defaultCleanerId: Uuid? = null,
    @SerialName("ses_establishment_code")
    val sesEstablishmentCode: String? = null,
    // The SES integration to assign when setting a non-blank establishment code. Only accepted
    // together with ses_establishment_code; an explicit "" code clears both code and assignment
    // (same clear-to-NULL convention as arrendador_nif).
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
    @SerialName("checkin_time")
    val checkinTime: String? = null,
    @SerialName("checkout_time")
    val checkoutTime: String? = null,
)

fun PropertyPatch.validate(): ValidationResult {
    val errors = mutableListOf<String>()

    this.name?.let { name ->
        if (name.isBlank()) {
            errors.add("Property name must not be blank")
        }
        if (name.length > PROPERTY_MAX_NAME_LENGTH) {
            errors.add("Property name must not exceed $PROPERTY_MAX_NAME_LENGTH characters")
        }
    }

    this.address?.let { address ->
        if (address.isBlank()) {
            errors.add("Property address must not be blank")
        }
    }

    this.zipcode?.let { zipcode ->
        if (zipcode.isBlank()) {
            errors.add("Property zipcode must not be blank")
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

    this.roomsCount?.let { rooms ->
        if (rooms < 1) {
            errors.add("rooms_count must be at least 1")
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
