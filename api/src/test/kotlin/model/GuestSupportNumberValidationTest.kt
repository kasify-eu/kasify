package model

import eu.kasify.db.model.DocumentType
import eu.kasify.db.model.Gender
import eu.kasify.model.api.requests.GuestRegistrationRequest
import eu.kasify.model.api.requests.validate
import io.ktor.server.plugins.requestvalidation.ValidationResult
import org.junit.Test
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Focused tests for the DNI/NIE "número de soporte" validation added to
 * [GuestRegistrationRequest.validate]. Formats follow the Spanish Ministry of
 * Justice guidance ("Cómo introducir el número de soporte").
 */
class GuestSupportNumberValidationTest {
    private fun request(
        documentType: DocumentType = DocumentType.DNI,
        documentNumber: String = "12345678Z",
        documentSupportNumber: String? = "ABC123456",
    ) = GuestRegistrationRequest(
        firstName = "Juan",
        lastName = "Garcia",
        secondLastName = "Lopez",
        nationalityCountryCode = "ESP",
        dateOfBirth = LocalDate.of(1990, 1, 1),
        sex = Gender.Male,
        documentType = documentType,
        documentNumber = documentNumber,
        documentSupportNumber = documentSupportNumber,
        residenceAddress = "Calle Test 1",
        residenceCountryCode = "USA",
        residenceCity = "New York",
        residencePostalCode = "10001",
        phone = "612345678",
        phonePrefix = "+34",
        email = "juan@test.com",
    )

    private fun reasons(result: ValidationResult): List<String> =
        when (result) {
            is ValidationResult.Invalid -> result.reasons
            ValidationResult.Valid -> emptyList()
        }

    private fun supportError(result: ValidationResult): String? = reasons(result).firstOrNull { it.startsWith("document_support_number") }

    @Test
    fun `accepts a well-formed DNI support number`() {
        assertEquals(ValidationResult.Valid, request(documentSupportNumber = "ABC123456").validate())
    }

    @Test
    fun `accepts a lowercase DNI support number (normalized)`() {
        assertEquals(ValidationResult.Valid, request(documentSupportNumber = "abc123456").validate())
    }

    @Test
    fun `accepts NIE support numbers with the E and C prefixes`() {
        val tie =
            request(
                documentType = DocumentType.NIE,
                documentNumber = "X1234567L",
                documentSupportNumber = "E12345678",
            ).validate()
        val certificate =
            request(
                documentType = DocumentType.NIE,
                documentNumber = "X1234567L",
                documentSupportNumber = "C01234567",
            ).validate()
        assertEquals(ValidationResult.Valid, tie)
        assertEquals(ValidationResult.Valid, certificate)
    }

    @Test
    fun `rejects a DNI support number in NIE format`() {
        val result = request(documentSupportNumber = "E12345678").validate()
        assertTrue(supportError(result)?.contains("3 letters followed by 6 digits") == true)
    }

    @Test
    fun `rejects a NIE support number in DNI format`() {
        val result =
            request(
                documentType = DocumentType.NIE,
                documentNumber = "X1234567L",
                documentSupportNumber = "ABC123456",
            ).validate()
        assertTrue(supportError(result)?.contains("letter E or C followed by 8 digits") == true)
    }

    @Test
    fun `rejects a support number of the wrong length`() {
        assertTrue(supportError(request(documentSupportNumber = "ABC12345").validate()) != null)
        assertTrue(
            supportError(
                request(
                    documentType = DocumentType.NIE,
                    documentNumber = "X1234567L",
                    documentSupportNumber = "E123",
                ).validate(),
            ) != null,
        )
    }

    @Test
    fun `rejects a support number equal to the document number`() {
        // Passport allows arbitrary alphanumerics, so a DNI-shaped value can legitimately be a
        // document number; here we force the support number to equal it to exercise the guard.
        val result =
            request(
                documentType = DocumentType.NIE,
                documentNumber = "E12345678",
                documentSupportNumber = "E12345678",
            ).validate()
        assertTrue(supportError(result)?.contains("must be different from document_number") == true)
    }

    @Test
    fun `requires a support number for DNI and NIE`() {
        assertTrue(supportError(request(documentSupportNumber = null).validate()) != null)
    }
}
