package eu.kasify.routes

import eu.kasify.TEST_ACCOUNT_1_USER_1
import eu.kasify.TEST_ACCOUNT_2_USER_3
import eu.kasify.TEST_ACCOUNT_ID_1
import eu.kasify.TEST_ACCOUNT_ID_2
import eu.kasify.extensions.TEST_ENCRYPTION_KEY
import eu.kasify.extensions.configureTestModules
import eu.kasify.extensions.createClient
import eu.kasify.security.EncryptionService
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.uuid.Uuid

class IntegrationsRouteTest : BaseRouteDBTests() {
    // Account Integrations table IDs
    private val nukiAccountIntegration1Id = Uuid.Companion.parse("70000000-0000-0000-0000-000000000001")
    private val unifiAccountIntegration1Id = Uuid.Companion.parse("74000000-0000-0000-0000-000000000001")
    private val nukiAccountIntegration2Id = Uuid.Companion.parse("76000000-0000-0000-0000-000000000001")

    // Vendor-specific table IDs (these are returned in responses)
    private val nukiIntegration1Id = Uuid.Companion.parse("71000000-0000-0000-0000-000000000001")
    private val unifiIntegration1Id = Uuid.Companion.parse("75000000-0000-0000-0000-000000000001")
    private val nukiIntegration2Id = Uuid.Companion.parse("77000000-0000-0000-0000-000000000001")

    override fun setupData() {
        // Create encryption service with test key to properly encrypt passwords
        val encryptionService = EncryptionService(key = TEST_ENCRYPTION_KEY)
        val encryptedNukiToken = encryptionService.encrypt("test_nuki_token")
        val encryptedUnifiPassword = encryptionService.encrypt("test_unifi_password")
        val encryptedNukiToken2 = encryptionService.encrypt("test_nuki_token_acc2")

        // Account 1: Has Nuki and Unifi integrations

        // Insert Nuki integration for account 1
        testDBConnection.executeQuery(
            "INSERT INTO public.account_integrations (id, account_id, vendor, created_at, updated_at) " +
                "VALUES('$nukiAccountIntegration1Id', '${TEST_ACCOUNT_ID_1}', 'nuki', now(), now())",
        )
        testDBConnection.executeQuery(
            "INSERT INTO public.account_nuki_integrations (id, account_integration_id, nuki_token, created_at, updated_at) " +
                "VALUES('$nukiIntegration1Id', '$nukiAccountIntegration1Id', '$encryptedNukiToken', now(), now())",
        )

        // Insert Unifi integration for account 1
        testDBConnection.executeQuery(
            "INSERT INTO public.account_integrations (id, account_id, vendor, created_at, updated_at) " +
                "VALUES('$unifiAccountIntegration1Id', '${TEST_ACCOUNT_ID_1}', 'unifi', now(), now())",
        )
        testDBConnection.executeQuery(
            "INSERT INTO public.account_unifi_integrations (id, account_integration_id, unifi_username, unifi_password, unifi_console_url, created_at, updated_at) " +
                "VALUES('$unifiIntegration1Id', '$unifiAccountIntegration1Id', 'unifi_user', '$encryptedUnifiPassword', 'https://unifi.test', now(), now())",
        )

        // Account 2: Has only Nuki integration
        testDBConnection.executeQuery(
            "INSERT INTO public.account_integrations (id, account_id, vendor, created_at, updated_at) " +
                "VALUES('$nukiAccountIntegration2Id', '${TEST_ACCOUNT_ID_2}', 'nuki', now(), now())",
        )
        testDBConnection.executeQuery(
            "INSERT INTO public.account_nuki_integrations (id, account_integration_id, nuki_token, created_at, updated_at) " +
                "VALUES('$nukiIntegration2Id', '$nukiAccountIntegration2Id', '$encryptedNukiToken2', now(), now())",
        )
    }

    private fun findByType(
        responseText: String,
        type: String,
    ): List<kotlinx.serialization.json.JsonObject> =
        Json
            .parseToJsonElement(responseText)
            .jsonObject["integrations"]!!
            .jsonArray
            .map { it.jsonObject }
            .filter { it["type"]?.jsonPrimitive?.content == type }

    @Test
    fun `should return all configured integrations`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val response =
                client.get("/v1/integrations") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                }

            assertEquals(HttpStatusCode.Companion.OK, response.status)
            val responseText = response.bodyAsText()

            // Parse JSON and verify integrations array
            val json = Json.parseToJsonElement(responseText).jsonObject
            val integrations = json["integrations"]!!.jsonArray
            assertEquals(2, integrations.size)

            // Verify each type is present with expected data
            val nuki = findByType(responseText, "nuki")
            assertEquals(1, nuki.size)
            assertContains(responseText, nukiIntegration1Id.toString())

            val unifi = findByType(responseText, "unifi")
            assertEquals(1, unifi.size)
            assertContains(responseText, unifiIntegration1Id.toString())
            assertContains(responseText, "unifi_user")
            assertContains(responseText, "https://unifi.test")

            // Verify passwords are NOT in response
            assertFalse(responseText.contains("encrypted_nuki_token"))
            assertFalse(responseText.contains("encrypted_unifi_pass"))
        }

    @Test
    fun `should return only configured integrations`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val response =
                client.get("/v1/integrations") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_2_USER_3.token}")
                }

            assertEquals(HttpStatusCode.Companion.OK, response.status)
            val responseText = response.bodyAsText()

            // Parse JSON and verify only 1 integration
            val json = Json.parseToJsonElement(responseText).jsonObject
            val integrations = json["integrations"]!!.jsonArray
            assertEquals(1, integrations.size)

            // Verify it's the Nuki integration
            val nuki = findByType(responseText, "nuki")
            assertEquals(1, nuki.size)
            assertContains(responseText, nukiIntegration2Id.toString())

            // Verify no other types present
            assertEquals(0, findByType(responseText, "unifi").size)
        }

    @Test
    fun `should return unauthorized when getting integrations without token`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val response = client.get("/v1/integrations")

            assertEquals(HttpStatusCode.Companion.Unauthorized, response.status)
        }

    @Test
    fun `should return unauthorized when getting integrations with invalid token`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val response =
                client.get("/v1/integrations") {
                    header(HttpHeaders.Authorization, "Bearer invalid-token")
                }

            assertEquals(HttpStatusCode.Companion.Unauthorized, response.status)
        }

    @Test
    fun `should verify response structure is correct`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val response =
                client.get("/v1/integrations") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                }

            assertEquals(HttpStatusCode.Companion.OK, response.status)
            val responseText = response.bodyAsText()

            // Verify top-level has integrations array
            val json = Json.parseToJsonElement(responseText).jsonObject
            assertNotNull(json["integrations"]) { "Response should have 'integrations' field" }
            val integrations = json["integrations"]!!.jsonArray

            // Each item should have a type discriminator and id
            for (item in integrations) {
                val obj = item.jsonObject
                assertNotNull(obj["type"]) { "Each integration should have 'type' field" }
                assertNotNull(obj["id"]) { "Each integration should have 'id' field" }
            }

            // Verify Unifi has correct fields
            val unifi = findByType(responseText, "unifi").first()
            assertNotNull(unifi["unifi_username"]) { "Unifi should have 'unifi_username' field" }
            assertNotNull(unifi["unifi_console_url"]) { "Unifi should have 'unifi_console_url' field" }
            assertFalse(unifi.containsKey("unifi_password"), "Unifi password should not be in response")
        }

    @Test
    fun `passwords should never be returned in integrations response`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val response =
                client.get("/v1/integrations") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                }

            assertEquals(HttpStatusCode.Companion.OK, response.status)
            val responseText = response.bodyAsText()

            // Verify none of the encrypted passwords appear in response
            assertFalse(responseText.contains("encrypted_nuki_token"), "Nuki token should not be in response")
            assertFalse(responseText.contains("encrypted_unifi_pass"), "Unifi password should not be in response")

            // Verify password field names don't appear
            assertFalse(responseText.contains("\"nuki_token\""), "nuki_token field should not be in response")
            assertFalse(responseText.contains("\"unifi_password\""), "unifi_password field should not be in response")
        }
}
