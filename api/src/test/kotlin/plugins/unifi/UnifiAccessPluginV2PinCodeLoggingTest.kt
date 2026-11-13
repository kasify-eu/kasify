package eu.kasify.plugins.unifi

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import eu.kasify.helpers.loadTestResource
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The guest's door PIN (and the `pin_code_token` Unifi derives from it) must never reach the logs,
 * while the request sent to Unifi must still carry the PIN.
 */
class UnifiAccessPluginV2PinCodeLoggingTest {
    private val pin = "90817263"
    private val pinCodeToken = "c0ffee11".repeat(8)

    private val pluginLogger = LoggerFactory.getLogger(UnifiAccessPluginV2::class.java) as Logger
    private val appender = ListAppender<ILoggingEvent>()
    private var previousLevel: Level? = null

    @Before
    fun attachAppender() {
        previousLevel = pluginLogger.level
        pluginLogger.level = Level.DEBUG
        appender.start()
        pluginLogger.addAppender(appender)
    }

    @After
    fun detachAppender() {
        pluginLogger.detachAppender(appender)
        appender.stop()
        pluginLogger.level = previousLevel
    }

    private val logLines: List<String>
        get() = appender.list.map { it.formattedMessage }

    private fun assertNoSecretsLogged() {
        logLines.forEach { line ->
            assertFalse(line.contains(pin), "PIN leaked into log line: $line")
            assertFalse(line.contains(pinCodeToken), "pin_code_token leaked into log line: $line")
        }
    }

    private fun pluginRespondingWith(
        status: HttpStatusCode,
        body: String,
        sentBodies: MutableList<String>,
    ): UnifiAccessPluginV2 {
        val engine =
            MockEngine { request ->
                if (request.url.encodedPath == "/proxy/access/api/v2/visitor" && request.method == HttpMethod.Post) {
                    sentBodies += String(request.body.toByteArray())
                    respond(
                        content = ByteReadChannel(body),
                        status = status,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                } else {
                    respond(content = ByteReadChannel("Not Found"), status = HttpStatusCode.NotFound)
                }
            }
        return UnifiAccessPluginV2(baseUrl = "https://unifidevice.com", httpClient = HttpClient(engine)).also {
            it.restoreSession(csrfToken = "csrf", cookiesJson = "[]")
        }
    }

    private suspend fun UnifiAccessPluginV2.createGuestVisitor() =
        createVisitorSimplified(
            firstName = "Kasify-fn",
            lastName = "Kasify-ln",
            startTimeUTC = LocalDateTime.of(2026, 1, 27, 16, 0),
            endTimeUTC = LocalDateTime.of(2026, 1, 28, 16, 0),
            floorId = "floor-1",
            doorId = "door-1",
            pinCode = pin,
            remarks = "remarks",
        )

    @Test
    fun `successful visitor creation sends the PIN but never logs it or its token`() =
        runTest {
            val responseBody =
                loadTestResource("unifi/create_visitor_request_without_schedule.response.json")
                    .replace("0".repeat(64), pinCodeToken)
            assertTrue(responseBody.contains(pinCodeToken))
            val sentBodies = mutableListOf<String>()
            val plugin = pluginRespondingWith(HttpStatusCode.OK, responseBody, sentBodies)

            val visitor = plugin.createGuestVisitor()

            assertEquals("de7a1786-c3db-4e9b-829c-5718f8688561", visitor.uniqueId)
            assertEquals(pinCodeToken, visitor.pinCode?.pinCodeToken)
            assertEquals(1, sentBodies.size)
            assertTrue(sentBodies.single().contains("\"pin_code\":\"$pin\""), "PIN must still be sent to Unifi")

            val requestLog = logLines.firstOrNull { it.contains("Request body:") }
            assertNotNull(requestLog, "request body is still logged")
            assertTrue(requestLog.contains("\"pin_code\":\"[REDACTED]\""))
            assertTrue(requestLog.contains("Kasify-fn"))
            val responseLog = logLines.firstOrNull { it.contains("Response (") }
            assertNotNull(responseLog, "response is still logged")
            assertTrue(responseLog.contains("\"pin_code\":\"[REDACTED]\""))
            assertTrue(responseLog.contains("de7a1786-c3db-4e9b-829c-5718f8688561"))
            assertNoSecretsLogged()
        }

    @Test
    fun `failed visitor creation never logs the PIN echoed in the error response`() =
        runTest {
            val errorBody = """{"code":-1,"codeS":"CODE_PARAMS_INVALID","msg":"invalid","data":{"pin_code":"$pin"}}"""
            val plugin = pluginRespondingWith(HttpStatusCode.BadRequest, errorBody, mutableListOf())

            assertFailsWith<UnifiApiException> { plugin.createGuestVisitor() }

            val errorLog = logLines.firstOrNull { it.contains("Failed to create visitor") }
            assertNotNull(errorLog, "failure is still logged")
            assertTrue(errorLog.contains("\"pin_code\":\"[REDACTED]\""))
            assertTrue(errorLog.contains("CODE_PARAMS_INVALID"))
            assertNoSecretsLogged()
        }

    @Test
    fun `deeply nested or unparseable bodies mentioning pin_code are withheld`() =
        runTest {
            val deepBody = """{"pin_code":"$pin","x":""" + "[".repeat(5_000) + "]".repeat(5_000) + "}"
            val brokenBody = """not json "pin_code":"$pin""""
            for (body in listOf(deepBody, brokenBody)) {
                appender.list.clear()
                val plugin = pluginRespondingWith(HttpStatusCode.BadRequest, body, mutableListOf())

                assertFailsWith<UnifiApiException> { plugin.createGuestVisitor() }

                val errorLog = logLines.firstOrNull { it.contains("Failed to create visitor") }
                assertNotNull(errorLog)
                assertTrue(errorLog.contains("<withheld: body contains pin_code>"))
                assertNoSecretsLogged()
            }
        }

    @Test
    fun `bodies without pin_code are logged unchanged`() =
        runTest {
            val plugin = pluginRespondingWith(HttpStatusCode.BadRequest, "Bad Request: upstream down", mutableListOf())

            assertFailsWith<UnifiApiException> { plugin.createGuestVisitor() }

            val errorLog = logLines.firstOrNull { it.contains("Failed to create visitor") }
            assertNotNull(errorLog)
            assertTrue(errorLog.contains("response=Bad Request: upstream down"))
        }
}
