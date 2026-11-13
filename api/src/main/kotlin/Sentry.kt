package eu.kasify

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.application.Application
import io.sentry.Sentry

private val logger = KotlinLogging.logger {}

fun Application.configureSentry() {
    val sentryDSN = System.getenv("SENTRY_DSN")
    if (sentryDSN == null) {
        logger.debug { "Environment variable 'SENTRY_DSN' not set. Sentry disabled" }
        return
    }

    val sentryDebug = System.getenv("SENTRY_DEBUG")?.toBoolean() ?: false
    Sentry.init { options ->
        options.dsn = sentryDSN
        // When first trying Sentry it's good to see what the SDK is doing:
        options.isDebug = sentryDebug
    }

    logger.debug { "Sentry enabled. Debug Mode: $sentryDebug" }
}
