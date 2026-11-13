package eu.kasify.db

import eu.kasify.TEST_ACCOUNT_ID_1
import eu.kasify.extensions.TEST_ENCRYPTION_KEY
import eu.kasify.routes.BaseRouteDBTests
import eu.kasify.security.EncryptionService
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.uuid.Uuid

class SesHospedajesIntegrationsRepositoryTest : BaseRouteDBTests() {
    private val repository =
        IntegrationsRepository(PropertiesRepository(), EncryptionService(key = TEST_ENCRYPTION_KEY))

    @Test
    fun `create defaults rh_direct_bookings_enabled to true when omitted`() =
        runBlocking {
            val created =
                repository.createSesHospedajesIntegration(
                    accountId = TEST_ACCOUNT_ID_1,
                    arrendadorCode = "0000145750",
                    sesUsername = "ses-user",
                    sesPassword = "ses-pass",
                )

            assertEquals(true, created.rhDirectBookingsEnabled)
        }

    @Test
    fun `create honors explicit rh_direct_bookings_enabled false`() =
        runBlocking {
            val created =
                repository.createSesHospedajesIntegration(
                    accountId = TEST_ACCOUNT_ID_1,
                    arrendadorCode = "0000145750",
                    sesUsername = "ses-user",
                    sesPassword = "ses-pass",
                    rhDirectBookingsEnabled = false,
                )

            assertEquals(false, created.rhDirectBookingsEnabled)

            val fetched = repository.getSesHospedajesIntegrationById(TEST_ACCOUNT_ID_1, created.id!!)
            assertNotNull(fetched)
            assertEquals(false, fetched.rhDirectBookingsEnabled)
        }

    @Test
    fun `update leaves rh_direct_bookings_enabled untouched when null`() =
        runBlocking {
            val created =
                repository.createSesHospedajesIntegration(
                    accountId = TEST_ACCOUNT_ID_1,
                    arrendadorCode = "0000145750",
                    sesUsername = "ses-user",
                    sesPassword = "ses-pass",
                    rhDirectBookingsEnabled = false,
                )

            // Omitting the flag (null) must not reset the stored value back to the default.
            repository.updateSesHospedajesIntegration(
                accountId = TEST_ACCOUNT_ID_1,
                integrationId = created.id!!,
                arrendadorCode = "0000145750",
                sesUsername = "ses-user-2",
                sesPassword = "ses-pass-2",
                rhDirectBookingsEnabled = null,
            )

            val afterNullUpdate = repository.getSesHospedajesIntegrationById(TEST_ACCOUNT_ID_1, created.id!!)
            assertNotNull(afterNullUpdate)
            assertEquals("ses-user-2", afterNullUpdate.sesUsername)
            assertEquals(false, afterNullUpdate.rhDirectBookingsEnabled)

            // Explicitly toggling it through does persist.
            repository.updateSesHospedajesIntegration(
                accountId = TEST_ACCOUNT_ID_1,
                integrationId = created.id!!,
                arrendadorCode = "0000145750",
                sesUsername = "ses-user-2",
                sesPassword = "ses-pass-2",
                rhDirectBookingsEnabled = true,
            )

            val afterTrueUpdate = repository.getSesHospedajesIntegrationById(TEST_ACCOUNT_ID_1, created.id!!)
            assertNotNull(afterTrueUpdate)
            assertEquals(true, afterTrueUpdate.rhDirectBookingsEnabled)
        }

    @Test
    fun `updateSettings toggles rh_direct_bookings_enabled without touching credentials`() =
        runBlocking {
            val created =
                repository.createSesHospedajesIntegration(
                    accountId = TEST_ACCOUNT_ID_1,
                    arrendadorCode = "0000145750",
                    sesUsername = "ses-user",
                    sesPassword = "ses-pass",
                )

            val ok =
                repository.updateSesHospedajesSettings(
                    accountId = TEST_ACCOUNT_ID_1,
                    integrationId = created.id!!,
                    rhDirectBookingsEnabled = false,
                )
            assertEquals(true, ok)

            val fetched = repository.getSesHospedajesIntegrationById(TEST_ACCOUNT_ID_1, created.id!!)
            assertNotNull(fetched)
            assertEquals(false, fetched.rhDirectBookingsEnabled)
            // Credentials are untouched by a settings-only update.
            assertEquals("ses-user", fetched.sesUsername)
            assertEquals("ses-pass", fetched.sesPassword)
        }

    @Test
    fun `updateSettings returns false for an unknown integration`() =
        runBlocking {
            val unknownId = Uuid.parse("00000000-0000-0000-0000-0000000000ff")
            val ok =
                repository.updateSesHospedajesSettings(
                    accountId = TEST_ACCOUNT_ID_1,
                    integrationId = unknownId,
                    rhDirectBookingsEnabled = false,
                )
            assertEquals(false, ok)
        }

    // --- credential-error flag (markSesCredentialsError / clearSesCredentialsError) ---

    private fun createIntegration() =
        runBlocking {
            repository.createSesHospedajesIntegration(
                accountId = TEST_ACCOUNT_ID_1,
                arrendadorCode = "0000145750",
                sesUsername = "ses-user",
                sesPassword = "ses-pass",
            )
        }

    @Test
    fun `markSesCredentialsError sets the flag only on the first call`() =
        runBlocking {
            val created = createIntegration()

            // First writer wins and reports the transition (drives the one-time owner email).
            assertEquals(true, repository.markSesCredentialsError(created.id!!, 10107, "Usuario incorrecto"))
            // A later failure (same or different código) does not re-transition.
            assertEquals(false, repository.markSesCredentialsError(created.id!!, 10103, "Otro error"))

            val fetched = repository.getSesHospedajesIntegrationById(TEST_ACCOUNT_ID_1, created.id!!)
            assertNotNull(fetched)
            assertNotNull(fetched.credentialsErrorAt)
            assertEquals(10107, fetched.credentialsErrorCode)
            assertEquals("Usuario incorrecto", fetched.credentialsErrorMessage)
        }

    @Test
    fun `clearSesCredentialsError clears the flag and reports whether one was set`() =
        runBlocking {
            val created = createIntegration()
            repository.markSesCredentialsError(created.id!!, 10107, "Usuario incorrecto")

            assertEquals(true, repository.clearSesCredentialsError(created.id!!))
            // Already clear: nothing to do.
            assertEquals(false, repository.clearSesCredentialsError(created.id!!))

            val fetched = repository.getSesHospedajesIntegrationById(TEST_ACCOUNT_ID_1, created.id!!)
            assertNotNull(fetched)
            assertEquals(null, fetched.credentialsErrorCode)
            assertEquals(null, fetched.credentialsErrorMessage)
            assertEquals(null, fetched.credentialsErrorAt)
        }

    @Test
    fun `updating credentials clears the credential-error flag`() =
        runBlocking {
            val created = createIntegration()
            repository.markSesCredentialsError(created.id!!, 10107, "Usuario incorrecto")

            // The handler SES-verifies credentials before persisting, so a successful update means
            // fresh valid credentials — the pause must lift without any extra step.
            val ok =
                repository.updateSesHospedajesIntegration(
                    accountId = TEST_ACCOUNT_ID_1,
                    integrationId = created.id!!,
                    arrendadorCode = "0000145750",
                    sesUsername = "ses-user",
                    sesPassword = "new-pass",
                )
            assertEquals(true, ok)

            val fetched = repository.getSesHospedajesIntegrationById(TEST_ACCOUNT_ID_1, created.id!!)
            assertNotNull(fetched)
            assertEquals(null, fetched.credentialsErrorCode)
            assertEquals(null, fetched.credentialsErrorMessage)
            assertEquals(null, fetched.credentialsErrorAt)
        }
}
