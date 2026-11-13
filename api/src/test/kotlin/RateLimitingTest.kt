package eu.kasify

import eu.kasify.security.AuthMethod
import eu.kasify.security.KasifyUserPrincipal
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.bearer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.Uuid

/**
 * Self-contained rate-limit tests. We register a small set of routes that
 * mirror the wrapping patterns in production (`maybeRateLimit` for IP/code
 * providers, the global limiter for authenticated routes) without needing
 * the full route/DB stack.
 */
class RateLimitingTest {
    private val userA = Uuid.parse("aaaaaaaa-0000-0000-0000-000000000001")
    private val userB = Uuid.parse("aaaaaaaa-0000-0000-0000-000000000002")
    private val accountA = Uuid.parse("aaaaaaaa-0000-0000-0000-000000000aaa")
    private val tokenA = Uuid.parse("bbbbbbbb-0000-0000-0000-000000000001")
    private val tokenB = Uuid.parse("bbbbbbbb-0000-0000-0000-000000000002")

    /**
     * Tight test limits so we can exhaust buckets in a few requests. The
     * production defaults (120/300/etc.) would make tests painfully slow.
     */
    private val testLimits =
        RateLimitsConfig(
            pat = LimitConfig(limit = 2, period = 1.minutes),
            user = LimitConfig(limit = 3, period = 1.minutes),
            ipCheckin = LimitConfig(limit = 4, period = 1.minutes),
            checkinCode = LimitConfig(limit = 2, period = 1.minutes),
            ipGeo = LimitConfig(limit = 3, period = 1.minutes),
            ipOpenApi = LimitConfig(limit = 2, period = 1.minutes),
            ipTelegram = LimitConfig(limit = 5, period = 1.minutes),
        )

    /**
     * Configures a minimal test app:
     *  - Bearer auth that maps `token-user-a` / `token-user-b` to JWT
     *    principals and `token-pat-a` / `token-pat-b` to PAT principals.
     *  - `/protected` — authenticated, hits the global principal limiter.
     *  - `/health` — unauthenticated, on the skip list, never throttled.
     *  - `/checkin/{code}` — public, layered IpCheckin + CheckinCode.
     *  - `/geo/countries` — public, IpGeo only.
     *  - `/openapi.json` — public, IpOpenApi only.
     */
    private fun Application.testApp() {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
        install(Authentication) {
            bearer(AUTH_BEARER) {
                authenticate { credential ->
                    when (credential.token) {
                        "token-user-a" -> KasifyUserPrincipal(userA, accountA, authMethod = AuthMethod.Jwt)
                        "token-user-b" -> KasifyUserPrincipal(userB, accountA, authMethod = AuthMethod.Jwt)
                        "token-pat-a" ->
                            KasifyUserPrincipal(userA, accountA, authMethod = AuthMethod.ApiToken, tokenId = tokenA)
                        "token-pat-b" ->
                            KasifyUserPrincipal(userA, accountA, authMethod = AuthMethod.ApiToken, tokenId = tokenB)
                        else -> null
                    }
                }
            }
        }
        configureStatusPages()
        configureRateLimiting(testLimits)

        routing {
            get("/health") { call.respondText("ok") }
            // Mirror the production wiring: auth wraps rate-limit so the
            // principal is set when the rate-limit interceptor inspects it.
            authenticate(AUTH_BEARER) {
                maybeRateLimit(RateLimitProviders.Principal) {
                    get("/protected") { call.respond(HttpStatusCode.OK, "ok") }
                }
            }
            maybeRateLimit(RateLimitProviders.IpCheckin) {
                maybeRateLimit(RateLimitProviders.CheckinCode) {
                    get("/checkin/{code}") { call.respondText("ok") }
                }
            }
            maybeRateLimit(RateLimitProviders.IpGeo) {
                get("/geo/countries") { call.respondText("ok") }
            }
            maybeRateLimit(RateLimitProviders.IpOpenApi) {
                get("/openapi.json") { call.respondText("{}") }
            }
        }
    }

    @Test
    fun `JWT user is throttled after exhausting their bucket`() =
        testApplication {
            application { testApp() }
            // user limit = 3
            repeat(3) { i ->
                val res = client.get("/protected") { bearerAuth("token-user-a") }
                assertEquals(HttpStatusCode.OK, res.status, "request $i should pass")
            }
            val throttled = client.get("/protected") { bearerAuth("token-user-a") }
            assertEquals(HttpStatusCode.TooManyRequests, throttled.status)
        }

    @Test
    fun `Two JWT users have independent buckets`() =
        testApplication {
            application { testApp() }
            // user A exhausts (3 requests, then 1 throttled)
            repeat(3) { client.get("/protected") { bearerAuth("token-user-a") } }
            val aThrottled = client.get("/protected") { bearerAuth("token-user-a") }
            assertEquals(HttpStatusCode.TooManyRequests, aThrottled.status)

            // user B is unaffected — full bucket
            val bRes = client.get("/protected") { bearerAuth("token-user-b") }
            assertEquals(HttpStatusCode.OK, bRes.status)
        }

    @Test
    fun `PAT and JWT for the same user have independent buckets`() =
        testApplication {
            application { testApp() }
            // PAT limit = 2, fully drain it
            repeat(2) {
                val res = client.get("/protected") { bearerAuth("token-pat-a") }
                assertEquals(HttpStatusCode.OK, res.status)
            }
            val patThrottled = client.get("/protected") { bearerAuth("token-pat-a") }
            assertEquals(HttpStatusCode.TooManyRequests, patThrottled.status)

            // The same user via JWT is keyed differently — fresh bucket
            val jwtRes = client.get("/protected") { bearerAuth("token-user-a") }
            assertEquals(HttpStatusCode.OK, jwtRes.status)
        }

    @Test
    fun `PAT bucket is smaller than JWT bucket per the dynamic limit`() =
        testApplication {
            application { testApp() }
            // PAT exhausts at 2, JWT exhausts at 3 — different limits despite same code path
            repeat(2) { client.get("/protected") { bearerAuth("token-pat-a") } }
            assertEquals(
                HttpStatusCode.TooManyRequests,
                client.get("/protected") { bearerAuth("token-pat-a") }.status,
            )
            repeat(3) { client.get("/protected") { bearerAuth("token-user-b") } }
            assertEquals(
                HttpStatusCode.TooManyRequests,
                client.get("/protected") { bearerAuth("token-user-b") }.status,
            )
        }

    @Test
    fun `Two distinct PATs for the same user get independent buckets`() =
        testApplication {
            application { testApp() }
            repeat(2) { client.get("/protected") { bearerAuth("token-pat-a") } }
            val aThrottled = client.get("/protected") { bearerAuth("token-pat-a") }
            assertEquals(HttpStatusCode.TooManyRequests, aThrottled.status)

            // Same user, different token — separate bucket
            val bRes = client.get("/protected") { bearerAuth("token-pat-b") }
            assertEquals(HttpStatusCode.OK, bRes.status)
        }

    @Test
    fun `Health endpoint is exempt and never throttles`() =
        testApplication {
            application { testApp() }
            // Way over any limit — must always 200
            repeat(20) {
                val res = client.get("/health")
                assertEquals(HttpStatusCode.OK, res.status, "iteration $it")
            }
        }

    @Test
    fun `Checkin code-specific limit fires before IP limit`() =
        testApplication {
            application { testApp() }
            // checkinCode = 2, so the third request to the same code is throttled
            repeat(2) {
                val res = client.get("/checkin/code-1")
                assertEquals(HttpStatusCode.OK, res.status)
            }
            val throttled = client.get("/checkin/code-1")
            assertEquals(HttpStatusCode.TooManyRequests, throttled.status)
        }

    @Test
    fun `Different checkin codes have independent code buckets but share IP bucket`() =
        testApplication {
            application { testApp() }
            // Each code has its own CheckinCode bucket (limit 2)
            // The IP-wide IpCheckin bucket has limit 4 (= 2 codes x 2 each)
            assertEquals(HttpStatusCode.OK, client.get("/checkin/code-1").status)
            assertEquals(HttpStatusCode.OK, client.get("/checkin/code-1").status)
            assertEquals(HttpStatusCode.OK, client.get("/checkin/code-2").status)
            assertEquals(HttpStatusCode.OK, client.get("/checkin/code-2").status)

            // 5th request from same IP — IpCheckin (limit 4) exhausts regardless of code
            val res = client.get("/checkin/code-3")
            assertEquals(HttpStatusCode.TooManyRequests, res.status)
        }

    @Test
    fun `Geo endpoint throttles per IP after the configured limit`() =
        testApplication {
            application { testApp() }
            repeat(3) {
                assertEquals(HttpStatusCode.OK, client.get("/geo/countries").status)
            }
            val res = client.get("/geo/countries")
            assertEquals(HttpStatusCode.TooManyRequests, res.status)
        }

    @Test
    fun `OpenAPI endpoint throttles per IP after the configured limit`() =
        testApplication {
            application { testApp() }
            repeat(2) {
                assertEquals(HttpStatusCode.OK, client.get("/openapi.json").status)
            }
            assertEquals(
                HttpStatusCode.TooManyRequests,
                client.get("/openapi.json").status,
            )
        }

    @Test
    fun `429 response carries the rate_limit_exceeded error code`() =
        testApplication {
            application { testApp() }
            repeat(3) { client.get("/protected") { bearerAuth("token-user-a") } }
            val throttled = client.get("/protected") { bearerAuth("token-user-a") }
            assertEquals(HttpStatusCode.TooManyRequests, throttled.status)
            val body = Json.parseToJsonElement(throttled.bodyAsText()).jsonObject
            assertEquals("rate_limit.exceeded", body["code"]?.jsonPrimitive?.content)
            assertNotNull(body["message"]?.jsonPrimitive?.content)
        }

    @Test
    fun `429 response carries Retry-After header`() =
        testApplication {
            application { testApp() }
            repeat(3) { client.get("/protected") { bearerAuth("token-user-a") } }
            val throttled = client.get("/protected") { bearerAuth("token-user-a") }
            assertEquals(HttpStatusCode.TooManyRequests, throttled.status)
            val retryAfter = throttled.headers["Retry-After"]
            assertNotNull(retryAfter, "Retry-After header should be present on 429")
            // Sanity: it should parse as a positive integer (seconds)
            val seconds = retryAfter.toLongOrNull()
            assertNotNull(seconds, "Retry-After should be a number of seconds")
            assertTrue(seconds > 0, "Retry-After should be positive, was $seconds")
        }

    @Test
    fun `Successful response carries X-RateLimit-Limit and X-RateLimit-Remaining headers`() =
        testApplication {
            application { testApp() }
            val res = client.get("/protected") { bearerAuth("token-user-a") }
            assertEquals(HttpStatusCode.OK, res.status)
            val limit = res.headers["X-RateLimit-Limit"]
            val remaining = res.headers["X-RateLimit-Remaining"]
            assertEquals("3", limit, "Limit header should reflect the user bucket size")
            // First request consumes one token; remaining should be 2
            assertEquals("2", remaining)
        }

    @Test
    fun `Public routes are not affected by the principal limiter`() =
        testApplication {
            application { testApp() }
            // Public routes have no auth and no Principal wrap, so even
            // many requests don't 429 from the principal bucket — only
            // from the route-scoped IP/code limits.
            assertEquals(HttpStatusCode.OK, client.get("/checkin/x-1").status)
            assertEquals(HttpStatusCode.OK, client.get("/checkin/x-2").status)
            assertEquals(HttpStatusCode.OK, client.get("/geo/countries").status)
        }
}
