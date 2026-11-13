package eu.kasify

import io.ktor.server.application.Application
import io.ktor.server.routing.Route
import io.ktor.server.routing.route
import io.ktor.server.routing.routing

const val API_VERSION_PREFIX = "/v1"

fun Application.versionedRouting(build: Route.() -> Unit) {
    routing {
        route(API_VERSION_PREFIX) {
            build()
        }
    }
}
