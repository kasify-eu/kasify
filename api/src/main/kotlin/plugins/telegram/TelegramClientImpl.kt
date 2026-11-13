package eu.kasify.plugins.telegram

import eu.kasify.plugins.telegram.model.SendMessageRequest
import eu.kasify.plugins.telegram.model.SendMessageResponse
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import java.util.Collections
import java.util.IdentityHashMap

/**
 * Production [TelegramClient] backed by the Bot API. Follows the same Ktor CIO + timeout + retry
 * pattern as [eu.kasify.plugins.nuki.NukiPlugin]. 5xx are retried by the client; a still-failing 5xx
 * or a 429 surfaces as a retryable [TelegramException], while other 4xx (bot blocked, invalid chat)
 * surface as non-retryable so the dispatch worker dead-letters instead of looping.
 */
class TelegramClientImpl(
    private val botToken: String,
    httpClient: HttpClient? = null,
) : TelegramClient {
    private val logger = KotlinLogging.logger {}
    private val baseUrl = "https://api.telegram.org/bot$botToken"
    private val json = Json { ignoreUnknownKeys = true }

    private val client =
        httpClient ?: HttpClient(CIO) {
            install(HttpTimeout) {
                requestTimeoutMillis = 30_000
                connectTimeoutMillis = 10_000
                socketTimeoutMillis = 30_000
            }
            install(HttpRequestRetry) {
                retryOnServerErrors(maxRetries = 3)
            }
            engine {
                dispatcher = Dispatchers.IO
            }
        }

    override suspend fun sendMessage(
        chatId: Long,
        text: String,
    ): String {
        val response =
            try {
                client.post("$baseUrl/sendMessage") {
                    contentType(ContentType.Application.Json)
                    setBody(json.encodeToString(SendMessageRequest.serializer(), SendMessageRequest(chatId, text)))
                }
            } catch (e: Exception) {
                // Ktor transport errors (e.g. HttpRequestTimeoutException) embed the request URL, which
                // carries the bot token. Redact it from the message and from every throwable in the cause
                // chain, so it never reaches logs, notifications.last_error or Sentry.
                throw TelegramException(
                    "Telegram request failed: ${redact(e.message)}",
                    retryable = true,
                    cause = redactedCopy(e, Collections.newSetFromMap(IdentityHashMap())),
                )
            }

        val body = response.bodyAsText()
        if (!response.status.isSuccess()) {
            val status = response.status.value
            // 429 (rate limit) and 5xx are transient; other 4xx are permanent for this message.
            val retryable = status == 429 || status in 500..599
            logger.warn { "Telegram sendMessage failed status=$status retryable=$retryable body=$body" }
            throw TelegramException("Telegram API $status: $body", retryable = retryable)
        }

        val parsed = json.decodeFromString(SendMessageResponse.serializer(), body)
        val messageId =
            parsed.result?.messageId
                ?: throw TelegramException("Telegram response had no message id: $body", retryable = false)
        return messageId.toString()
    }

    fun close() {
        client.close()
    }

    /** Removes the bot token from [text], both verbatim and as the `/bot<token>` URL path segment. */
    private fun redact(text: String?): String? {
        if (text == null) return null
        val withoutToken = if (botToken.isEmpty()) text else text.replace(botToken, REDACTED)
        return BOT_PATH_SEGMENT.replace(withoutToken, REDACTED)
    }

    /**
     * Copies [throwable] and its cause/suppressed chain into [RedactedTransportException]s that keep each
     * original class name and stack trace but carry a token-redacted message. [seen] guards against cycles.
     */
    private fun redactedCopy(
        throwable: Throwable,
        seen: MutableSet<Throwable>,
    ): Throwable? {
        if (!seen.add(throwable)) return null
        val message = redact(throwable.message)
        val copy =
            RedactedTransportException(
                if (message == null) throwable.javaClass.name else "${throwable.javaClass.name}: $message",
                throwable.cause?.let { redactedCopy(it, seen) },
            )
        copy.stackTrace = throwable.stackTrace
        throwable.suppressed.forEach { suppressed -> redactedCopy(suppressed, seen)?.let(copy::addSuppressed) }
        return copy
    }

    private companion object {
        const val REDACTED = "<redacted>"

        /** The token segment of a Bot API URL path (`/bot<token>/<method>`). */
        val BOT_PATH_SEGMENT = Regex("(?<=/bot)[^/?#\\s,\\]]+")
    }
}

/**
 * Stand-in for a transport exception whose message could contain the bot token. The message is prefixed
 * with the original exception's class name, so the failure type stays visible in logs and Sentry.
 */
private class RedactedTransportException(
    message: String,
    cause: Throwable?,
) : Exception(message, cause)
