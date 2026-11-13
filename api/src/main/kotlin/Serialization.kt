package eu.kasify

import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import kotlinx.serialization.json.Json

fun Application.configureSerialization() {
    val isDevelopment = System.getenv("ENVIRONMENT") != "production"
    install(ContentNegotiation) {
        json(
            Json {
                prettyPrint = isDevelopment
                isLenient = true
                ignoreUnknownKeys = true
                explicitNulls = false
            },
        )
    }
}
