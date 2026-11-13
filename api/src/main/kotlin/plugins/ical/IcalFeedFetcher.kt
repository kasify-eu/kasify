package eu.kasify.plugins.ical

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpRedirect
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.SendCountExceedException
import io.ktor.client.plugins.pluginOrNull
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.http.contentType
import io.ktor.http.isSecure
import io.ktor.http.isSuccess
import io.ktor.http.takeFrom
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.net.URI

private val logger = KotlinLogging.logger {}

/**
 * Raised when a calendar feed URL does not yield a usable iCal document (non-2xx status, a body
 * that isn't a VCALENDAR, or a failed request such as a timeout). The message never contains the
 * feed URL, so it is safe to log and to persist as the integration's last_error.
 */
class CalendarFeedException(
    message: String,
) : Exception(message)

/**
 * Shared HTTP fetcher for iCal feeds. A misconfigured URL (wrong listing id, missing secret,
 * a calendar page copied from the browser instead of the export link) typically answers with a
 * 404 or an HTML page, which would otherwise parse to zero events and be indistinguishable from
 * a legitimately empty calendar — so both cases throw instead of returning a non-feed body.
 *
 * Transient upstream statuses (5xx, 429, 408) get one in-run retry before throwing, so a brief
 * maintenance blip doesn't flip a healthy integration to a failed state until the next
 * scheduled sync.
 *
 * The URL is user-supplied, so every request hop (first request, retry and each redirect) must
 * pass [IcalUrlGuard] first; [resolveHost] is injectable so tests can stub DNS.
 */
class IcalFeedFetcher(
    private val clientFactory: () -> HttpClient = ::defaultHttpClient,
    private val transientRetryDelayMs: Long = TRANSIENT_RETRY_DELAY_MS,
    private val resolveHost: (String) -> List<InetAddress> = IcalUrlGuard::resolveSystem,
) {
    suspend fun fetch(url: String): String =
        try {
            fetchFeed(url)
        } catch (e: CalendarFeedException) {
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The feed URL is a bearer capability (Airbnb `?s=`, Booking.com `?t=`), and Ktor's
            // client exceptions embed it verbatim (e.g. "Request timeout has expired [url=...]").
            // Callers log and persist whatever escapes here, so surface only the host and the
            // error class, and drop the cause so the URL can't resurface in a "Caused by" trace.
            throw CalendarFeedException(
                "calendar.feed.request_failed host=${redactedHost(url)} error_class=${e::class.simpleName}",
            )
        }

    private suspend fun fetchFeed(url: String): String =
        withContext(Dispatchers.IO) {
            clientFactory().use { client ->
                // Redirects are followed by hand so each hop is guarded; a client that follows
                // them itself would let a redirect reach an internal address unchecked.
                check(client.pluginOrNull(HttpRedirect) == null) {
                    "IcalFeedFetcher requires an HttpClient with followRedirects = false"
                }
                var response = getFollowingRedirects(client, url)
                if (response.status.isTransient()) {
                    logger.info { "calendar.feed.retry status=${response.status.value}" }
                    delay(transientRetryDelayMs)
                    response = getFollowingRedirects(client, url)
                }
                val body = response.bodyAsText()
                val contentType = response.contentType()?.toString() ?: "unknown"

                if (!response.status.isSuccess()) {
                    throw CalendarFeedException(
                        "calendar.feed.http_error status=${response.status.value} " +
                            "content_type=$contentType body_bytes=${body.length}",
                    )
                }
                // RFC 5545 property names are case-insensitive, so match the marker accordingly.
                if (!body.contains(VCALENDAR_MARKER, ignoreCase = true)) {
                    throw CalendarFeedException(
                        "calendar.feed.not_ical status=${response.status.value} " +
                            "content_type=$contentType body_bytes=${body.length} " +
                            "body_preview=${preview(body)}",
                    )
                }

                logger.debug {
                    "calendar.feed.fetched status=${response.status.value} " +
                        "content_type=$contentType body_bytes=${body.length}"
                }
                body
            }
        }

    /**
     * GETs [url], following redirects by hand with the semantics of Ktor's HttpRedirect, which
     * used to follow them: GET is kept on 301/302/303/307/308, Location resolves against the
     * current URL with its query cleared, a missing Location or an https -> http downgrade returns
     * the redirect response as is, and the cap matches HttpSend's default of 20 sends per call.
     */
    private suspend fun getFollowingRedirects(
        client: HttpClient,
        url: String,
    ): HttpResponse {
        var target = Url(url)
        var redirects = 0
        while (true) {
            val response = guardedGet(client, target)
            if (response.status.value !in REDIRECT_STATUSES) return response
            val location = response.headers[HttpHeaders.Location] ?: return response
            val next =
                URLBuilder(target)
                    .apply {
                        parameters.clear()
                        takeFrom(location)
                    }.build()
            if (target.protocol.isSecure() && !next.protocol.isSecure()) return response
            if (redirects == MAX_REDIRECTS) {
                // Same failure HttpSend raised when Ktor followed the redirects itself.
                throw SendCountExceedException(
                    "Max send count ${MAX_REDIRECTS + 1} exceeded. Consider increasing the property " +
                        "maxSendCount if more is required.",
                )
            }
            redirects++
            target = next
        }
    }

    /**
     * Sends one GET to [target] after [IcalUrlGuard] has validated it. https keeps its hostname:
     * TLS verifies the certificate against it, so a second DNS answer pointing elsewhere can't be
     * served. Plain http has no such check, so it is pinned to the validated address, keeping the
     * original Host header for virtual hosting.
     */
    private suspend fun guardedGet(
        client: HttpClient,
        target: Url,
    ): HttpResponse {
        val address = IcalUrlGuard.requirePublicTarget(target, resolveHost)
        if (target.protocol.isSecure()) return client.get(target)

        val pinned = URLBuilder(target).apply { host = IcalUrlGuard.urlHost(address) }.build()
        val hostHeader = if (target.port == target.protocol.defaultPort) target.host else "${target.host}:${target.port}"
        return client.get(pinned) { headers[HttpHeaders.Host] = hostHeader }
    }

    private fun HttpStatusCode.isTransient(): Boolean =
        value >= 500 ||
            this == HttpStatusCode.TooManyRequests ||
            this == HttpStatusCode.RequestTimeout

    private fun preview(body: String): String =
        body
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(PREVIEW_MAX_CHARS)

    private fun redactedHost(url: String): String = runCatching { URI(url).host ?: "unknown" }.getOrDefault("invalid")

    companion object {
        private const val VCALENDAR_MARKER = "BEGIN:VCALENDAR"
        private const val PREVIEW_MAX_CHARS = 120
        private const val TRANSIENT_RETRY_DELAY_MS = 2_000L

        /** HttpSend's default maxSendCount (20) minus the original request. */
        internal const val MAX_REDIRECTS = 19
        private val REDIRECT_STATUSES =
            setOf(
                HttpStatusCode.MovedPermanently.value,
                HttpStatusCode.Found.value,
                HttpStatusCode.SeeOther.value,
                HttpStatusCode.TemporaryRedirect.value,
                HttpStatusCode.PermanentRedirect.value,
            )

        private fun defaultHttpClient(): HttpClient =
            HttpClient(CIO) {
                followRedirects = false
                install(HttpTimeout) {
                    requestTimeoutMillis = 30_000
                    connectTimeoutMillis = 10_000
                    socketTimeoutMillis = 30_000
                }
            }
    }
}
