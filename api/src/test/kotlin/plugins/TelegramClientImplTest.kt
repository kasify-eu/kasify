package eu.kasify.plugins

import eu.kasify.plugins.telegram.TelegramClientImpl
import eu.kasify.plugins.telegram.TelegramException
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.ConnectTimeoutException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.SocketTimeoutException
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TelegramClientImplTest {
    private fun clientReturning(
        status: HttpStatusCode,
        body: String,
    ): TelegramClientImpl {
        val engine =
            MockEngine {
                respond(
                    content = ByteReadChannel(body),
                    status = status,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            }
        return TelegramClientImpl(botToken = "test-token", httpClient = HttpClient(engine))
    }

    @Test
    fun `sendMessage returns the message id on success`() =
        runBlocking {
            val client =
                clientReturning(
                    HttpStatusCode.OK,
                    """{"ok":true,"result":{"message_id":4242,"chat":{"id":99}}}""",
                )
            assertEquals("4242", client.sendMessage(99, "hello"))
        }

    @Test
    fun `a 4xx is a non-retryable failure`() =
        runBlocking {
            val client =
                clientReturning(
                    HttpStatusCode.Forbidden,
                    """{"ok":false,"description":"Forbidden: bot was blocked by the user"}""",
                )
            val ex = assertFailsWith<TelegramException> { client.sendMessage(99, "hi") }
            assertTrue(!ex.retryable, "403 should be terminal")
        }

    @Test
    fun `a 5xx is a retryable failure`() =
        runBlocking {
            val client = clientReturning(HttpStatusCode.InternalServerError, "upstream boom")
            val ex = assertFailsWith<TelegramException> { client.sendMessage(99, "hi") }
            assertTrue(ex.retryable, "500 should be retryable")
        }

    @Test
    fun `a 429 is retryable`() =
        runBlocking {
            val client =
                clientReturning(
                    HttpStatusCode.TooManyRequests,
                    """{"ok":false,"description":"Too Many Requests","parameters":{"retry_after":5}}""",
                )
            val ex = assertFailsWith<TelegramException> { client.sendMessage(99, "hi") }
            assertTrue(ex.retryable, "429 should be retryable")
        }

    // --- Bot token must never leak through transport exception messages (URL = .../bot<token>/...) ---

    private val botToken = "123456789:AAHsecret-Token_value42"

    private fun clientWithTimeouts(
        requestTimeoutMillis: Long = 30_000,
        handler: suspend (HttpRequestData) -> Nothing,
    ): TelegramClientImpl {
        val engine = MockEngine { request -> handler(request) }
        val httpClient =
            HttpClient(engine) {
                install(HttpTimeout) {
                    this.requestTimeoutMillis = requestTimeoutMillis
                    connectTimeoutMillis = 10_000
                    socketTimeoutMillis = 30_000
                }
            }
        return TelegramClientImpl(botToken = botToken, httpClient = httpClient)
    }

    private fun assertNoTokenLeak(ex: TelegramException) {
        val secrets = listOf(botToken, botToken.substringAfter(':'))
        val chain = generateSequence<Throwable>(ex) { it.cause }.toList()
        assertTrue(chain.size > 1, "the (redacted) transport cause should be kept for debugging")
        val all = chain + chain.flatMap { it.suppressed.toList() }
        val rendered = all.map { "${it.message}" } + all.map { it.toString() } + ex.stackTraceToString()
        for (text in rendered) {
            for (secret in secrets) {
                assertFalse(text.contains(secret), "bot token leaked: $text")
            }
        }
    }

    @Test
    fun `a request timeout does not leak the bot token and stays retryable`() =
        runBlocking {
            val client =
                clientWithTimeouts(requestTimeoutMillis = 50) {
                    delay(10_000)
                    error("unreachable")
                }
            val ex = assertFailsWith<TelegramException> { client.sendMessage(99, "hi") }
            assertTrue(ex.retryable, "a timeout should be retryable")
            assertNoTokenLeak(ex)
            val message = ex.message.orEmpty()
            assertTrue(message.contains("Request timeout has expired"), message)
            assertTrue(message.contains("/bot<redacted>/sendMessage"), message)
            assertTrue(message.contains("request_timeout=50 ms"), message)
        }

    @Test
    fun `a connect timeout does not leak the bot token anywhere in the cause chain`() =
        runBlocking {
            val client =
                clientWithTimeouts { request ->
                    throw ConnectTimeoutException(request, cause = IOException("connect to ${request.url} failed"))
                }
            val ex = assertFailsWith<TelegramException> { client.sendMessage(99, "hi") }
            assertTrue(ex.retryable, "a timeout should be retryable")
            assertNoTokenLeak(ex)
            assertTrue(ex.message.orEmpty().contains("connect_timeout=10000 ms"), ex.message)
            val trace = ex.stackTraceToString()
            assertTrue(trace.contains("io.ktor.client.network.sockets.ConnectTimeoutException"), trace)
            assertTrue(trace.contains("java.io.IOException: connect to https://api.telegram.org/bot<redacted>/sendMessage"), trace)
        }

    @Test
    fun `a socket timeout does not leak the bot token through suppressed exceptions`() =
        runBlocking {
            val client =
                clientWithTimeouts { request ->
                    throw SocketTimeoutException(request).apply {
                        addSuppressed(IllegalStateException("while writing to ${request.url}"))
                    }
                }
            val ex = assertFailsWith<TelegramException> { client.sendMessage(99, "hi") }
            assertTrue(ex.retryable, "a timeout should be retryable")
            assertNoTokenLeak(ex)
            assertTrue(ex.message.orEmpty().contains("socket_timeout=30000"), ex.message)
            assertTrue(ex.stackTraceToString().contains("java.net.SocketTimeoutException"))
        }
}
