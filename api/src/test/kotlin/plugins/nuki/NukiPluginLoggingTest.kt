package eu.kasify.plugins.nuki

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.slf4j.LoggerFactory
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Regression test: the Nuki auth list contains the keypad PIN of every authorization
 * on the lock (including codes Kasify never created), so it must never reach the logs.
 */
class NukiPluginLoggingTest {
    private val kasifyCode = "482915"
    private val foreignPermanentCode = "730164"

    private val authListBody =
        """
        [
          {
            "id": "auth-object-kasify",
            "smartlockId": 1,
            "authId": 8192,
            "code": $kasifyCode,
            "type": 13,
            "name": "Jane Guest",
            "enabled": true,
            "remoteAllowed": false,
            "lockCount": 0,
            "creationDate": "2025-08-02T18:13:19.000Z",
            "updateDate": "2025-10-03T14:37:21.279Z"
          },
          {
            "id": "auth-object-permanent",
            "smartlockId": 1,
            "authId": 8193,
            "code": $foreignPermanentCode,
            "type": 13,
            "name": "Owner permanent code",
            "enabled": true,
            "remoteAllowed": false,
            "lockCount": 12,
            "creationDate": "2024-01-07T03:45:15.000Z",
            "updateDate": "2025-10-03T14:37:17.788Z"
          }
        ]
        """.trimIndent()

    private val nukiLogger = LoggerFactory.getLogger("eu.kasify.plugins.nuki") as Logger
    private val appender = ListAppender<ILoggingEvent>()
    private var previousLevel: Level? = null

    @Before
    fun attachAppender() {
        previousLevel = nukiLogger.level
        nukiLogger.level = Level.DEBUG
        appender.start()
        nukiLogger.addAppender(appender)
    }

    @After
    fun detachAppender() {
        nukiLogger.detachAppender(appender)
        appender.stop()
        nukiLogger.level = previousLevel
    }

    @Test
    fun `getAccessCodes does not log keypad codes`() =
        runTest {
            val mockEngine =
                MockEngine {
                    respond(
                        content = ByteReadChannel(authListBody),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            val plugin = NukiPlugin("test-token", HttpClient(mockEngine))

            val codes = plugin.getAccessCodes("1")

            // The decoded result still carries the codes for the callers that match on them.
            assertEquals(listOf(kasifyCode.toLong(), foreignPermanentCode.toLong()), codes.map { it.code })

            val messages = appender.list.map { it.formattedMessage }
            messages.forEach { message ->
                assertFalse(message.contains(kasifyCode), "Kasify keypad code leaked into log: $message")
                assertFalse(message.contains(foreignPermanentCode), "Non-Kasify keypad code leaked into log: $message")
            }
            assertTrue(
                messages.any { it.contains("2 authorizations") && it.contains("auth-object-kasify") },
                "Expected a DEBUG line with the authorization count and ids, got: $messages",
            )
        }
}
