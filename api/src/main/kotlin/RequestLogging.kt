package eu.kasify
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.HttpHeaders
import io.ktor.http.decodeURLPart
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.auth.principal
import io.ktor.server.plugins.callid.CallId
import io.ktor.server.plugins.callid.callIdMdc
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.origin
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.util.AttributeKey
import java.util.UUID

private val logger = KotlinLogging.logger {}

/**
 * Configures comprehensive request logging with correlation IDs
 */
fun Application.configureRequestLogging() {
    // Install CallId plugin for request correlation/tracking
    install(CallId) {
        // Try to retrieve call ID from header (if client provides it)
        retrieveFromHeader(HttpHeaders.XRequestId)
        retrieveFromHeader("X-Correlation-Id")

        // Generate a new UUID if no call ID is provided
        generate { UUID.randomUUID().toString() }

        // Include the call ID in response headers for client tracking
        replyToHeader(HttpHeaders.XRequestId)
    }

    // Install CallLogging plugin for automatic request/response logging
    install(CallLogging) {
        // Log level based on environment
        val isDevelopment = System.getenv("ENVIRONMENT") != "production"
        level = if (isDevelopment) org.slf4j.event.Level.DEBUG else org.slf4j.event.Level.INFO

        // Include call ID in logs
        callIdMdc("request_id")

        // Custom log format
        format { call ->
            val status = call.response.status()
            val httpMethod = call.request.httpMethod.value
            val userAgent = call.request.headers["User-Agent"]
            val requestUri = call.loggableRequestPath()
            val userId = call.principal<eu.kasify.security.KasifyUserPrincipal>()?.userId

            buildString {
                append("${status?.value ?: "???"} ")
                append("$httpMethod $requestUri")

                // Add execution time if available
                val duration = call.attributes.getOrNull(RequestDurationKey)
                if (duration != null) {
                    append(" (${duration}ms)")
                }

                // Add user info if authenticated
                if (userId != null) {
                    append(" [user=$userId]")
                }

                // Add user agent in debug mode
                if (isDevelopment && userAgent != null) {
                    append(" [agent=${userAgent.take(50)}]")
                }
            }
        }

        // Filter out health check endpoints from logs (optional - reduces noise)
        filter { call ->
            !call.request.path().startsWith("/health")
        }

        // Log failed requests at WARN level
        mdc("http_status") { call ->
            call.response
                .status()
                ?.value
                ?.toString() ?: "unknown"
        }

        mdc("http_method") { call ->
            call.request.httpMethod.value
        }

        mdc("request_uri") { call ->
            call.loggableRequestPath()
        }

        mdc("user_id") { call ->
            call.principal<eu.kasify.security.KasifyUserPrincipal>()?.userId?.toString() ?: "anonymous"
        }

        mdc("client_ip") { call ->
            call.request.origin.remoteHost
        }
    }

    // Intercept requests to measure execution time
    intercept(ApplicationCallPipeline.Monitoring) {
        val startTime = System.currentTimeMillis()

        try {
            proceed()
        } finally {
            val duration = System.currentTimeMillis() - startTime
            call.attributes.put(RequestDurationKey, duration)

            // Log slow requests as warnings
            if (duration > 1000) { // More than 1 second
                logger.warn { "Slow request: ${call.request.httpMethod.value} ${call.loggableRequestPath()} took ${duration}ms" }
            }
        }
    }
}

// Attribute key for storing request duration
private val RequestDurationKey = AttributeKey<Long>("RequestDuration")

/**
 * Routes whose path carries a bearer secret, as templates matched against the request's
 * leading path segments; `null` marks the secret segment. Templates match with or without
 * the API version prefix, and trailing segments (e.g. `/checkin/{code}/guests`) are allowed.
 */
private val SECRET_PATH_TEMPLATES: List<List<String?>> =
    listOf(
        // /v1/public/tasks/{token}: unauthenticated task link
        listOf("public", "tasks", null),
        // /v1/invitations/{token}/accept: crew invitation token
        listOf("invitations", null, "accept"),
        // /v1/marketing/unsubscribe/{token}: one-click unsubscribe token
        listOf("marketing", "unsubscribe", null),
        // /v1/checkin/{code}[/guests]: booking check-in code (reads and registers guests)
        listOf("checkin", null),
    )

private const val REDACTED_PATH_SEGMENT = "***"

/**
 * The request target as it is safe to log: the query string is dropped (it carries secrets
 * such as the `/metrics?token=` scrape token) and secret path segments are redacted.
 * Segments are compared the way Ktor routing reads them (empty segments skipped, each one
 * URL-decoded), so an equivalent spelling of a secret route is redacted as well.
 */
private fun ApplicationCall.loggableRequestPath(): String {
    val path = request.path()
    val rawSegments = path.split('/')
    val routedIndices = rawSegments.indices.filter { rawSegments[it].isNotEmpty() }
    val routed = routedIndices.map { runCatching { rawSegments[it].decodeURLPart() }.getOrDefault(rawSegments[it]) }
    val offset = if (routed.firstOrNull() == API_VERSION_PREFIX.trimStart('/')) 1 else 0
    val template =
        SECRET_PATH_TEMPLATES.firstOrNull { candidate ->
            routed.size - offset >= candidate.size &&
                candidate.indices.all { i -> candidate[i] == null || candidate[i] == routed[offset + i] }
        } ?: return path

    val redacted = rawSegments.toMutableList()
    template.indices
        .filter { template[it] == null }
        .forEach { redacted[routedIndices[offset + it]] = REDACTED_PATH_SEGMENT }
    return redacted.joinToString("/")
}
