package eu.kasify

import eu.kasify.security.AuthenticationService
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.application.Application
import io.ktor.server.auth.AuthenticationContext
import io.ktor.server.auth.AuthenticationProvider
import io.ktor.server.auth.authentication
import io.ktor.server.auth.bearer
import io.ktor.server.auth.principal
import org.koin.ktor.ext.inject

const val AUTH_BEARER = "auth-bearer"
const val AUTH_COOKIE = "auth-cookie"

fun Application.configureSecurity() {
    val authService by inject<AuthenticationService>()

    authentication {
        bearer(AUTH_BEARER) {
            authenticate { token ->
                authService.authenticateWithToken(token.token)
            }
        }

        register(CookieAuthProvider(authService))
    }
}

private class CookieAuthProvider(
    private val authService: AuthenticationService,
) : AuthenticationProvider(Config()) {
    private val logger = KotlinLogging.logger {}

    class Config : AuthenticationProvider.Config(AUTH_COOKIE)

    override suspend fun onAuthenticate(context: AuthenticationContext) {
        val token = context.call.request.cookies["access_token"] ?: return
        logger.debug { "Cookie authentication: Validating access_token cookie" }
        val principal = authService.authenticateWithToken(token)
        if (principal != null) {
            context.principal(principal)
        }
    }
}
