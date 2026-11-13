package eu.kasify

import eu.kasify.security.AuthMethod
import eu.kasify.security.KasifyUserPrincipal
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.application.pluginOrNull
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.plugins.origin
import io.ktor.server.plugins.ratelimit.RateLimit
import io.ktor.server.plugins.ratelimit.RateLimitName
import io.ktor.server.plugins.ratelimit.RateLimiter
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.request.uri
import io.ktor.server.routing.Route
import io.ktor.server.routing.application
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

private val logger = KotlinLogging.logger {}

/**
 * Named rate-limit providers. Keyed by different caller identities; route
 * code wraps with `maybeRateLimit(RateLimitProviders.X) { ... }` (or via
 * the [authenticatedAndRateLimited] helper for the [Principal] provider).
 *
 * Why not use Ktor's `global { }` for [Principal]? The application-level
 * interceptor runs *before* route resolution, so `call.principal` is null
 * at request-key time and every authenticated request would fall to the
 * IP fallback. Per-route wrapping (inside `authenticate { }`) ensures the
 * principal is set when the bucket key is computed.
 */
object RateLimitProviders {
    /**
     * Authenticated requests. Keyed by token id (PAT) or user id (JWT) so
     * each caller has its own bucket. Bucket size depends on auth method:
     * PATs get a stricter limit than dashboard JWTs.
     *
     * Apply via [authenticatedAndRateLimited] — must run after the auth
     * interceptor has set the principal.
     */
    val Principal = RateLimitName("principal")

    /** Tighter per-IP limit for the unauthenticated `/checkin/...` surface. */
    val IpCheckin = RateLimitName("ip-checkin")

    /** Per booking-code limit on `/checkin/{code}/...` — kills code brute-force. */
    val CheckinCode = RateLimitName("checkin-code")

    /** Per-IP limit for `/geo/...` lookups. */
    val IpGeo = RateLimitName("ip-geo")

    /** Per-IP limit for `/openapi.{json,yaml}` fetches. */
    val IpOpenApi = RateLimitName("ip-openapi")

    /** Per-IP limit for the public `/telegram/webhook` endpoint. */
    val IpTelegram = RateLimitName("ip-telegram")
}

/**
 * Indicates whether rate limiting is active. When `false`:
 *  - the [RateLimit] plugin is not installed,
 *  - [maybeRateLimit] and [authenticatedAndRateLimited] route wraps become
 *    no-ops (the latter still runs `authenticate` so auth still applies).
 *
 * Default: enforce (true). Set `RATE_LIMIT_ENFORCE=false` for shadow rollout
 * — the registry still logs the configured limits at startup, but no traffic
 * is throttled.
 */
val rateLimitEnforce: Boolean by lazy {
    System.getenv("RATE_LIMIT_ENFORCE")?.lowercase() != "false"
}

data class LimitConfig(
    val limit: Int,
    val period: Duration,
)

/** Bundle of every limit applied by [configureRateLimiting]. Tests construct
 *  small instances; the env-driven factory below builds the production one. */
data class RateLimitsConfig(
    val pat: LimitConfig,
    val user: LimitConfig,
    val ipCheckin: LimitConfig,
    val checkinCode: LimitConfig,
    val ipGeo: LimitConfig,
    val ipOpenApi: LimitConfig,
    val ipTelegram: LimitConfig,
) {
    companion object {
        fun fromEnv(): RateLimitsConfig =
            RateLimitsConfig(
                pat = LimitConfig(envInt("RATE_LIMIT_PAT_PER_MIN", 100), 1.minutes),
                user = LimitConfig(envInt("RATE_LIMIT_USER_PER_MIN", 100), 1.minutes),
                ipCheckin = LimitConfig(envInt("RATE_LIMIT_IP_CHECKIN_PER_MIN", 50), 1.minutes),
                checkinCode = LimitConfig(envInt("RATE_LIMIT_CHECKIN_CODE_PER_MIN", 50), 1.minutes),
                ipGeo = LimitConfig(envInt("RATE_LIMIT_IP_GEO_PER_MIN", 50), 1.minutes),
                ipOpenApi = LimitConfig(envInt("RATE_LIMIT_IP_OPENAPI_PER_MIN", 10), 1.minutes),
                ipTelegram = LimitConfig(envInt("RATE_LIMIT_IP_TELEGRAM_PER_MIN", 100), 1.minutes),
            )
    }
}

private fun envInt(
    name: String,
    default: Int,
): Int = System.getenv(name)?.toIntOrNull() ?: default

fun Application.configureRateLimiting(limits: RateLimitsConfig = RateLimitsConfig.fromEnv()) {
    logger.info { "Rate limiting registered (enforce=$rateLimitEnforce)" }
    logger.info { "  ${RateLimitProviders.Principal}: pat=${limits.pat.limit}/${limits.pat.period}, user=${limits.user.limit}/${limits.user.period}" }
    logger.info { "  ${RateLimitProviders.IpCheckin}: ${limits.ipCheckin.limit} per ${limits.ipCheckin.period}" }
    logger.info { "  ${RateLimitProviders.CheckinCode}: ${limits.checkinCode.limit} per ${limits.checkinCode.period}" }
    logger.info { "  ${RateLimitProviders.IpGeo}: ${limits.ipGeo.limit} per ${limits.ipGeo.period}" }
    logger.info { "  ${RateLimitProviders.IpOpenApi}: ${limits.ipOpenApi.limit} per ${limits.ipOpenApi.period}" }
    logger.info { "  ${RateLimitProviders.IpTelegram}: ${limits.ipTelegram.limit} per ${limits.ipTelegram.period}" }

    if (!rateLimitEnforce) {
        logger.info { "RATE_LIMIT_ENFORCE=false — RateLimit plugin not installed; no traffic will be throttled" }
        return
    }

    install(RateLimit) {
        register(RateLimitProviders.Principal) {
            // Per-call dynamic limit: PATs and JWT users get different sizes.
            // computeIfAbsent caches the bucket per resolved key, so the right
            // limit is locked in on first request and reused thereafter.
            rateLimiter { call, _ ->
                val authMethod = call.principal<KasifyUserPrincipal>()?.authMethod
                val cfg =
                    when (authMethod) {
                        AuthMethod.ApiToken -> limits.pat
                        // Fallback only fires if Principal is wrapped on a
                        // route that isn't actually authenticated — the
                        // requestKey logs a warning when it sees this.
                        AuthMethod.Jwt, null -> limits.user
                    }
                RateLimiter.default(limit = cfg.limit, refillPeriod = cfg.period)
            }
            requestKey { call ->
                val principal = call.principal<KasifyUserPrincipal>()
                when {
                    principal?.authMethod == AuthMethod.ApiToken && principal.tokenId != null ->
                        "pat:${principal.tokenId}"
                    principal?.authMethod == AuthMethod.Jwt ->
                        "user:${principal.userId}"
                    else -> {
                        logger.warn {
                            "RateLimitProviders.Principal applied to an unauthenticated request " +
                                "[path=${call.request.uri}] — falling back to IP keying"
                        }
                        call.request.origin.remoteHost
                    }
                }
            }
        }

        register(RateLimitProviders.IpCheckin) {
            rateLimiter(limit = limits.ipCheckin.limit, refillPeriod = limits.ipCheckin.period)
            requestKey { call -> call.request.origin.remoteHost }
        }

        register(RateLimitProviders.CheckinCode) {
            rateLimiter(limit = limits.checkinCode.limit, refillPeriod = limits.checkinCode.period)
            requestKey { call ->
                // Check-in code lives in path param `code`. Routes that wrap
                // this provider must define `{code}` in their path template.
                call.parameters["code"] ?: "missing"
            }
        }

        register(RateLimitProviders.IpGeo) {
            rateLimiter(limit = limits.ipGeo.limit, refillPeriod = limits.ipGeo.period)
            requestKey { call -> call.request.origin.remoteHost }
        }

        register(RateLimitProviders.IpOpenApi) {
            rateLimiter(limit = limits.ipOpenApi.limit, refillPeriod = limits.ipOpenApi.period)
            requestKey { call -> call.request.origin.remoteHost }
        }

        register(RateLimitProviders.IpTelegram) {
            rateLimiter(limit = limits.ipTelegram.limit, refillPeriod = limits.ipTelegram.period)
            requestKey { call -> call.request.origin.remoteHost }
        }
    }
}

/**
 * Wraps [build] with a Ktor `rateLimit(name)` block when:
 * 1. [rateLimitEnforce] is true (env-flag for the rollout switch), AND
 * 2. The [RateLimit] plugin is actually installed on the application.
 *
 * Otherwise applies no rate limiting. The plugin-presence check makes route
 * configuration safe to call from test fixtures that don't install RateLimit
 * — the wrap silently degrades to a no-op instead of failing at startup.
 *
 * Use this in route files instead of calling `rateLimit(...)` directly.
 */
fun Route.maybeRateLimit(
    name: RateLimitName,
    build: Route.() -> Unit,
) {
    val pluginInstalled = application.pluginOrNull(RateLimit) != null
    if (rateLimitEnforce && pluginInstalled) {
        rateLimit(name) { build() }
    } else {
        build()
    }
}

/**
 * Drop-in replacement for `authenticate(AUTH_BEARER, AUTH_COOKIE) { ... }`
 * that adds the [RateLimitProviders.Principal] limit. Auth always runs
 * (regardless of enforce flag); rate-limit wrapping is conditional on
 * [maybeRateLimit].
 */
fun Route.authenticatedAndRateLimited(build: Route.() -> Unit) {
    authenticate(AUTH_BEARER, AUTH_COOKIE) {
        maybeRateLimit(RateLimitProviders.Principal) {
            build()
        }
    }
}
