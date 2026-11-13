package eu.kasify

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.AppenderBase
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.log
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.delay
import org.slf4j.LoggerFactory
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.fail
import ch.qos.logback.classic.Logger as LogbackLogger

/**
 * Secrets carried in the request target (the `/metrics?token=` scrape token and the
 * capability tokens in public route paths) must never reach the access log line, the
 * `request_uri` MDC, or the slow-request warning written by [configureRequestLogging].
 */
class RequestLoggingTest {
    private data class CapturedEvent(
        val loggerName: String,
        val message: String,
        val mdc: Map<String, String>,
    )

    /** Copies each event eagerly: MDC is read lazily and would otherwise be lost across threads. */
    private class CapturingAppender : AppenderBase<ILoggingEvent>() {
        val events = CopyOnWriteArrayList<CapturedEvent>()

        override fun append(event: ILoggingEvent) {
            events += CapturedEvent(event.loggerName, event.formattedMessage, HashMap(event.mdcPropertyMap))
        }
    }

    private val rootLogger = LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME) as LogbackLogger
    private val appender = CapturingAppender()
    private var previousRootLevel: Level? = null

    @BeforeTest
    fun attachAppender() {
        previousRootLevel = rootLogger.level
        // CallLogging logs at DEBUG outside production; make sure it is not filtered out.
        rootLogger.level = Level.DEBUG
        appender.context = rootLogger.loggerContext
        appender.start()
        rootLogger.addAppender(appender)
    }

    @AfterTest
    fun detachAppender() {
        rootLogger.detachAppender(appender)
        appender.stop()
        rootLogger.level = previousRootLevel
    }

    private fun Application.testApp(onRequest: suspend () -> Unit = {}) {
        configureRequestLogging()
        routing {
            get("/metrics") { call.respondText("ok") }
        }
        versionedRouting {
            get("/public/tasks/{token}") {
                onRequest()
                call.respondText("task")
            }
            post("/invitations/{token}/accept") { call.respondText("accepted") }
            post("/marketing/unsubscribe/{token}") { call.respondText("unsubscribed") }
            get("/checkin/{code}") { call.respondText("snapshot") }
            post("/checkin/{code}/guests") { call.respondText("registered") }
            get("/properties/{id}") { call.respondText("property") }
        }
    }

    private suspend fun awaitEvent(predicate: (CapturedEvent) -> Boolean): CapturedEvent {
        repeat(500) {
            appender.events.firstOrNull(predicate)?.let { return it }
            delay(10)
        }
        fail("Expected log event was not captured; got ${appender.events.map { it.message }}")
    }

    private suspend fun HttpClient.assertLogged(
        requestId: String,
        method: String,
        target: String,
        expectedPath: String,
        accessLoggerName: String,
    ) {
        val response =
            if (method == "GET") {
                get(target) { header(HttpHeaders.XRequestId, requestId) }
            } else {
                post(target) { header(HttpHeaders.XRequestId, requestId) }
            }
        assertEquals(HttpStatusCode.OK, response.status, "request to $target")

        val accessLine =
            awaitEvent { it.loggerName == accessLoggerName && it.mdc["request_id"] == requestId }
        // Format: "<status> <method> <path> (<n>ms) ..."
        val parts = accessLine.message.split(' ')
        assertEquals("200", parts[0], accessLine.message)
        assertEquals(method, parts[1], accessLine.message)
        assertEquals(expectedPath, parts[2], accessLine.message)
        assertEquals(expectedPath, accessLine.mdc["request_uri"])
    }

    private fun assertNoSecretLogged(vararg secrets: String) {
        for (event in appender.events) {
            for (secret in secrets) {
                assertFalse(event.mdc["request_uri"].orEmpty().contains(secret), "request_uri MDC leaked $secret: $event")
                if (event.loggerName.startsWith("eu.kasify") || event.mdc.containsKey("request_uri")) {
                    assertFalse(event.message.contains(secret), "log message leaked $secret: $event")
                }
            }
        }
    }

    @Test
    fun `access log and request_uri MDC drop the query string and redact token path segments`() =
        testApplication {
            var accessLoggerName = ""
            application {
                accessLoggerName = log.name
                testApp()
            }
            startApplication()

            client.assertLogged("req-metrics", "GET", "/metrics?token=metrics-secret-0001", "/metrics", accessLoggerName)
            client.assertLogged("req-task", "GET", "/v1/public/tasks/task-secret-0002", "/v1/public/tasks/***", accessLoggerName)
            client.assertLogged("req-task-dbl", "GET", "/v1//public/tasks/task-secret-0003", "/v1//public/tasks/***", accessLoggerName)
            client.assertLogged("req-invite", "POST", "/v1/invitations/invite-secret-0004/accept", "/v1/invitations/***/accept", accessLoggerName)
            client.assertLogged("req-unsub", "POST", "/v1/marketing/unsubscribe/unsub-secret-0005", "/v1/marketing/unsubscribe/***", accessLoggerName)
            client.assertLogged("req-checkin", "GET", "/v1/checkin/checkin-secret-0006", "/v1/checkin/***", accessLoggerName)
            client.assertLogged("req-guests", "POST", "/v1/checkin/checkin-secret-0007/guests", "/v1/checkin/***/guests", accessLoggerName)
            // Non-secret paths are logged verbatim, minus the query string.
            client.assertLogged("req-property", "GET", "/v1/properties/42?page=2&q=query-secret-0008", "/v1/properties/42", accessLoggerName)

            assertNoSecretLogged(
                "metrics-secret-0001",
                "task-secret-0002",
                "task-secret-0003",
                "invite-secret-0004",
                "unsub-secret-0005",
                "checkin-secret-0006",
                "checkin-secret-0007",
                "query-secret-0008",
            )
        }

    @Test
    fun `slow request warning redacts token path segments`() =
        testApplication {
            application {
                testApp(onRequest = { delay(1_100) })
            }

            client.get("/v1/public/tasks/slow-secret-0009?token=slow-secret-0010")

            val warning = awaitEvent { it.message.startsWith("Slow request:") }
            assertEquals("Slow request: GET /v1/public/tasks/*** took", warning.message.substringBeforeLast(' '))
            assertNoSecretLogged("slow-secret-0009", "slow-secret-0010")
        }
}
