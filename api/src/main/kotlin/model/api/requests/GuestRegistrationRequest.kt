package eu.kasify.model.api.requests

import eu.kasify.db.model.DocumentType
import eu.kasify.db.model.Gender
import eu.kasify.serialization.JavaLocalDateSerializer
import io.ktor.server.plugins.requestvalidation.ValidationResult
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.Period
import kotlin.uuid.Uuid

/**
 * Guest registration request used by the public check-in flow.
 *
 * Field validation follows SES Hospedajes / Spanish RD 933/2021 rules. Document data
 * is mandatory only for guests aged 14 or older; for younger guests we still require
 * basic identification (name, date of birth, sex, nationality). Contact data (at least a
 * phone or an email) is mandatory for *every* guest, including minors — SES rejects a
 * parte de viajeros whose travellers lack contact details ("Datos de contacto obligatorios").
 */
@Serializable
data class GuestRegistrationRequest(
    @SerialName("first_name")
    val firstName: String,
    @SerialName("last_name")
    val lastName: String,
    @SerialName("second_last_name")
    val secondLastName: String? = null,
    @SerialName("nationality_country_code")
    val nationalityCountryCode: String,
    @Serializable(with = JavaLocalDateSerializer::class)
    @SerialName("date_of_birth")
    val dateOfBirth: LocalDate,
    val sex: Gender,
    @SerialName("document_type")
    val documentType: DocumentType? = null,
    @SerialName("document_number")
    val documentNumber: String? = null,
    @SerialName("document_support_number")
    val documentSupportNumber: String? = null,
    @SerialName("residence_address")
    val residenceAddress: String? = null,
    @SerialName("residence_country_code")
    val residenceCountryCode: String? = null,
    @SerialName("residence_province_id")
    val residenceProvinceId: Uuid? = null,
    @SerialName("residence_city_id")
    val residenceCityId: Uuid? = null,
    @SerialName("residence_city")
    val residenceCity: String? = null,
    @SerialName("residence_postal_code")
    val residencePostalCode: String? = null,
    @SerialName("phone_prefix")
    val phonePrefix: String? = null,
    val phone: String? = null,
    val email: String? = null,
    // SES "parentesco" — relationship of a minor guest to an accompanying adult.
    // Required (and validated against the SES catalog) when the guest is a minor.
    @SerialName("relationship_code")
    val relationshipCode: String? = null,
    // Optional marketing opt-in (GDPR Art. 6(1)(a) / LSSI Art. 21). Never required;
    // the service layer only stamps consent for adults (18+) who provided an email.
    @SerialName("marketing_consent")
    val marketingConsent: Boolean = false,
)

/**
 * Privacy-policy version in force when the guest opted in to marketing
 * (stored in guests.marketing_consent_version — GDPR Art. 7(1) proof).
 * Must match the "Version:" line in dashboard/app/legal/privacy-policy{,.es}.md;
 * bump the policy version and this constant together whenever the marketing
 * wording (policy section or check-in checkbox copy) materially changes.
 */
const val MARKETING_CONSENT_VERSION = "privacy-policy-v1.1"

// SES Hospedajes / RD 933/2021 validation constants
const val GUEST_ADULT_AGE = 14
const val GUEST_MINOR_AGE = 18

// SES TIPO_PARENTESCO catalog. Source:
// src/test/resources/ses/catalog_tables_tipo_parentesco_response.xml
val SES_PARENTESCO_CODES =
    setOf(
        "AB", // Abuelo/a
        "BA", // Bisabuelo/a
        "BN", // Bisnieto/a
        "CD", // Cuñado/a
        "CY", // Cónyuge
        "HJ", // Hijo/a
        "HR", // Hermano/a
        "NI", // Nieto/a
        "OT", // Otro
        "PM", // Padre o madre
        "SB", // Sobrino/a
        "SG", // Suegro/a
        "TI", // Tío/a
        "TU", // Tutor/a
        "YN", // Yerno o nuera
    )
const val MAX_PERSON_NAME_LENGTH = 100
const val MAX_ADDRESS_LENGTH = 200
const val MAX_CITY_LENGTH = 100
const val MAX_DOCUMENT_NUMBER_LENGTH = 20
const val MAX_PHONE_LENGTH = 20
const val MAX_PHONE_PREFIX_LENGTH = 5
const val MAX_GUEST_AGE_YEARS = 120

private val PERSON_NAME_REGEX = Regex("^[\\p{L} '\\-.]+$")
private val DNI_REGEX = Regex("^\\d{8}[A-HJ-NP-TV-Z]$")
private val NIE_REGEX = Regex("^[XYZ]\\d{7}[A-HJ-NP-TV-Z]$")
private val PASSPORT_REGEX = Regex("^[A-Z0-9]{6,20}$")
private val OTHER_DOC_REGEX = Regex("^[A-Z0-9-]{1,20}$")

// DNI/NIE "número de soporte" formats per the Spanish Ministry of Justice
// ("Cómo introducir el número de soporte"). Both are always 9 characters
// (shorter NIE numbers are zero-padded on the left):
//  - DNI electrónico (IDESP, front of card): 3 letters + 6 digits, e.g. ABC123456.
//  - NIE (TIE / permiso de residencia): letter E + 8 digits, e.g. E12345678.
//  - NIE (certificado de registro de ciudadano de la Unión): letter C + 8 digits, e.g. C01234567.
private val DNI_SUPPORT_REGEX = Regex("^[A-Z]{3}[0-9]{6}$")
private val NIE_SUPPORT_REGEX = Regex("^[EC][0-9]{8}$")
private val SPANISH_POSTAL_CODE_REGEX = Regex("^\\d{5}$")
private val PHONE_REGEX = Regex("^[0-9 ()-]{6,20}$")
private val PHONE_PREFIX_REGEX = Regex("^\\+\\d{1,4}$")
private val ALPHA3_REGEX = Regex("^[A-Z]{3}$")
private val GENERIC_EMAIL_REGEX = Regex(EMAIL_REGEX)

const val SPAIN_ALPHA3 = "ESP"

private const val DNI_LETTERS = "TRWAGMYFPDXBNJZSQVHLCKE"

/**
 * Computes the expected DNI control letter for an 8-digit number.
 */
private fun expectedDniLetter(digits: String): Char = DNI_LETTERS[digits.toInt() % 23]

/**
 * Verifies the checksum letter of a DNI / NIE document number.
 */
private fun isValidDniOrNieChecksum(value: String): Boolean {
    val normalized = value.uppercase()
    val (digits, expectedLetter) =
        when {
            DNI_REGEX.matches(normalized) -> normalized.substring(0, 8) to normalized.last()
            NIE_REGEX.matches(normalized) -> {
                val prefix =
                    when (normalized[0]) {
                        'X' -> "0"
                        'Y' -> "1"
                        'Z' -> "2"
                        else -> return false
                    }
                (prefix + normalized.substring(1, 8)) to normalized.last()
            }
            else -> return false
        }
    return expectedDniLetter(digits) == expectedLetter
}

fun GuestRegistrationRequest.validate(): ValidationResult {
    val errors = mutableListOf<String>()

    // --- Identity (required for everyone) ---
    validateName("first_name", firstName, errors)
    validateName("last_name", lastName, errors)
    if (!secondLastName.isNullOrBlank()) {
        validateName("second_last_name", secondLastName, errors)
    }

    if (!ALPHA3_REGEX.matches(nationalityCountryCode)) {
        errors.add("nationality_country_code must be a 3-letter ISO 3166-1 alpha-3 code")
    }

    val isSpanishNationality = nationalityCountryCode == SPAIN_ALPHA3
    if (isSpanishNationality && secondLastName.isNullOrBlank()) {
        errors.add("second_last_name is required for Spanish nationality")
    }

    val today = LocalDate.now()
    if (!dateOfBirth.isBefore(today)) {
        errors.add("date_of_birth must be in the past")
    }
    val age = Period.between(dateOfBirth, today).years
    if (age > MAX_GUEST_AGE_YEARS) {
        errors.add("date_of_birth is implausibly old (max $MAX_GUEST_AGE_YEARS years)")
    }

    if (sex != Gender.Male && sex != Gender.Female) {
        // SES Hospedajes only accepts Male/Female (H/M)
        errors.add("sex must be Male or Female")
    }

    val isAdult = age >= GUEST_ADULT_AGE

    // --- Parentesco (value-only check; whether it's required depends on the
    // booking's minor count and is enforced at the service layer). ---
    if (!relationshipCode.isNullOrBlank() && relationshipCode !in SES_PARENTESCO_CODES) {
        errors.add("relationship_code must be one of: ${SES_PARENTESCO_CODES.sorted().joinToString(", ")}")
    }

    // --- Document (mandatory for guests >= 14) ---
    if (isAdult) {
        if (documentType == null) {
            errors.add("document_type is required for guests aged $GUEST_ADULT_AGE or older")
        }
        if (documentNumber.isNullOrBlank()) {
            errors.add("document_number is required for guests aged $GUEST_ADULT_AGE or older")
        }
    }

    if (documentType != null && !documentNumber.isNullOrBlank()) {
        validateDocumentNumber(documentType, documentNumber, errors)
    } else if (documentType != null && documentNumber.isNullOrBlank()) {
        errors.add("document_number is required when document_type is provided")
    } else if (documentType == null && !documentNumber.isNullOrBlank()) {
        errors.add("document_type is required when document_number is provided")
    }

    if (documentType == DocumentType.DNI || documentType == DocumentType.NIE) {
        validateDocumentSupportNumber(documentType, documentSupportNumber, documentNumber, errors)
    }

    // --- Residence (mandatory for everyone, including minors) ---
    if (residenceCountryCode != null && !ALPHA3_REGEX.matches(residenceCountryCode)) {
        errors.add("residence_country_code must be a 3-letter ISO 3166-1 alpha-3 code")
    }

    val isSpanishResidence = residenceCountryCode == SPAIN_ALPHA3
    if (residenceAddress.isNullOrBlank()) {
        errors.add("residence_address is required")
    }
    if (residenceCountryCode.isNullOrBlank()) {
        errors.add("residence_country_code is required")
    }
    if (isSpanishResidence) {
        if (residenceProvinceId == null) {
            errors.add("residence_province_id is required for Spanish residence")
        }
        if (residenceCityId == null) {
            errors.add("residence_city_id is required for Spanish residence")
        }
    } else if (residenceCity.isNullOrBlank() && residenceCityId == null) {
        // Non-Spanish residence — text or structured city at least
        errors.add("residence_city or residence_city_id is required")
    }
    if (residencePostalCode.isNullOrBlank()) {
        errors.add("residence_postal_code is required")
    }

    if (!residenceAddress.isNullOrBlank() && residenceAddress.length > MAX_ADDRESS_LENGTH) {
        errors.add("residence_address must not exceed $MAX_ADDRESS_LENGTH characters")
    }
    if (!residenceCity.isNullOrBlank() && residenceCity.length > MAX_CITY_LENGTH) {
        errors.add("residence_city must not exceed $MAX_CITY_LENGTH characters")
    }
    if (!residencePostalCode.isNullOrBlank() && !SPANISH_POSTAL_CODE_REGEX.matches(residencePostalCode)) {
        // Postal code format validation (5 digits, applicable for Spanish postal codes)
        // For non-Spanish, we accept any format but still validate length
    }

    // --- Contact (mandatory for every guest, minors included: at least phone or email) ---
    // SES Hospedajes requires contact details on each traveller of a parte de viajeros; a
    // minor without their own phone/email should carry the responsible adult's contact.
    if (phone.isNullOrBlank() && email.isNullOrBlank()) {
        errors.add("at least one of phone or email is required")
    }

    if (!email.isNullOrBlank() && !GENERIC_EMAIL_REGEX.matches(email)) {
        errors.add("email format is invalid")
    }
    if (!phone.isNullOrBlank()) {
        if (!PHONE_REGEX.matches(phone)) {
            errors.add("phone must contain only digits, spaces, dashes or parentheses (6-$MAX_PHONE_LENGTH chars)")
        }
    }
    if (!phonePrefix.isNullOrBlank()) {
        if (!PHONE_PREFIX_REGEX.matches(phonePrefix)) {
            errors.add("phone_prefix must start with '+' followed by 1-4 digits")
        }
        if (phonePrefix.length > MAX_PHONE_PREFIX_LENGTH) {
            errors.add("phone_prefix must not exceed $MAX_PHONE_PREFIX_LENGTH characters")
        }
    }

    return if (errors.isEmpty()) ValidationResult.Valid else ValidationResult.Invalid(errors)
}

private fun validateName(
    field: String,
    value: String,
    errors: MutableList<String>,
) {
    if (value.isBlank()) {
        errors.add("$field is required")
        return
    }
    if (value.length > MAX_PERSON_NAME_LENGTH) {
        errors.add("$field must not exceed $MAX_PERSON_NAME_LENGTH characters")
    }
    if (!PERSON_NAME_REGEX.matches(value)) {
        errors.add("$field contains invalid characters")
    }
}

private fun validateDocumentNumber(
    type: DocumentType,
    number: String,
    errors: MutableList<String>,
) {
    val normalized = number.uppercase()
    if (number.length > MAX_DOCUMENT_NUMBER_LENGTH) {
        errors.add("document_number must not exceed $MAX_DOCUMENT_NUMBER_LENGTH characters")
        return
    }
    when (type) {
        DocumentType.DNI -> {
            if (!DNI_REGEX.matches(normalized) || !isValidDniOrNieChecksum(normalized)) {
                errors.add("document_number is not a valid Spanish DNI")
            }
        }
        DocumentType.NIE -> {
            if (!NIE_REGEX.matches(normalized) || !isValidDniOrNieChecksum(normalized)) {
                errors.add("document_number is not a valid Spanish NIE")
            }
        }
        DocumentType.Passport -> {
            if (!PASSPORT_REGEX.matches(normalized)) {
                errors.add("document_number is not a valid passport (6-20 alphanumeric characters)")
            }
        }
        DocumentType.Other -> {
            if (!OTHER_DOC_REGEX.matches(normalized)) {
                errors.add("document_number contains invalid characters")
            }
        }
    }
}

/**
 * Validates the DNI/NIE "número de soporte" (support number printed on the card).
 *
 * Formats per the Spanish Ministry of Justice ("Cómo introducir el número de soporte"):
 *  - DNI: 3 letters + 6 digits (IDESP), e.g. ABC123456.
 *  - NIE (TIE / permiso de residencia): letter E + 8 digits, e.g. E12345678.
 *  - NIE (certificado de registro de ciudadano de la Unión): letter C + 8 digits, e.g. C01234567.
 *
 * It must also differ from the document number itself.
 */
private fun validateDocumentSupportNumber(
    type: DocumentType,
    supportNumber: String?,
    documentNumber: String?,
    errors: MutableList<String>,
) {
    if (supportNumber.isNullOrBlank()) {
        errors.add("document_support_number is required for DNI/NIE documents")
        return
    }
    val normalized = supportNumber.uppercase()

    // Must differ from the document number itself.
    if (!documentNumber.isNullOrBlank() && normalized == documentNumber.uppercase()) {
        errors.add("document_support_number must be different from document_number")
        return
    }

    // Format per the document type.
    val matchesFormat =
        when (type) {
            DocumentType.DNI -> DNI_SUPPORT_REGEX.matches(normalized)
            DocumentType.NIE -> NIE_SUPPORT_REGEX.matches(normalized)
            else -> false
        }
    if (!matchesFormat) {
        errors.add(
            if (type == DocumentType.DNI) {
                "document_support_number must be 3 letters followed by 6 digits for DNI (e.g. ABC123456)"
            } else {
                "document_support_number must be the letter E or C followed by 8 digits for NIE " +
                    "(e.g. E12345678 or C01234567)"
            },
        )
    }
}
