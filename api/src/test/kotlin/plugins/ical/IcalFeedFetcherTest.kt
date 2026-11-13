package eu.kasify.plugins.ical

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.ConnectTimeoutException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.net.InetAddress
import java.net.UnknownHostException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IcalFeedFetcherTest {
    private val validIcal =
        """
        BEGIN:VCALENDAR
        PRODID:-//Airbnb Inc//Hosting Calendar 1.0//EN
        VERSION:2.0
        BEGIN:VEVENT
        DTSTART;VALUE=DATE:20260714
        DTEND;VALUE=DATE:20260721
        UID:test@airbnb.com
        END:VEVENT
        END:VCALENDAR
        """.trimIndent()

    private val publicAddress: InetAddress = InetAddress.getByName("93.184.216.34")

    // Tests never touch real DNS: by default every host resolves to a public address.
    private val resolvePublic: (String) -> List<InetAddress> = { listOf(publicAddress) }

    private fun mockClient(handler: MockRequestHandler): HttpClient = HttpClient(MockEngine(handler)) { followRedirects = false }

    private fun fetcher(
        resolveHost: (String) -> List<InetAddress> = resolvePublic,
        handler: MockRequestHandler,
    ): IcalFeedFetcher =
        IcalFeedFetcher(
            clientFactory = { mockClient(handler) },
            transientRetryDelayMs = 0,
            resolveHost = resolveHost,
        )

    private fun fetcherRespondingWith(
        status: HttpStatusCode,
        body: String,
        contentType: String = "text/calendar",
    ): IcalFeedFetcher =
        fetcher {
            respond(
                content = body,
                status = status,
                headers = headersOf(HttpHeaders.ContentType, contentType),
            )
        }

    @Test
    fun `should return body for a valid iCal feed`() =
        runBlocking {
            val fetcher = fetcherRespondingWith(HttpStatusCode.OK, validIcal)

            val body = fetcher.fetch("https://www.airbnb.com/calendar/ical/123.ics?s=secret")

            assertEquals(validIcal, body)
        }

    @Test
    fun `should throw on non-2xx status instead of returning the error body`() =
        runBlocking {
            // A wrong listing id or secret makes Airbnb answer 404 — previously this body was
            // parsed as an empty feed and the sync was marked successful.
            val fetcher = fetcherRespondingWith(HttpStatusCode.NotFound, "<html>Not found</html>", "text/html")

            val exception =
                assertFailsWith<CalendarFeedException> {
                    fetcher.fetch("https://www.airbnb.com/calendar/ical/999.ics?s=wrong")
                }
            assertTrue(exception.message!!.contains("status=404"), "Message should include the HTTP status")
            assertTrue(exception.message!!.contains("text/html"), "Message should include the content type")
        }

    @Test
    fun `should throw when a 200 response is not an iCal document`() =
        runBlocking {
            // e.g. a calendar page URL copied from the browser instead of the iCal export link.
            val fetcher = fetcherRespondingWith(HttpStatusCode.OK, "<html><body>Login required</body></html>", "text/html")

            val exception =
                assertFailsWith<CalendarFeedException> {
                    fetcher.fetch("https://www.airbnb.es/multicalendar/12345")
                }
            assertTrue(exception.message!!.contains("not_ical"), "Message should flag a non-iCal body")
            assertTrue(exception.message!!.contains("body_preview="), "Message should include a body preview")
        }

    @Test
    fun `should keep the last_error message under the 1000-char column budget`() =
        runBlocking {
            val hugeHtml = "<html>" + "x".repeat(50_000) + "</html>"
            val fetcher = fetcherRespondingWith(HttpStatusCode.OK, hugeHtml, "text/html")

            val exception =
                assertFailsWith<CalendarFeedException> {
                    fetcher.fetch("https://www.airbnb.es/some-page")
                }
            assertTrue(
                exception.message!!.length < 1000,
                "Message should stay well under the last_error truncation limit",
            )
        }

    @Test
    fun `should accept a lowercase vcalendar marker`() =
        runBlocking {
            // RFC 5545 property names are case-insensitive.
            val fetcher = fetcherRespondingWith(HttpStatusCode.OK, validIcal.lowercase())

            val body = fetcher.fetch("https://www.airbnb.com/calendar/ical/123.ics?s=secret")

            assertEquals(validIcal.lowercase(), body)
        }

    @Test
    fun `should retry once on a transient status and return the recovered body`() =
        runBlocking {
            var requests = 0
            val fetcher =
                fetcher {
                    requests++
                    if (requests == 1) {
                        respond(
                            content = "<html>Maintenance</html>",
                            status = HttpStatusCode.ServiceUnavailable,
                            headers = headersOf(HttpHeaders.ContentType, "text/html"),
                        )
                    } else {
                        respond(
                            content = validIcal,
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "text/calendar"),
                        )
                    }
                }

            val body = fetcher.fetch("https://www.airbnb.com/calendar/ical/123.ics?s=secret")

            assertEquals(validIcal, body)
            assertEquals(2, requests, "A transient status should be retried exactly once")
        }

    @Test
    fun `should not retry a non-transient status`() =
        runBlocking {
            var requests = 0
            val fetcher =
                fetcher {
                    requests++
                    respond(
                        content = "<html>Not found</html>",
                        status = HttpStatusCode.NotFound,
                        headers = headersOf(HttpHeaders.ContentType, "text/html"),
                    )
                }

            assertFailsWith<CalendarFeedException> {
                fetcher.fetch("https://www.airbnb.com/calendar/ical/999.ics?s=wrong")
            }
            assertEquals(1, requests, "A misconfigured-URL status should not be retried")
        }

    // --- SSRF guard -------------------------------------------------------------------------

    private fun redirect(location: String) = headersOf(HttpHeaders.Location, location)

    @Test
    fun `should refuse a host that resolves to a non-public address without sending a request`() =
        runBlocking {
            var requests = 0
            val fetcher =
                fetcher(resolveHost = { listOf(InetAddress.getByName("169.254.169.254")) }) {
                    requests++
                    respond(validIcal)
                }

            val exception =
                assertFailsWith<CalendarFeedException> {
                    fetcher.fetch("http://metadata.attacker.example/latest/meta-data/")
                }
            assertEquals(IcalUrlGuard.URL_NOT_ALLOWED_MESSAGE, exception.message)
            assertEquals(0, requests, "A refused target must not be contacted")
        }

    @Test
    fun `should refuse a host when any resolved address is non-public`() =
        runBlocking {
            var requests = 0
            val fetcher =
                fetcher(resolveHost = { listOf(publicAddress, InetAddress.getByName("10.0.0.7")) }) {
                    requests++
                    respond(validIcal)
                }

            assertFailsWith<CalendarFeedException> {
                fetcher.fetch("https://mixed.attacker.example/feed.ics")
            }
            assertEquals(0, requests)
        }

    @Test
    fun `should report an unresolvable host with the same generic message`() =
        runBlocking {
            val fetcher = fetcher(resolveHost = { throw UnknownHostException(it) }) { respond(validIcal) }

            val exception =
                assertFailsWith<CalendarFeedException> {
                    fetcher.fetch("https://internal-only.corp/feed.ics")
                }
            assertEquals(IcalUrlGuard.URL_NOT_ALLOWED_MESSAGE, exception.message)
        }

    @Test
    fun `should refuse non-public IP literals and localhost before resolving`() =
        runBlocking {
            var resolutions = 0
            val fetcher =
                fetcher(resolveHost = {
                    resolutions++
                    resolvePublic(it)
                }) { respond(validIcal) }

            listOf(
                "http://127.0.0.1:8080/",
                "http://2130706433/",
                "http://0x7f.0.0.1/",
                "http://[::1]/",
                "http://localhost/",
                "ftp://www.airbnb.com/feed.ics",
            ).forEach { url ->
                val exception = assertFailsWith<CalendarFeedException>(url) { fetcher.fetch(url) }
                assertEquals(IcalUrlGuard.URL_NOT_ALLOWED_MESSAGE, exception.message, url)
            }
            assertEquals(0, resolutions)
        }

    @Test
    fun `should re-check the target on the transient retry`() =
        runBlocking {
            // DNS answers public for the first request and internal for the retry.
            var resolutions = 0
            var requests = 0
            val fetcher =
                fetcher(resolveHost = {
                    resolutions++
                    if (resolutions == 1) listOf(publicAddress) else listOf(InetAddress.getByName("127.0.0.1"))
                }) {
                    requests++
                    respond("<html>Maintenance</html>", HttpStatusCode.ServiceUnavailable)
                }

            assertFailsWith<CalendarFeedException> {
                fetcher.fetch("https://www.airbnb.com/calendar/ical/123.ics?s=secret")
            }
            assertEquals(2, resolutions)
            assertEquals(1, requests, "The retry must not reach the now-internal address")
        }

    @Test
    fun `should refuse a redirect to a non-public address`() =
        runBlocking {
            val requestedUrls = mutableListOf<String>()
            val fetcher =
                fetcher { request ->
                    requestedUrls += request.url.toString()
                    respond("", HttpStatusCode.Found, redirect("http://169.254.169.254/latest/meta-data/"))
                }

            val exception =
                assertFailsWith<CalendarFeedException> {
                    fetcher.fetch("http://feeds.example.com/calendar/ical/123.ics?s=secret")
                }
            assertEquals(IcalUrlGuard.URL_NOT_ALLOWED_MESSAGE, exception.message)
            assertEquals(listOf("http://93.184.216.34/calendar/ical/123.ics?s=secret"), requestedUrls)
        }

    @Test
    fun `should refuse a redirect to a hostname that resolves internally`() =
        runBlocking {
            var requests = 0
            val fetcher =
                fetcher(resolveHost = { host ->
                    if (host == "internal.attacker.example") listOf(InetAddress.getByName("192.168.1.1")) else listOf(publicAddress)
                }) {
                    requests++
                    respond("", HttpStatusCode.MovedPermanently, redirect("https://internal.attacker.example/admin"))
                }

            val exception =
                assertFailsWith<CalendarFeedException> {
                    fetcher.fetch("https://www.airbnb.com/calendar/ical/123.ics?s=secret")
                }
            assertEquals(IcalUrlGuard.URL_NOT_ALLOWED_MESSAGE, exception.message)
            assertEquals(1, requests)
        }

    private fun redirectChainFetcher(
        hops: Int,
        onRequest: () -> Unit,
    ): IcalFeedFetcher =
        fetcher { request ->
            onRequest()
            // Each hop lives on a different public host: https://hN.example.com/hop/N
            val hop =
                request.url.encodedPath
                    .removePrefix("/hop/")
                    .toInt()
            if (hop < hops) {
                respond("", HttpStatusCode.Found, redirect("https://h${hop + 1}.example.com/hop/${hop + 1}"))
            } else {
                respond(validIcal, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "text/calendar"))
            }
        }

    @Test
    fun `should follow a chain of 19 redirects between public hosts`() =
        runBlocking {
            var requests = 0
            val fetcher = redirectChainFetcher(hops = IcalFeedFetcher.MAX_REDIRECTS) { requests++ }

            val body = fetcher.fetch("https://h0.example.com/hop/0")

            assertEquals(validIcal, body)
            assertEquals(20, requests, "19 redirects means 20 requests, as Ktor's HttpSend allowed")
        }

    @Test
    fun `should fail on the 20th redirect like Ktor's HttpSend did`() =
        runBlocking {
            var requests = 0
            val fetcher = redirectChainFetcher(hops = IcalFeedFetcher.MAX_REDIRECTS + 1) { requests++ }

            val exception =
                assertFailsWith<CalendarFeedException> {
                    fetcher.fetch("https://h0.example.com/hop/0")
                }
            assertTrue(exception.message!!.contains("error_class=SendCountExceedException"), exception.message)
            assertEquals(20, requests, "The 20th redirect must not be sent")
        }

    @Test
    fun `should keep the redirect response on an https to http downgrade`() =
        runBlocking {
            var requests = 0
            val fetcher =
                fetcher {
                    requests++
                    respond("", HttpStatusCode.Found, redirect("http://www.airbnb.com/calendar/ical/123.ics"))
                }

            val exception =
                assertFailsWith<CalendarFeedException> {
                    fetcher.fetch("https://www.airbnb.com/calendar/ical/123.ics?s=secret")
                }
            assertTrue(exception.message!!.contains("status=302"), exception.message)
            assertEquals(1, requests)
        }

    @Test
    fun `should pin plain http to the validated address and keep the Host header`() =
        runBlocking {
            val seen = mutableListOf<Pair<String, String?>>()
            val fetcher =
                fetcher { request ->
                    seen += request.url.toString() to request.headers[HttpHeaders.Host]
                    if (request.url.encodedPath == "/start") {
                        // Relative Location: must resolve against the original hostname, not the pinned IP.
                        respond("", HttpStatusCode.Found, redirect("/feed.ics?s=next"))
                    } else {
                        respond(validIcal, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "text/calendar"))
                    }
                }

            val body = fetcher.fetch("http://feeds.example.com:8080/start?s=first")

            assertEquals(validIcal, body)
            assertEquals(
                listOf<Pair<String, String?>>(
                    "http://93.184.216.34:8080/start?s=first" to "feeds.example.com:8080",
                    "http://93.184.216.34:8080/feed.ics?s=next" to "feeds.example.com:8080",
                ),
                seen,
            )
        }

    @Test
    fun `should pin plain http to a bracketed IPv6 address on the default port`() =
        runBlocking {
            var seenHost: String? = null
            var seenHostHeader: String? = null
            val fetcher =
                fetcher(resolveHost = { listOf(InetAddress.getByName("2606:4700:4700::1111")) }) { request ->
                    seenHost = request.url.host
                    seenHostHeader = request.headers[HttpHeaders.Host]
                    respond(validIcal)
                }

            fetcher.fetch("http://feeds.example.com/feed.ics")

            assertEquals("[2606:4700:4700:0:0:0:0:1111]", seenHost)
            assertEquals("feeds.example.com", seenHostHeader)
        }

    @Test
    fun `should keep the hostname for https so TLS verifies it`() =
        runBlocking {
            val url = "https://admin.booking.com/hotel/hoteladmin/ical.html?t=a1b2%2Bc3&lang=es"
            var seenUrl: String? = null
            var seenHostHeader: String? = "unset"
            val fetcher =
                fetcher { request ->
                    seenUrl = request.url.toString()
                    seenHostHeader = request.headers[HttpHeaders.Host]
                    respond(validIcal)
                }

            fetcher.fetch(url)

            assertEquals(url, seenUrl, "https must be requested by hostname, URL unchanged")
            assertNull(seenHostHeader, "https must not override the Host header")
        }

    @Test
    fun `should refuse a client that follows redirects itself`() =
        runBlocking<Unit> {
            val fetcher =
                IcalFeedFetcher(
                    clientFactory = { HttpClient(MockEngine { respond(validIcal) }) },
                    resolveHost = resolvePublic,
                )

            val exception =
                assertFailsWith<CalendarFeedException> {
                    fetcher.fetch("https://www.airbnb.com/calendar/ical/123.ics?s=secret")
                }
            assertTrue(exception.message!!.contains("error_class=IllegalStateException"), exception.message)
        }

    @Test
    fun `should not leak the secret feed URL when the request times out`() =
        runBlocking {
            // Ktor's HttpRequestTimeoutException message is "Request timeout has expired [url=<full url>, ...]".
            val fetcher =
                IcalFeedFetcher(
                    clientFactory = {
                        HttpClient(
                            MockEngine {
                                delay(10_000)
                                respond(
                                    content = validIcal,
                                    status = HttpStatusCode.OK,
                                    headers = headersOf(HttpHeaders.ContentType, "text/calendar"),
                                )
                            },
                        ) {
                            followRedirects = false
                            install(HttpTimeout) { requestTimeoutMillis = 50 }
                        }
                    },
                    transientRetryDelayMs = 0,
                    resolveHost = resolvePublic,
                )

            val exception =
                assertFailsWith<CalendarFeedException> {
                    fetcher.fetch("https://www.airbnb.com/calendar/ical/123.ics?s=$FEED_SECRET")
                }

            assertRedacted(exception, host = "www.airbnb.com", errorClass = "HttpRequestTimeoutException")
        }

    @Test
    fun `should not leak the secret feed URL when the connection times out`() =
        runBlocking {
            // Mirrors what the CIO engine throws: "Connect timeout has expired [url=<full url>, ...]".
            val fetcher =
                IcalFeedFetcher(
                    clientFactory = { mockClient { request -> throw ConnectTimeoutException(request) } },
                    transientRetryDelayMs = 0,
                    resolveHost = resolvePublic,
                )

            val exception =
                assertFailsWith<CalendarFeedException> {
                    fetcher.fetch("https://ical.booking.com/v1/export?t=$FEED_SECRET")
                }

            assertRedacted(exception, host = "ical.booking.com", errorClass = "ConnectTimeoutException")
        }

    /**
     * The job and the plugins log the exception with its stack trace and persist its message as
     * last_error, so neither the message nor any cause may carry the feed URL's secret.
     */
    private fun assertRedacted(
        exception: CalendarFeedException,
        host: String,
        errorClass: String,
    ) {
        val message = exception.message!!
        assertTrue(message.contains("host=$host"), "Message should name the host: $message")
        assertTrue(message.contains("error_class=$errorClass"), "Message should name the error class: $message")
        assertNull(exception.cause, "The original exception embeds the URL and must not be chained")
        assertFalse(
            exception.stackTraceToString().contains(FEED_SECRET),
            "The feed secret must not appear anywhere in the logged stack trace",
        )
    }

    private companion object {
        const val FEED_SECRET = "s3cr3t-feed-token"
    }
}
