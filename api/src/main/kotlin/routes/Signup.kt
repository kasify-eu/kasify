package eu.kasify.routes

import com.auth0.jwt.JWT
import eu.kasify.db.UsersRepository
import eu.kasify.model.api.Account
import eu.kasify.model.api.requests.SignupRequest
import eu.kasify.model.email.Locale
import eu.kasify.routes.exceptions.BadParamRequestException
import eu.kasify.routes.exceptions.ConflictException
import eu.kasify.routes.exceptions.InternalServerException
import eu.kasify.routes.exceptions.NotAuthorizedException
import eu.kasify.security.JWTSecurity
import eu.kasify.security.JWTValidationException
import eu.kasify.versionedRouting
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.header
import io.ktor.client.request.put
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.openapi.hide
import io.ktor.server.routing.post
import io.ktor.utils.io.ExperimentalKtorApi
import org.koin.ktor.ext.inject
import java.sql.SQLException

@OptIn(ExperimentalKtorApi::class)
fun Application.configureAuthRouting() {
    val logger = KotlinLogging.logger {}
    val usersRepository by inject<UsersRepository>()
    val jwtSecurity by inject<JWTSecurity>()
    val kindeM2MTokenService by inject<eu.kasify.services.KindeM2MTokenService>()

    versionedRouting {
        post("/signup") {
            // Validation plugin handles all validation including required dates
            val signupRequest = call.receive<SignupRequest>()

            // Get JWT token from access_token cookie
            val token = call.request.cookies["access_token"]
            val idToken = call.request.cookies["id_token"]

            if (token == null || idToken == null) {
                logger.warn { "Signup failed: access & id token cookies not found" }
                throw BadParamRequestException(
                    "Missing access_token or id_token cookie",
                    code = "auth.missing_token",
                )
            }

            // Validate the token with JWTSecurity FIRST to ensure it's signed by Kinde
            // and it is not expired
            try {
                jwtSecurity.validateToken(token)
                jwtSecurity.validateToken(idToken)
            } catch (_: JWTValidationException) {
                logger.warn { "Token not valid" }
                throw NotAuthorizedException("Invalid or expired token", code = "auth.invalid_token")
            }

            // Now decode the validated JWT to extract user information
            val decodedJWT = JWT.decode(idToken)
            val subject = decodedJWT.subject // User ID from Kinde
            val email = decodedJWT.getClaim("email").asString()
            // Preferred language for notifications; the OIDC `locale` claim when present, else default (es).
            val locale =
                decodedJWT
                    .getClaim("locale")
                    .asString()
                    ?.let { Locale.fromTag(it) }
                    ?: Locale.ES

            if (subject == null || email == null) {
                logger.warn { "Signup failed: Missing required claims in JWT [subject=$subject, email=$email]" }
                throw BadParamRequestException(
                    "Invalid token: missing required user information",
                    code = "auth.invalid_claims",
                )
            }

            // Check if user already exists
            val existingUser = usersRepository.getUserByExternalId(subject)

            if (existingUser == null) {
                try {
                    logger.info { "Creating new user account [externalUserId=$subject, email=$email, name=${signupRequest.name}]" }
                    // Create user and account in database
                    val user =
                        usersRepository.createUserWithAccount(
                            externalUserId = subject,
                            name = signupRequest.name,
                            lastName = signupRequest.lastname,
                            email = email,
                            locale = locale,
                        )

                    logger.info { "User account created successfully [userId=${user.id}, accountId=${user.accountId}]" }

                    // Update Kinde user properties with k_uid and k_aid using cached M2M token
                    try {
                        val kindeM2MToken = kindeM2MTokenService.getToken()
                        updateKindeUserProperties(subject, user.id.toString(), user.accountId.toString(), kindeM2MToken, logger)
                        logger.info { "Kinde user properties updated successfully [externalUserId=$subject]" }
                    } catch (e: Exception) {
                        logger.error(e) { "Failed to update Kinde user properties [externalUserId=$subject]: ${e.message}" }
                    }

                    call.respond(HttpStatusCode.OK, Account.from(user))
                } catch (e: Exception) {
                    if (e.isUniqueViolation()) {
                        // Email already owned by a different external user, e.g. the Kinde user was deleted and re-created
                        logger.warn(e) { "Signup failed: email already registered [externalUserId=$subject, email=$email]" }
                        throw ConflictException(
                            "Email is already registered to another account",
                            code = "signup.email_already_registered",
                            cause = e,
                        )
                    }
                    logger.error(e) { "Signup failed: ${e.message}" }
                    throw InternalServerException("Failed to create user account", code = "signup.account_creation_failed", cause = e)
                }
            } else {
                // User already exists - return their existing info
                call.respond(HttpStatusCode.OK, Account.from(existingUser))
            }
        }.hide()
    }
}

private const val PG_UNIQUE_VIOLATION = "23505"

private fun Throwable.isUniqueViolation(): Boolean =
    generateSequence(this) { it.cause }
        .any { it is SQLException && it.sqlState == PG_UNIQUE_VIOLATION }

/**
 * Updates Kinde user custom properties (k_uid and k_aid) via Kinde Management API
 */
private suspend fun updateKindeUserProperties(
    kindeUserId: String,
    userId: String,
    accountId: String,
    m2mToken: String,
    logger: io.github.oshai.kotlinlogging.KLogger,
) {
    val kindeDomain = System.getenv("KINDE_DOMAIN")

    HttpClient(CIO) {
        install(HttpTimeout) {
            requestTimeoutMillis = 15_000
            connectTimeoutMillis = 10_000
            socketTimeoutMillis = 15_000
        }
    }.use { client ->
        // Update k_uid property
        logger.debug { "Updating k_uid property for Kinde user $kindeUserId" }
        val uidResponse =
            client.put("$kindeDomain/api/v1/users/$kindeUserId/properties/k_uid?value=$userId") {
                header("Authorization", "Bearer $m2mToken")
                header("Accept", "application/json")
            }

        if (uidResponse.status.value !in 200..299) {
            val errorBody = uidResponse.bodyAsText()
            logger.warn { "Failed to update k_uid property [status=${uidResponse.status}, body=$errorBody]" }
            throw Exception("Failed to update k_uid: ${uidResponse.status}")
        }

        // Update k_aid property
        logger.debug { "Updating k_aid property for Kinde user $kindeUserId" }
        val aidResponse =
            client.put("$kindeDomain/api/v1/users/$kindeUserId/properties/k_aid?value=$accountId") {
                header("Authorization", "Bearer $m2mToken")
                header("Accept", "application/json")
            }

        if (aidResponse.status.value !in 200..299) {
            val errorBody = aidResponse.bodyAsText()
            logger.warn { "Failed to update k_aid property [status=${aidResponse.status}, body=$errorBody]" }
            throw Exception("Failed to update k_aid: ${aidResponse.status}")
        }

        logger.debug { "Successfully updated both k_uid and k_aid properties for Kinde user $kindeUserId" }
    }
}
