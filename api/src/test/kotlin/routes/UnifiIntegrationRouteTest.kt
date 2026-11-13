package eu.kasify.routes

import com.sun.net.httpserver.HttpServer
import eu.kasify.TEST_ACCOUNT_1_USER_1
import eu.kasify.TEST_ACCOUNT_2_USER_3
import eu.kasify.TEST_ACCOUNT_ID_1
import eu.kasify.extensions.TEST_ENCRYPTION_KEY
import eu.kasify.extensions.configureTestModules
import eu.kasify.extensions.createClient
import eu.kasify.helpers.assertNoForgedLogLines
import eu.kasify.helpers.captureKasifyLogs
import eu.kasify.model.api.requests.UnifiIntegrationRequest
import eu.kasify.security.EncryptionService
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import org.junit.Test
import java.net.InetAddress
import java.net.InetSocketAddress
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class UnifiIntegrationRouteTest : BaseRouteDBTests() {
    private val testIntegrationId = Uuid.Companion.parse("62000000-0000-0000-0000-000000000001")
    private val testAccountIntegrationId = Uuid.Companion.parse("63000000-0000-0000-0000-000000000001")
    private val testConsoleUrl = "https://unifi.example.com"
    private val testUsername = "test-unifi-user"
    private val testPassword = "test_value_secret"

    override fun setupData() {
        val encryptionService = EncryptionService(key = TEST_ENCRYPTION_KEY)
        val encryptedPassword = encryptionService.encrypt(testPassword)

        val sql1 =
            "INSERT INTO public.account_integrations " +
                "(id, account_id, vendor, created_at, updated_at) " +
                "VALUES('$testAccountIntegrationId', '${TEST_ACCOUNT_ID_1}', 'unifi', now(), now())"
        testDBConnection.executeQuery(sql1)

        val sql2 =
            "INSERT INTO public.account_unifi_integrations " +
                "(id, account_integration_id, unifi_username, unifi_password, unifi_console_url, created_at, updated_at) " +
                "VALUES('$testIntegrationId', '$testAccountIntegrationId', '$testUsername', '$encryptedPassword', '$testConsoleUrl', now(), now())"
        testDBConnection.executeQuery(sql2)
    }

    // ============================================================
    // GET /integrations/unifi
    // ============================================================

    @Test
    fun `should return unifi integration when user has valid integration`() =
        testApplication {
            application { configureTestModules() }

            val client = createClient()

            val response =
                client.get("/v1/integrations/unifi") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                }

            assertEquals(HttpStatusCode.Companion.OK, response.status)
            val responseText = response.bodyAsText()
            assertContains(responseText, testIntegrationId.toString())
            assertContains(responseText, testUsername)
            assertContains(responseText, testConsoleUrl)
            assertFalse(responseText.contains(testPassword))
        }

    @Test
    fun `should return empty list when user has no unifi integration`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val response =
                client.get("/v1/integrations/unifi") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_2_USER_3.token}")
                }

            assertEquals(HttpStatusCode.Companion.OK, response.status)
            assertEquals("[]", response.bodyAsText())
        }

    @Test
    fun `should return unauthorized when getting unifi integration without token`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val response = client.get("/v1/integrations/unifi")

            assertEquals(HttpStatusCode.Companion.Unauthorized, response.status)
        }

    @Test
    fun `should return unauthorized when getting unifi integration with invalid token`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val response =
                client.get("/v1/integrations/unifi") {
                    header(HttpHeaders.Authorization, "Bearer invalid-token")
                }

            assertEquals(HttpStatusCode.Companion.Unauthorized, response.status)
        }

    // ============================================================
    // POST /integrations/unifi
    // ============================================================

    @Test
    fun `should return unauthorized when creating without token`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val request =
                UnifiIntegrationRequest(
                    unifiConsoleUrl = "https://new.unifi.com",
                    unifiUsername = "new-user",
                    unifiPassword = "new-secret",
                )

            val response =
                client.post("/v1/integrations/unifi") {
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }

            assertEquals(HttpStatusCode.Companion.Unauthorized, response.status)
        }

    @Test
    fun `should return unauthorized when creating with invalid token`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val request =
                UnifiIntegrationRequest(
                    unifiConsoleUrl = "https://new.unifi.com",
                    unifiUsername = "new-user",
                    unifiPassword = "new-secret",
                )

            val response =
                client.post("/v1/integrations/unifi") {
                    header(HttpHeaders.Authorization, "Bearer invalid-token")
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }

            assertEquals(HttpStatusCode.Companion.Unauthorized, response.status)
        }

    @Test
    fun `should return bad request when creating with invalid json`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val response =
                client.post("/v1/integrations/unifi") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_2_USER_3.token}")
                    contentType(ContentType.Application.Json)
                    setBody("{invalid json}")
                }

            assertEquals(HttpStatusCode.Companion.BadRequest, response.status)
        }

    @Test
    fun `should return bad request when creating with missing fields`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val response =
                client.post("/v1/integrations/unifi") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_2_USER_3.token}")
                    contentType(ContentType.Application.Json)
                    setBody("{}")
                }

            assertEquals(HttpStatusCode.Companion.BadRequest, response.status)
        }

    // ============================================================
    // PUT /integrations/unifi/{id}
    // ============================================================

    @Test
    fun `should return not found when updating non-existent integration`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val request =
                UnifiIntegrationRequest(
                    unifiConsoleUrl = "https://updated.unifi.com",
                    unifiUsername = "updated-user",
                    unifiPassword = "updated-secret",
                )

            val response =
                client.put("/v1/integrations/unifi/$testIntegrationId") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_2_USER_3.token}")
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }

            assertEquals(HttpStatusCode.Companion.NotFound, response.status)
        }

    @Test
    fun `should return unauthorized when updating without token`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val request =
                UnifiIntegrationRequest(
                    unifiConsoleUrl = "https://updated.unifi.com",
                    unifiUsername = "updated-user",
                    unifiPassword = "updated-secret",
                )

            val response =
                client.put("/v1/integrations/unifi/$testIntegrationId") {
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }

            assertEquals(HttpStatusCode.Companion.Unauthorized, response.status)
        }

    // ============================================================
    // DELETE /integrations/unifi/{id}
    // ============================================================

    @Test
    fun `should delete unifi integration successfully`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val response =
                client.delete("/v1/integrations/unifi/$testIntegrationId") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                }

            assertEquals(HttpStatusCode.Companion.NoContent, response.status)

            val getResponse =
                client.get("/v1/integrations/unifi") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                }

            assertEquals(HttpStatusCode.Companion.OK, getResponse.status)
            assertEquals("[]", getResponse.bodyAsText())
        }

    @Test
    fun `should return not found when deleting non-existent integration`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val response =
                client.delete("/v1/integrations/unifi/$testIntegrationId") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_2_USER_3.token}")
                }

            assertEquals(HttpStatusCode.Companion.NotFound, response.status)
        }

    @Test
    fun `should return unauthorized when deleting without token`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val response = client.delete("/v1/integrations/unifi/$testIntegrationId")

            assertEquals(HttpStatusCode.Companion.Unauthorized, response.status)
        }

    // ============================================================
    // CREDENTIAL SECURITY TESTS
    // ============================================================

    @Test
    fun `credentials should never be returned in GET response`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val response =
                client.get("/v1/integrations/unifi") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                }

            assertEquals(HttpStatusCode.Companion.OK, response.status)
            val responseText = response.bodyAsText()

            assertFalse(responseText.contains(testPassword), "Secret should not be in response: $responseText")
        }

    @Test
    fun `credentials should never leak through JSON serialization`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val response =
                client.get("/v1/integrations/unifi") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                }

            assertEquals(HttpStatusCode.Companion.OK, response.status)
            val responseText = response.bodyAsText()

            assertContains(responseText, "\"id\"")
            assertContains(responseText, "\"unifi_username\"")
            assertContains(responseText, "\"unifi_console_url\"")

            assertFalse(responseText.contains("\"unifi_password\""))
        }

    // ============================================================
    // LOG INJECTION (CWE-117)
    // ============================================================

    private val forged = "2026-10-27 12:00:00.000 ERROR [main] eu.kasify.Fake - FORGED entry"

    /** Starts a fake Unifi console whose login endpoint answers 200 with a non-JSON, multi-line body. */
    private fun startFakeConsole(): HttpServer =
        HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0).apply {
            createContext("/api/auth/login") { exchange ->
                val body = "x\n$forged".toByteArray()
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
            start()
        }

    private fun HttpServer.consoleRequest() =
        UnifiIntegrationRequest(
            unifiConsoleUrl = "http://127.0.0.1:${address.port}",
            unifiUsername = "admin\r\n$forged",
            unifiPassword = "secret",
        )

    @Test
    fun `create does not log forged lines when the console login answers with a non-JSON body`() =
        testApplication {
            application { configureTestModules() }
            startApplication()
            val client = createClient()
            val console = startFakeConsole()
            try {
                lateinit var response: HttpResponse
                val events =
                    captureKasifyLogs {
                        response =
                            client.post("/v1/integrations/unifi") {
                                header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_2_USER_3.token}")
                                contentType(ContentType.Application.Json)
                                setBody(console.consoleRequest())
                            }
                    }

                assertEquals(HttpStatusCode.Companion.Forbidden, response.status)
                assertContains(response.bodyAsText(), "integration.invalid_credentials")
                assertNoForgedLogLines(events, forged)
                // The handler's logger.warn(e) logged the plugin's exception chain.
                assertTrue(events.any { it.loggerName.contains("UnifiIntegrationHandler") && it.throwableProxy != null })
            } finally {
                console.stop(0)
            }
        }

    @Test
    fun `update does not log forged lines when the console login answers with a non-JSON body`() =
        testApplication {
            application { configureTestModules() }
            startApplication()
            val client = createClient()
            val console = startFakeConsole()
            try {
                lateinit var response: HttpResponse
                val events =
                    captureKasifyLogs {
                        response =
                            client.put("/v1/integrations/unifi/$testIntegrationId") {
                                header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                                contentType(ContentType.Application.Json)
                                setBody(console.consoleRequest())
                            }
                    }

                assertEquals(HttpStatusCode.Companion.Forbidden, response.status)
                assertContains(response.bodyAsText(), "integration.invalid_credentials")
                assertNoForgedLogLines(events, forged)
                assertTrue(events.any { it.loggerName.contains("UnifiIntegrationHandler") && it.throwableProxy != null })
            } finally {
                console.stop(0)
            }
        }
}
