package eu.kasify.services

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SesFailureClassifierTest {
    @Test
    fun `credential-family codigos classify as IntegrationCredentials`() {
        for (codigo in listOf(10103, 10107, 10119, 10120)) {
            assertEquals(
                SesFailureCause.IntegrationCredentials(codigo),
                SesFailureClassifier.classifyRejection(codigo, "whatever"),
            )
        }
    }

    @Test
    fun `unknown-establishment text classifies as PropertyConfig regardless of case`() {
        val sesText =
            "Error de validación en la comunicación [1]: [No existe un establecimiento para ese " +
                "código. Solo puede enviar comunicaciones a sus establecimientos.]"
        assertTrue(SesFailureClassifier.isPropertyConfigError(sesText))
        assertTrue(SesFailureClassifier.isPropertyConfigError(sesText.uppercase()))
        assertEquals(SesFailureCause.PropertyConfig, SesFailureClassifier.classifyRejection(10121, sesText))
    }

    @Test
    fun `anything unrecognized degrades to BookingData (conservative fallback)`() {
        assertEquals(
            SesFailureCause.BookingData,
            SesFailureClassifier.classifyRejection(10121, "Datos de contacto obligatorios, teléfono o correo."),
        )
        assertEquals(SesFailureCause.BookingData, SesFailureClassifier.classifyRejection(10999, null))
        assertFalse(SesFailureClassifier.isPropertyConfigError(null))
        assertFalse(SesFailureClassifier.isPropertyConfigError("  "))
        // "establecimiento" alone must NOT match — only the full stable phrase does.
        assertFalse(SesFailureClassifier.isPropertyConfigError("El establecimiento no admite RH"))
    }
}
