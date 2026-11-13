package eu.kasify.plugins.nuki

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Before
import org.junit.Test
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class NukiPluginTest {
    private lateinit var plugin: NukiPlugin

    private fun loadTestResource(fileName: String): String =
        this::class.java.classLoader
            .getResourceAsStream("nuki/$fileName")
            ?.readBytes()
            ?.decodeToString()
            ?: throw IllegalArgumentException("Resource not found: nuki/$fileName")

    @Before
    fun setup() {
        val mockEngine =
            MockEngine { request ->
                when {
                    request.url.toString().endsWith("/smartlock") && request.method == HttpMethod.Get -> {
                        respond(
                            content = ByteReadChannel(loadTestResource("smartlocks_response.json")),
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "application/json"),
                        )
                    }

                    request.url.toString().contains("/smartlock/123456789") && request.method == HttpMethod.Get -> {
                        respond(
                            content = ByteReadChannel(loadTestResource("smartlock_single_response.json")),
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "application/json"),
                        )
                    }

                    request.url.toString().contains("/smartlock/1/auth") && request.method == HttpMethod.Get -> {
                        respond(
                            content = ByteReadChannel(loadTestResource("auth_get_keys_response.json")),
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "application/json"),
                        )
                    }

                    request.url.toString().contains("/smartlock/1/auth") && request.method == HttpMethod.Put -> {
                        respond(
                            content = ByteReadChannel(""),
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "application/json"),
                        )
                    }

                    request.url.toString().contains("/smartlock/1/auth/auth-123") && request.method == HttpMethod.Post -> {
                        respond(
                            content = ByteReadChannel(""),
                            status = HttpStatusCode.NoContent,
                            headers = headersOf(HttpHeaders.ContentType, "application/json"),
                        )
                    }

                    request.url.toString().contains("/smartlock/1") && request.method == HttpMethod.Get -> {
                        respond(
                            content = ByteReadChannel(loadTestResource("smartlock_single_response.json")),
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "application/json"),
                        )
                    }

                    request.url.toString().contains("/action") && request.method == HttpMethod.Post -> {
                        respond(
                            content = ByteReadChannel(loadTestResource("action_response.json")),
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

        val mockClient =
            HttpClient(mockEngine) {
                install(ContentNegotiation) {
                    json(
                        Json {
                            ignoreUnknownKeys = true
                        },
                    )
                }
            }
        plugin =
            NukiPlugin(
                apiToken = token,
                httpClient = mockClient,
            )
    }

    val token = "NUKITOKEN"

    @Test
    fun `test get smart locks returns mocked data`() =
        runTest {
            val locks = plugin.getSmartLocks()

            assertTrue(locks.isNotEmpty())
            assertEquals(2, locks.size)
            assertEquals(123456788, locks[0].smartlockId)
            assertEquals(123456789, locks[1].smartlockId)
        }

    @Test
    fun `test get single smart lock returns mocked data`() =
        runTest {
            val lock = plugin.getSmartLock(123456789)

            assertEquals(123456789, lock.smartlockId)
            assertEquals(1999000000, lock.accountId)
            assertEquals("Front Door", lock.name)
        }

    @Test
    fun `test unlock door action`() =
        runTest {
            // Should not throw exception with mocked response
            plugin.unlock(123456789)
        }

    @Test
    fun `test lock door action`() =
        runTest {
            // Should not throw exception with mocked response
            plugin.lock(123456789)
        }

    @Test
    fun `test open door action`() =
        runTest {
            // Should not throw exception with mocked response
            plugin.openDoor(123456789)
        }

    @Test
    fun `get access code`() =
        runTest {
            val codes =
                plugin.getAccessCodes(
                    "1",
                )

            assertNotNull(codes)
        }

    @Test
    fun `update access code dates`() =
        runTest {
            val today = LocalDateTime.now()
            val sevenDaysLater = today.plusDays(7)

            plugin.updateAccessCode(
                smartLockId = "1",
                authId = "auth-123",
                from = today,
                to = sevenDaysLater,
            )
        }

    @Test
    fun `create access code`() =
        runTest {
            val today = LocalDateTime.now()
            val sevenDaysLater = today.plusDays(7)

            // val plugin = NukiPlugin()
            plugin.createAccessCode(
                "TOKEN - API CODE",
                code = "223344",
                from = today,
                to = sevenDaysLater,
                smartLockId = "1",
            )
        }

    // @Test
    fun `production test`() =
        runTest {
            val token = System.getenv("NUKI_API_TOKEN") ?: return@runTest
            val plugin = NukiPlugin(token)
            val smartlocks = plugin.getSmartLocks()
            assertNotNull(smartlocks)
            val codes = plugin.getAccessCodes("22654391911")
            assertNotNull(codes)
            plugin.deleteAccessCode("68ca45503356cd53b51630b5")
        }
}
