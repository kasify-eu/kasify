package eu.kasify.routes

import eu.kasify.configureOpenApi
import eu.kasify.extensions.configureTestModules
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The OpenAPI spec is public (served at /openapi.json and published in the docs portal), so
 * internal surfaces must stay out of it: the platform-admin routes (hidden via `.hide()` on
 * the `requireRole("admin")` blocks) and the Telegram webhook (a Telegram-to-server callback,
 * not a client API). `/metrics` is hidden the same way but is env-gated, so it is not
 * registered here.
 */
class OpenApiSpecTest {
    @Test
    fun specHidesInternalEndpoints() =
        testApplication {
            application {
                configureTestModules()
                configureAdminListsRouting()
                configureAdminSesMonitoringRouting()
                configureAdminOverviewRouting()
                configureOpenApi()
            }

            val response = client.get("/openapi.json")
            assertEquals(HttpStatusCode.OK, response.status)

            val spec = response.bodyAsText()
            assertFalse(spec.contains("/admin/"), "admin endpoints must not appear in the OpenAPI spec")
            assertFalse(spec.contains("/telegram/webhook"), "the Telegram webhook must not appear in the OpenAPI spec")
            assertTrue(spec.contains("/v1/properties"), "public endpoints must appear in the OpenAPI spec")
        }
}
