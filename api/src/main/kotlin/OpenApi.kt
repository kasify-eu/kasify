package eu.kasify

import io.ktor.http.ContentType
import io.ktor.openapi.OpenApiDoc
import io.ktor.openapi.OpenApiInfo
import io.ktor.openapi.SecuritySchemeIn
import io.ktor.openapi.Tag
import io.ktor.server.application.Application
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.openapi.OpenApiDocSource
import io.ktor.server.routing.openapi.registerApiKeySecurityScheme
import io.ktor.server.routing.openapi.registerBearerAuthSecurityScheme
import io.ktor.server.routing.routing

fun Application.configureOpenApi() {
    registerBearerAuthSecurityScheme(
        name = AUTH_BEARER,
        bearerFormat = "JWT",
        description = "Kinde-issued JWT supplied in the Authorization header",
    )
    registerApiKeySecurityScheme(
        name = AUTH_COOKIE,
        keyName = "access_token",
        keyLocation = SecuritySchemeIn.COOKIE,
        description = "Kinde-issued JWT delivered as the access_token cookie",
    )

    val yamlSource = OpenApiDocSource.Routing(contentType = ContentType.Application.Yaml)
    val jsonSource = OpenApiDocSource.Routing(contentType = ContentType.Application.Json)
    val defaults =
        OpenApiDoc(
            info =
                OpenApiInfo(
                    title = "Kasify API",
                    version = AppVersion.current,
                    description =
                        """
                        ## Versioning

                        All business endpoints are served under a `/v1` prefix (e.g. `GET /v1/properties`).
                        The OpenAPI spec at `/openapi.json` sits outside the version prefix because it
                        describes the API rather than serves business data. Within `v1` only additive,
                        backwards-compatible changes ship; breaking changes land at `/v2`.

                        ## Rate limiting

                        Every endpoint is subject to rate limiting. When a bucket is exhausted the API responds with
                        HTTP `429 Too Many Requests`, a `Retry-After` header (seconds), and the standard error envelope:

                        ```json
                        { "code": "rate_limit.exceeded", "message": "Too many requests. Retry after 42 seconds." }
                        ```

                        Default buckets (1-minute rolling window): 100/min per personal access token,
                        50/min per IP on `/v1/geo/...`, 100/min per IP on the Telegram webhook,
                        and 10/min per IP on the OpenAPI spec.
                        See the [Getting Started](/developers/getting-started) page for details.
                        """.trimIndent(),
                ),
            tags =
                listOf(
                    Tag(name = "AccessCodes"),
                    Tag(name = "Account"),
                    Tag(name = "Auth"),
                    Tag(name = "Bookings"),
                    Tag(name = "Calendar"),
                    Tag(name = "Checkin"),
                    Tag(name = "Geo"),
                    Tag(name = "Guests"),
                    Tag(name = "Health"),
                    Tag(name = "Integrations"),
                    Tag(name = "Properties"),
                    Tag(name = "PropertyMembers"),
                    Tag(name = "SmartLocks"),
                    Tag(name = "Tasks"),
                    Tag(name = "Telegram"),
                    Tag(name = "Timeline"),
                ),
        )

    routing {
        maybeRateLimit(RateLimitProviders.IpOpenApi) {
            get("/openapi.yaml") {
                val doc = yamlSource.read(this@configureOpenApi, defaults)
                call.respondText(doc.content, doc.contentType)
            }
            get("/openapi.json") {
                val doc = jsonSource.read(this@configureOpenApi, defaults)
                call.respondText(doc.content, doc.contentType)
            }
        }
    }
}
