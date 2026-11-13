package eu.kasify.plugins.unifi

import eu.kasify.helpers.assertNoForgedLogLines
import eu.kasify.helpers.assertNoLineBreaksInChain
import eu.kasify.helpers.captureKasifyLogs
import eu.kasify.helpers.loadTestResource
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.time.LocalDateTime
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class UnifiAccessPluginV2Test {
    private lateinit var plugin: UnifiAccessPluginV2

    @Before
    fun setup() {
        val mockEngine =
            MockEngine { request ->
                when {
                    request.url.toString().contains("/api/v1/developer/visitors") && request.method == HttpMethod.Post -> {
                        respond(
                            content = ByteReadChannel(loadTestResource("unifi/create_visitor_response.json")),
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "application/json"),
                        )
                    }

                    request.url.toString().contains("/api/v1/developer/visitors") && request.method == HttpMethod.Get -> {
                        respond(
                            content = ByteReadChannel(loadTestResource("unifi/list_visitors_response.json")),
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "application/json"),
                        )
                    }

                    request.url.toString().contains("/api/v1/developer/visitors/") && request.method == HttpMethod.Delete -> {
                        respond(
                            content = ByteReadChannel(loadTestResource("unifi/delete_visitor_response.json")),
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "application/json"),
                        )
                    }

                    request.url.toString().contains("/api/v2/schedules") && request.method == HttpMethod.Get -> {
                        respond(
                            content = ByteReadChannel(loadTestResource("unifi/schedules_response.json")),
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "application/json"),
                        )
                    }

                    request.url.toString().contains("/api/v1/developer/door_groups/topology") && request.method == HttpMethod.Get -> {
                        respond(
                            content = ByteReadChannel(loadTestResource("unifi/door_groups_topology_response.json")),
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "application/json"),
                        )
                    }

                    request.url.toString().contains("/api/v2/access_groups/resources") && request.method == HttpMethod.Get -> {
                        respond(
                            content = ByteReadChannel(loadTestResource("unifi/get_doors_response.json")),
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "application/json"),
                        )
                    }

                    else -> {
                        respond(
                            content = ByteReadChannel("Not Found"),
                            status = HttpStatusCode.NotFound,
                        )
                    }
                }
            }

        val mockClient = HttpClient(mockEngine)
        plugin =
            UnifiAccessPluginV2(
                baseUrl = "https://unifidevice.com",
                httpClient = mockClient,
            )
    }

    val baseUrl = "https://192.168.1.1:12445"
    val token = "UNIFI_TEST_TOKEN"

    @Test
    fun `test login succeeds`() =
        runTest {
            val mockEngine =
                MockEngine { request ->
                    when {
                        request.url.toString().contains("/api/auth/login") && request.method == HttpMethod.Post -> {
                            respond(
                                content = ByteReadChannel(loadTestResource("unifi/login_response.json")),
                                status = HttpStatusCode.OK,
                                headers =
                                    headersOf(
                                        HttpHeaders.ContentType to listOf("application/json"),
                                        HttpHeaders.SetCookie to listOf("TOKEN=test-cookie-value; Path=/; HttpOnly"),
                                    ),
                            )
                        }
                        else -> {
                            respond(
                                content = ByteReadChannel("Not Found"),
                                status = HttpStatusCode.NotFound,
                            )
                        }
                    }
                }

            val mockClient =
                HttpClient(mockEngine) {
                    install(HttpCookies)
                }
            val plugin =
                UnifiAccessPluginV2(
                    baseUrl = "https://unifidevice.com",
                    httpClient = mockClient,
                )

            val loginResponse =
                plugin.login(
                    username = "testuser",
                    password = "testpass",
                )

            assertNotNull(loginResponse)
            assertEquals("testuser", loginResponse.username)
            assertEquals("Test User", loginResponse.fullName)
            assertTrue(loginResponse.isSuperAdmin)
            assertEquals("ACTIVE", loginResponse.status)
        }

    @Test
    fun `test get doors returns mocked data`() =
        runTest {
            val doors = plugin.getDoors()

            assertEquals(2, doors.size)

            assertEquals("7f755fdd-6fc5-40e7-a6be-c6c6201cc5e5", doors[0].resourceId)
            assertEquals("d57e449f-f9b9-4c77-831b-ded1757cf9b7", doors[0].floorId)

            assertEquals("Puerta Almacen", doors[0].resourceName)
            assertEquals("door", doors[0].resourceType)
            assertEquals("UA-Hub-Door-Mini", doors[0].resourceExtraType)

            assertEquals("6a3596e3-bcf9-4aea-b524-f32c88e9db74", doors[1].resourceId)
            assertEquals("d57e449f-f9b9-4c77-831b-ded1757cf9b7", doors[1].floorId)

            assertEquals("Puerta Exterior", doors[1].resourceName)
            assertEquals("door", doors[1].resourceType)
            assertEquals("UAH", doors[1].resourceExtraType)
        }

    // ============================================================
    // Log injection (CWE-117): console- and request-supplied text must never
    // reach a log line, an exception message or a logged cause chain unescaped.
    // ============================================================

    private val forged = "2026-10-27 12:00:00.000 ERROR [main] eu.kasify.Fake - FORGED entry"

    private fun pluginWith(handler: MockRequestHandler): UnifiAccessPluginV2 =
        UnifiAccessPluginV2(
            baseUrl = "https://unifidevice.com",
            httpClient = HttpClient(MockEngine(handler)),
        )

    private fun pluginResponding(
        body: String,
        status: HttpStatusCode = HttpStatusCode.OK,
    ): UnifiAccessPluginV2 =
        pluginWith {
            respond(
                content = ByteReadChannel(body),
                status = status,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

    private val visitorStart = LocalDateTime.of(2026, 1, 1, 15, 0)
    private val visitorEnd = LocalDateTime.of(2026, 1, 3, 11, 0)

    @Test
    fun `login escapes CR and LF in the username it logs`() =
        runTest {
            val plugin = pluginResponding(loadTestResource("unifi/login_response.json"))

            val events = captureKasifyLogs { plugin.login("admin\r\n$forged", "secret") }

            assertNoForgedLogLines(events, forged)
            assertTrue(events.any { it.formattedMessage.contains("user: admin\\r\\n$forged") })
        }

    @Test
    fun `login escapes the console response body when the console rejects the login`() =
        runTest {
            val plugin = pluginResponding("denied\n$forged", HttpStatusCode.Unauthorized)

            lateinit var thrown: Throwable
            val events =
                captureKasifyLogs {
                    thrown = assertFailsWith<UnifiAuthenticationException> { plugin.login("admin", "secret") }
                }

            assertNoForgedLogLines(events, forged)
            assertTrue(events.any { it.formattedMessage.contains("response=denied\\n$forged") })
            assertNoLineBreaksInChain(thrown)
        }

    @Test
    fun `login escapes the username echoed back by the console`() =
        runTest {
            val body =
                loadTestResource("unifi/login_response.json")
                    .replace("\"username\": \"testuser\"", "\"username\": \"testuser\\n$forged\"")
            val plugin = pluginResponding(body)

            val events = captureKasifyLogs { plugin.login("testuser", "secret") }

            assertNoForgedLogLines(events, forged)
            assertTrue(events.any { it.formattedMessage.contains("Successfully logged in as testuser\\n$forged") })
        }

    @Test
    fun `updateVisitor escapes the visitor id and the console response body on failure`() =
        runTest {
            val plugin = pluginResponding("oops\n$forged", HttpStatusCode.InternalServerError)

            lateinit var thrown: Throwable
            val events =
                captureKasifyLogs {
                    thrown =
                        assertFailsWith<UnifiApiException> {
                            plugin.updateVisitor("visitor-1\n$forged", visitorStart, visitorEnd)
                        }
                }

            assertNoForgedLogLines(events, forged)
            assertTrue(events.any { it.formattedMessage.contains("response=oops\\n$forged") })
            assertNoLineBreaksInChain(thrown)
        }

    @Test
    fun `deleteVisitor escapes the console response body and status text on failure`() =
        runTest {
            val plugin =
                pluginResponding("oops\r\n$forged", HttpStatusCode(500, "Internal Server Error\r\n$forged"))

            lateinit var thrown: Throwable
            val events =
                captureKasifyLogs {
                    thrown = assertFailsWith<UnifiApiException> { plugin.deleteVisitor("visitor-1") }
                }

            assertNoForgedLogLines(events, forged)
            assertTrue(events.any { it.formattedMessage.contains("response=oops\\r\\n$forged") })
            assertNoLineBreaksInChain(thrown)
            assertContains(thrown.message!!, "Failed to delete visitor visitor-1: 500 Internal Server Error\\r\\n$forged")
        }

    @Test
    fun `getDoors escapes and truncates the console response body on failure`() =
        runTest {
            val body = "oops\n$forged" + "A".repeat(5_000)
            val plugin = pluginResponding(body, HttpStatusCode.BadGateway)

            lateinit var thrown: Throwable
            val events =
                captureKasifyLogs {
                    thrown = assertFailsWith<UnifiApiException> { plugin.getDoors() }
                }

            assertNoForgedLogLines(events, forged)
            val bodyLines = events.filter { it.formattedMessage.contains("oops\\n$forged") }
            // Both the DEBUG response line and the ERROR failure line, each truncated.
            assertEquals(2, bodyLines.size)
            bodyLines.forEach {
                assertContains(it.formattedMessage, "...[truncated")
                assertTrue(it.formattedMessage.length < body.length)
            }
            assertNoLineBreaksInChain(thrown)
        }

    @Test
    fun `login does not log or chain the raw body when the console answers 200 with non-JSON`() =
        runTest {
            val plugin = pluginResponding("x\n$forged")

            lateinit var thrown: Throwable
            val events =
                captureKasifyLogs {
                    thrown = assertFailsWith<UnifiApiException> { plugin.login("admin", "secret") }
                }

            assertNoForgedLogLines(events, forged)
            assertNoLineBreaksInChain(thrown)
            assertContains(thrown.message!!, "Login failed: ")
            // The cause keeps the original exception class name for diagnosis.
            assertContains(thrown.cause!!.message!!, "JsonDecodingException")
        }

    @Test
    fun `getDoors does not log or chain the raw body when the console answers 200 with non-JSON`() =
        runTest {
            val plugin = pluginResponding("<html>\n$forged\r\n</html>")

            lateinit var thrown: Throwable
            val events =
                captureKasifyLogs {
                    thrown = assertFailsWith<UnifiApiException> { plugin.getDoors() }
                }

            assertNoForgedLogLines(events, forged)
            assertNoLineBreaksInChain(thrown)
            assertContains(thrown.message!!, "Failed to fetch doors: ")
        }

    @Test
    fun `getDoors escapes an API error code and msg containing line breaks`() =
        runTest {
            val plugin = pluginResponding("""{"code":1,"codeS":"CODE_ERR\u2028$forged","msg":"bad\r\n$forged"}""")

            lateinit var thrown: Throwable
            val events =
                captureKasifyLogs {
                    thrown = assertFailsWith<UnifiApiException> { plugin.getDoors() }
                }

            assertNoForgedLogLines(events, forged)
            assertTrue(events.any { it.formattedMessage.contains("code=CODE_ERR\\u2028$forged") })
            assertTrue(events.any { it.formattedMessage.contains("msg=bad\\r\\n$forged") })
            assertNoLineBreaksInChain(thrown)
            assertContains(thrown.message!!, "Failed to fetch doors: bad\\r\\n$forged")
        }

    @Test
    fun `visitor calls do not log or chain raw text from a transport failure`() =
        runTest {
            val plugin = pluginWith { throw IOException("connection reset\n$forged") }

            lateinit var updateError: Throwable
            lateinit var deleteError: Throwable
            val events =
                captureKasifyLogs {
                    updateError =
                        assertFailsWith<UnifiApiException> {
                            plugin.updateVisitor("visitor-1", visitorStart, visitorEnd)
                        }
                    deleteError = assertFailsWith<UnifiApiException> { plugin.deleteVisitor("visitor-1") }
                }

            assertNoForgedLogLines(events, forged)
            assertNoLineBreaksInChain(updateError)
            assertNoLineBreaksInChain(deleteError)
            assertContains(updateError.message!!, "connection reset\\n$forged")
            assertContains(deleteError.message!!, "connection reset\\n$forged")
        }
}
