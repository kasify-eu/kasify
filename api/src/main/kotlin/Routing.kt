package eu.kasify

import eu.kasify.model.ApiInfo
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing

fun Application.configureRouting() {
    routing {
        get("/") {
            val info =
                ApiInfo(
                    name = "Kasify API",
                    version = AppVersion.current,
                    description = "Property management API",
                )
            call.respond(HttpStatusCode.OK, info)
        }
    }
}
