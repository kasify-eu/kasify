package eu.kasify.db

import eu.kasify.TEST_ACCOUNT_1_PROPERTY_1
import eu.kasify.model.api.requests.PropertyPatch
import eu.kasify.routes.BaseRouteDBTests
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** SES property-config error flag lifecycle (see SesPropertyConfigGuard). */
class PropertiesRepositorySesConfigErrorTest : BaseRouteDBTests() {
    private val repository = PropertiesRepository()
    private val propertyId = TEST_ACCOUNT_1_PROPERTY_1.id

    @Test
    fun `markSesConfigError sets the flag only on the first call`() =
        runBlocking {
            assertTrue(repository.markSesConfigError(propertyId, "No existe un establecimiento para ese código"))
            // Second failure of the same outage: no transition, so the caller sends no second email.
            assertFalse(repository.markSesConfigError(propertyId, "No existe un establecimiento para ese código"))

            val property = assertNotNull(repository.getProperty(propertyId))
            assertNotNull(property.sesConfigErrorAt)
            assertEquals("No existe un establecimiento para ese código", property.sesConfigErrorMessage)
        }

    @Test
    fun `clearSesConfigError clears the flag and reports whether one was set`() =
        runBlocking {
            assertFalse(repository.clearSesConfigError(propertyId))

            repository.markSesConfigError(propertyId, "No existe un establecimiento")
            assertTrue(repository.clearSesConfigError(propertyId))
            assertFalse(repository.clearSesConfigError(propertyId))

            val property = assertNotNull(repository.getProperty(propertyId))
            assertNull(property.sesConfigErrorMessage)
            assertNull(property.sesConfigErrorAt)
        }

    @Test
    fun `re-saving the establishment code clears the flag (optimistic, like a credential re-save)`() =
        runBlocking {
            repository.markSesConfigError(propertyId, "No existe un establecimiento")

            val patched =
                assertNotNull(
                    repository.patchProperty(propertyId, PropertyPatch(sesEstablishmentCode = "EST-FIXED1")),
                )

            assertEquals("EST-FIXED1", patched.sesEstablishmentCode)
            assertNull(patched.sesConfigErrorMessage)
            assertNull(patched.sesConfigErrorAt)
        }

    @Test
    fun `patching unrelated fields leaves the flag untouched`() =
        runBlocking {
            repository.markSesConfigError(propertyId, "No existe un establecimiento")

            val patched = assertNotNull(repository.patchProperty(propertyId, PropertyPatch(name = "Renamed")))

            assertEquals("No existe un establecimiento", patched.sesConfigErrorMessage)
        }
}
