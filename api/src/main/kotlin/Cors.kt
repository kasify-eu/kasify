package eu.kasify

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.cors.routing.CORS

private const val LOCAL_DEV_HOST = "localhost:5173"

/**
 * Parses CORS_ALLOWED_HOSTS: a comma-separated list of hosts (no scheme), e.g.
 * `app.example.com,localhost:5173`. When unset only the local dev dashboard is allowed.
 */
internal fun parseAllowedHosts(raw: String?): List<String> =
    raw
        ?.split(',')
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() }
        ?.takeIf { it.isNotEmpty() }
        ?: listOf(LOCAL_DEV_HOST)

fun Application.configureCORS() {
    val hosts = parseAllowedHosts(System.getenv("CORS_ALLOWED_HOSTS"))
    install(CORS) {
        hosts.forEach { host ->
            val isLocal = host.startsWith("localhost") || host.startsWith("127.0.0.1")
            allowHost(host, schemes = if (isLocal) listOf("http", "https") else listOf("https"))
        }
        allowCredentials = true
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Patch)
        allowMethod(HttpMethod.Delete)
    }
}
