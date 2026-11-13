package eu.kasify.routes

import eu.kasify.TEST_ACCOUNT_1_USER_1
import eu.kasify.TEST_ACCOUNT_2_USER_3
import eu.kasify.TEST_ACCOUNT_ID_1
import eu.kasify.extensions.configureTestModules
import eu.kasify.extensions.createClient
import eu.kasify.model.api.requests.NukiIntegrationRequest
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import org.junit.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

class NukiIntegrationsRouteTest : BaseRouteDBTests() {
    private val testIntegrationId = Uuid.Companion.parse("60000000-0000-0000-0000-000000000001")
    private val nonExistentId = Uuid.Companion.parse("99999999-9999-9999-9999-999999999999")

    override fun setupData() {
        val testAccountIntegrationId = Uuid.Companion.parse("61000000-0000-0000-0000-000000000001")

        // First insert into account_integrations
        val sql1 =
            "INSERT INTO public.account_integrations " +
                "(id, account_id, vendor, created_at, updated_at) " +
                "VALUES('$testAccountIntegrationId', '${TEST_ACCOUNT_ID_1}', 'nuki', '2025-07-11 17:52:49.558', '2025-07-11 17:52:49.558')"
        testDBConnection.executeQuery(sql1)

        // Then insert into account_nuki_integrations
        val sql2 =
            "INSERT INTO public.account_nuki_integrations " +
                "(id, account_integration_id, nuki_token, created_at, updated_at) " +
                "VALUES('$testIntegrationId', '$testAccountIntegrationId', 'i4JCjCWZzjeOD+0pZ94XDqrp9b2ZEYzgQ6wYm0+hFehAAvWNymkQuTLI7WY=', '2025-07-11 17:52:49.558', '2025-07-11 17:52:49.558')"
        testDBConnection.executeQuery(sql2)
    }

    @Test
    fun `should return nuki integrations list when user has valid integration`() =
        testApplication {
            application { configureTestModules() }

            val client = createClient()

            val response =
                client.get("/v1/integrations/nuki") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                }

            assertEquals(HttpStatusCode.Companion.OK, response.status)
            val responseText = response.bodyAsText()
            assertContains(responseText, testIntegrationId.toString())
            // Token should not be included in response
            assert(!responseText.contains("test-token-12345"))
        }

    @Test
    fun `should return empty list when user has no nuki integrations`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            // User from different account should get empty list
            val response =
                client.get("/v1/integrations/nuki") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_2_USER_3.token}")
                }

            assertEquals(HttpStatusCode.Companion.OK, response.status)
            assertEquals("[]", response.bodyAsText())
        }

    @Test
    fun `should return unauthorized when getting nuki integration without token`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val response = client.get("/v1/integrations/nuki")

            assertEquals(HttpStatusCode.Companion.Unauthorized, response.status)
        }

    @Test
    fun `should return unauthorized when getting nuki integration with invalid token`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val response =
                client.get("/v1/integrations/nuki") {
                    header(HttpHeaders.Authorization, "Bearer invalid-token")
                }

            assertEquals(HttpStatusCode.Companion.Unauthorized, response.status)
        }

    @Test
    fun `should create new nuki integration successfully`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val request =
                NukiIntegrationRequest(
                    nukiToken = "new-token-67890",
                )

            // Use account 2 user that doesn't have an integration
            val response =
                client.post("/v1/integrations/nuki") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_2_USER_3.token}")
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }

            assertEquals(HttpStatusCode.Companion.Created, response.status)
            val responseText = response.bodyAsText()
            // Token should not be included in response
            assert(!responseText.contains("new-token-67890"))
        }

    @Test
    fun `should allow creating multiple nuki integrations for same account`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val request =
                NukiIntegrationRequest(
                    nukiToken = "another-token",
                )

            // Account 1 already has an integration; creating another should succeed
            val response =
                client.post("/v1/integrations/nuki") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }

            assertEquals(HttpStatusCode.Companion.Created, response.status)
        }

    @Test
    fun `should return unauthorized when creating nuki integration without token`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val request =
                NukiIntegrationRequest(
                    nukiToken = "new-token",
                )

            val response =
                client.post("/v1/integrations/nuki") {
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }

            assertEquals(HttpStatusCode.Companion.Unauthorized, response.status)
        }

    @Test
    fun `should return unauthorized when creating nuki integration with invalid token`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val request =
                NukiIntegrationRequest(
                    nukiToken = "new-token",
                )

            val response =
                client.post("/v1/integrations/nuki") {
                    header(HttpHeaders.Authorization, "Bearer invalid-token")
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }

            assertEquals(HttpStatusCode.Companion.Unauthorized, response.status)
        }

    @Test
    fun `should return bad request when creating nuki integration with invalid json`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val response =
                client.post("/v1/integrations/nuki") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_2_USER_3.token}")
                    contentType(ContentType.Application.Json)
                    setBody("{invalid json}")
                }

            assertEquals(HttpStatusCode.Companion.BadRequest, response.status)
        }

    @Test
    fun `should return bad request when creating nuki integration with missing fields`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val response =
                client.post("/v1/integrations/nuki") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_2_USER_3.token}")
                    contentType(ContentType.Application.Json)
                    setBody("{}")
                }

            assertEquals(HttpStatusCode.Companion.BadRequest, response.status)
        }

    @Test
    fun `should update nuki integration successfully with PUT`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val request =
                NukiIntegrationRequest(
                    nukiToken = "updated-token",
                )

            val response =
                client.put("/v1/integrations/nuki/$testIntegrationId") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }

            assertEquals(HttpStatusCode.Companion.OK, response.status)
            val responseText = response.bodyAsText()
            // Token should not be included in response
            assert(!responseText.contains("updated-token"))
        }

    @Test
    fun `should return not found when updating non-existent nuki integration`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val request =
                NukiIntegrationRequest(
                    nukiToken = "updated-token",
                )

            val response =
                client.put("/v1/integrations/nuki/$nonExistentId") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_2_USER_3.token}")
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }

            assertEquals(HttpStatusCode.Companion.NotFound, response.status)
        }

    @Test
    fun `should return unauthorized when updating nuki integration without token`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val request =
                NukiIntegrationRequest(
                    nukiToken = "updated-token",
                )

            val response =
                client.put("/v1/integrations/nuki/$testIntegrationId") {
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }

            assertEquals(HttpStatusCode.Companion.Unauthorized, response.status)
        }

    @Test
    fun `should delete nuki integration successfully`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val response =
                client.delete("/v1/integrations/nuki/$testIntegrationId") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                }

            assertEquals(HttpStatusCode.Companion.NoContent, response.status)

            // Verify integration was deleted — list should be empty
            val getResponse =
                client.get("/v1/integrations/nuki") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                }

            assertEquals(HttpStatusCode.Companion.OK, getResponse.status)
            assertEquals("[]", getResponse.bodyAsText())
        }

    @Test
    fun `should return not found when deleting non-existent nuki integration`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val response =
                client.delete("/v1/integrations/nuki/$nonExistentId") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_2_USER_3.token}")
                }

            assertEquals(HttpStatusCode.Companion.NotFound, response.status)
        }

    @Test
    fun `should return unauthorized when deleting nuki integration without token`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val response = client.delete("/v1/integrations/nuki/$testIntegrationId")

            assertEquals(HttpStatusCode.Companion.Unauthorized, response.status)
        }

    @Test
    fun `should return unauthorized when deleting nuki integration with invalid token`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val response =
                client.delete("/v1/integrations/nuki/$testIntegrationId") {
                    header(HttpHeaders.Authorization, "Bearer invalid-token")
                }

            assertEquals(HttpStatusCode.Companion.Unauthorized, response.status)
        }

    // ===== TOKEN SECURITY TESTS =====

    @Test
    fun `token should never be returned in GET response`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val response =
                client.get("/v1/integrations/nuki") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                }

            assertEquals(HttpStatusCode.Companion.OK, response.status)
            val responseText = response.bodyAsText()

            // Verify token fields are not present
            assert(!responseText.contains("test-token-12345")) { "Token 'test-token-12345' should not be in response: $responseText" }
            assert(!responseText.contains("nukiToken")) { "nukiToken field should not be in response: $responseText" }
            assert(!responseText.contains("token")) { "Token field should not be in response: $responseText" }
        }

    @Test
    fun `token should never be returned in POST create response`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val request =
                NukiIntegrationRequest(
                    nukiToken = "supersecrettoken123456789",
                )

            val response =
                client.post("/v1/integrations/nuki") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_2_USER_3.token}")
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }

            assertEquals(HttpStatusCode.Created, response.status)
            val responseText = response.bodyAsText()

            // Verify token is not present
            assert(!responseText.contains("supersecrettoken123456789")) { "Token should not be in response: $responseText" }
            assert(!responseText.contains("nukiToken")) { "nukiToken field should not be in response: $responseText" }
            assert(!responseText.contains("token")) { "Token field should not be in response: $responseText" }
        }

    @Test
    fun `token should never be returned in PUT response`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val request =
                NukiIntegrationRequest(
                    nukiToken = "putsupersecrettoken987654321",
                )

            val response =
                client.put("/v1/integrations/nuki/$testIntegrationId") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }

            assertEquals(HttpStatusCode.Companion.OK, response.status)
            val responseText = response.bodyAsText()

            // Verify token is not present
            assert(!responseText.contains("putsupersecrettoken987654321")) { "Token should not be in response: $responseText" }
            assert(!responseText.contains("nukiToken")) { "nukiToken field should not be in response: $responseText" }
            assert(!responseText.contains("token")) { "Token field should not be in response: $responseText" }
        }

    // ===== GET /integrations/nuki/{id}/smartlocks TESTS =====

    @Test
    fun `should return smartlocks list for valid integration`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val response =
                client.get("/v1/integrations/nuki/$testIntegrationId/smartlocks") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                }

            assertEquals(HttpStatusCode.Companion.OK, response.status)
            val responseText = response.bodyAsText()
            assertContains(responseText, "123456789")
        }

    @Test
    fun `should return not found when smartlocks integration does not exist`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val response =
                client.get("/v1/integrations/nuki/$nonExistentId/smartlocks") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                }

            assertEquals(HttpStatusCode.Companion.NotFound, response.status)
        }

    @Test
    fun `should return not found when smartlocks integration belongs to different account`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val response =
                client.get("/v1/integrations/nuki/$testIntegrationId/smartlocks") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_2_USER_3.token}")
                }

            assertEquals(HttpStatusCode.Companion.NotFound, response.status)
        }

    @Test
    fun `should return bad request for invalid smartlocks integration id format`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val response =
                client.get("/v1/integrations/nuki/not-a-uuid/smartlocks") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                }

            assertEquals(HttpStatusCode.Companion.BadRequest, response.status)
        }

    @Test
    fun `should return unauthorized when fetching smartlocks without token`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val response = client.get("/v1/integrations/nuki/$testIntegrationId/smartlocks")

            assertEquals(HttpStatusCode.Companion.Unauthorized, response.status)
        }

    @Test
    fun `should return unauthorized when fetching smartlocks with invalid token`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val response =
                client.get("/v1/integrations/nuki/$testIntegrationId/smartlocks") {
                    header(HttpHeaders.Authorization, "Bearer invalid-token")
                }

            assertEquals(HttpStatusCode.Companion.Unauthorized, response.status)
        }

    // ===== PUT /integrations/nuki/{id} ADDITIONAL TESTS =====

    @Test
    fun `should return unauthorized when updating nuki integration with invalid token`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val response =
                client.put("/v1/integrations/nuki/$testIntegrationId") {
                    header(HttpHeaders.Authorization, "Bearer invalid-token")
                    contentType(ContentType.Application.Json)
                    setBody(NukiIntegrationRequest(nukiToken = "updated-token"))
                }

            assertEquals(HttpStatusCode.Companion.Unauthorized, response.status)
        }

    @Test
    fun `should return bad request for invalid put integration id format`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val response =
                client.put("/v1/integrations/nuki/not-a-uuid") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                    contentType(ContentType.Application.Json)
                    setBody(NukiIntegrationRequest(nukiToken = "updated-token"))
                }

            assertEquals(HttpStatusCode.Companion.BadRequest, response.status)
        }

    @Test
    fun `should return not found when updating integration belonging to different account`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val response =
                client.put("/v1/integrations/nuki/$testIntegrationId") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_2_USER_3.token}")
                    contentType(ContentType.Application.Json)
                    setBody(NukiIntegrationRequest(nukiToken = "updated-token"))
                }

            assertEquals(HttpStatusCode.Companion.NotFound, response.status)
        }

    // ===== DELETE /integrations/nuki/{id} ADDITIONAL TESTS =====

    @Test
    fun `should return bad request for invalid delete integration id format`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val response =
                client.delete("/v1/integrations/nuki/not-a-uuid") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                }

            assertEquals(HttpStatusCode.Companion.BadRequest, response.status)
        }

    @Test
    fun `should return not found when deleting integration belonging to different account`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val response =
                client.delete("/v1/integrations/nuki/$testIntegrationId") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_2_USER_3.token}")
                }

            assertEquals(HttpStatusCode.Companion.NotFound, response.status)
        }

    @Test
    fun `token should not leak through JSON serialization`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            // Create integration with a very distinctive token
            val distinctiveToken = "VERY_DISTINCTIVE_TOKEN_THAT_SHOULD_NEVER_APPEAR_IN_RESPONSE"
            val request =
                NukiIntegrationRequest(
                    nukiToken = distinctiveToken,
                )

            val createResponse =
                client.post("/v1/integrations/nuki") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_2_USER_3.token}")
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }

            assertEquals(HttpStatusCode.Companion.Created, createResponse.status)
            val createResponseText = createResponse.bodyAsText()
            assert(!createResponseText.contains(distinctiveToken)) {
                "Distinctive token should not appear in create response: $createResponseText"
            }

            // Also check GET response
            val getResponse =
                client.get("/v1/integrations/nuki") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_2_USER_3.token}")
                }

            assertEquals(HttpStatusCode.Companion.OK, getResponse.status)
            val getResponseText = getResponse.bodyAsText()
            assert(!getResponseText.contains(distinctiveToken)) {
                "Distinctive token should not appear in get response: $getResponseText"
            }
        }
}
