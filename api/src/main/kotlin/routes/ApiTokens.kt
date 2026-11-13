package eu.kasify.routes

import eu.kasify.authenticatedAndRateLimited
import eu.kasify.db.ApiTokensRepository
import eu.kasify.model.api.ApiToken
import eu.kasify.model.api.CreateApiTokenRequest
import eu.kasify.model.api.CreateApiTokenResponse
import eu.kasify.routes.exceptions.BadParamRequestException
import eu.kasify.routes.exceptions.ConflictException
import eu.kasify.routes.exceptions.ForbiddenException
import eu.kasify.routes.exceptions.NotFoundException
import eu.kasify.security.AuthMethod
import eu.kasify.security.getKasifyUser
import eu.kasify.services.ApiTokenService
import eu.kasify.versionedRouting
import io.ktor.http.HttpStatusCode
import io.ktor.openapi.jsonSchema
import io.ktor.server.application.Application
import io.ktor.server.plugins.MissingRequestParameterException
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.openapi.describe
import io.ktor.server.routing.post
import io.ktor.utils.io.ExperimentalKtorApi
import org.koin.ktor.ext.inject
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.uuid.Uuid

private const val MAX_ACTIVE_TOKENS_PER_USER: Int = 10

@OptIn(ExperimentalKtorApi::class)
fun Application.configureApiTokensRouting() {
    val apiTokenService by inject<ApiTokenService>()
    val apiTokensRepository by inject<ApiTokensRepository>()

    versionedRouting {
        authenticatedAndRateLimited {
            post("/account/tokens") {
                val user = call.getKasifyUser()
                requireJwtAuth(user.authMethod)

                val request = call.receive<CreateApiTokenRequest>()

                val name = request.name.trim()
                if (name.isEmpty() || name.length > CreateApiTokenRequest.MAX_NAME_LENGTH) {
                    throw BadParamRequestException(
                        "Token name must be 1–${CreateApiTokenRequest.MAX_NAME_LENGTH} characters",
                        code = "api_token.invalid_name",
                    )
                }

                val expiresAt =
                    when (val days = request.expiresInDays) {
                        null -> null
                        in CreateApiTokenRequest.MIN_EXPIRATION_DAYS..CreateApiTokenRequest.MAX_EXPIRATION_DAYS ->
                            OffsetDateTime.now(ZoneOffset.UTC).plusDays(days.toLong())
                        else ->
                            throw BadParamRequestException(
                                "expires_in_days must be between ${CreateApiTokenRequest.MIN_EXPIRATION_DAYS} and ${CreateApiTokenRequest.MAX_EXPIRATION_DAYS}",
                                code = "api_token.invalid_expiration",
                            )
                    }

                if (apiTokensRepository.countActiveByUser(user.userId) >= MAX_ACTIVE_TOKENS_PER_USER) {
                    throw ConflictException(
                        "You have reached the maximum number of active API tokens",
                        code = "api_token.limit_reached",
                    )
                }

                if (apiTokensRepository.activeNameExists(user.userId, name)) {
                    throw ConflictException(
                        "An active API token with this name already exists",
                        code = "api_token.name_taken",
                    )
                }

                val generated =
                    apiTokenService.generate(
                        userId = user.userId,
                        accountId = user.accountId,
                        name = name,
                        expiresAt = expiresAt,
                    )

                call.respond(
                    HttpStatusCode.Created,
                    CreateApiTokenResponse(
                        id = generated.token.id,
                        name = generated.token.name,
                        token = generated.rawToken,
                        tokenPrefix = generated.token.tokenPrefix,
                        expiresAt = generated.token.expiresAt,
                        createdAt = generated.token.createdAt,
                    ),
                )
            }.describe {
                tag("ApiTokens")
                summary = "Create API token"
                description = "Creates a personal access token for the authenticated user. The raw token value is returned exactly once in this response and cannot be retrieved later."
                requestBody {
                    description = "Token name and optional expiration in days (default 365, max 730, explicit `null` means never expires)."
                    required = true
                    schema = jsonSchema<CreateApiTokenRequest>()
                }
                responses {
                    HttpStatusCode.Created {
                        description = "Token created. Includes the raw `token` string — store it securely."
                        schema = jsonSchema<CreateApiTokenResponse>()
                    }
                    HttpStatusCode.BadRequest {
                        description = "Invalid name (`api_token.invalid_name`) or expiration (`api_token.invalid_expiration`)"
                    }
                    HttpStatusCode.Unauthorized { description = "Not authenticated" }
                    HttpStatusCode.Forbidden { description = "Authenticated with an API token (`api_token.requires_session`)" }
                    HttpStatusCode.Conflict {
                        description = "Name already used (`api_token.name_taken`) or active-token limit reached (`api_token.limit_reached`)"
                    }
                }
            }

            get("/account/tokens") {
                val user = call.getKasifyUser()
                requireJwtAuth(user.authMethod)

                val tokens = apiTokensRepository.listActiveByUser(user.userId)
                call.respond(tokens.map { ApiToken.from(it) })
            }.describe {
                tag("ApiTokens")
                summary = "List API tokens"
                description = "Returns the authenticated user's active (non-revoked) API tokens. The raw token value is never included."
                responses {
                    HttpStatusCode.OK {
                        description = "List of the user's active API tokens"
                        schema = jsonSchema<List<ApiToken>>()
                    }
                    HttpStatusCode.Unauthorized { description = "Not authenticated" }
                    HttpStatusCode.Forbidden { description = "Authenticated with an API token (`api_token.requires_session`)" }
                }
            }

            delete("/account/tokens/{id}") {
                val user = call.getKasifyUser()
                requireJwtAuth(user.authMethod)

                val id =
                    call.parameters["id"]?.let { Uuid.parse(it) }
                        ?: throw MissingRequestParameterException("Missing token id")

                val revoked = apiTokensRepository.revoke(id, user.userId)
                if (!revoked) {
                    throw NotFoundException("API token not found")
                }
                call.respond(HttpStatusCode.NoContent)
            }.describe {
                tag("ApiTokens")
                summary = "Revoke API token"
                description = "Soft-deletes the token. Apps using it will be rejected on the next request. Returns 404 if the token does not exist or belongs to another user."
                parameters {
                    path("id") { description = "API token id" }
                }
                responses {
                    HttpStatusCode.NoContent { description = "Token revoked" }
                    HttpStatusCode.Unauthorized { description = "Not authenticated" }
                    HttpStatusCode.Forbidden { description = "Authenticated with an API token (`api_token.requires_session`)" }
                    HttpStatusCode.NotFound { description = "Token not found for this user" }
                }
            }
        }
    }
}

private fun requireJwtAuth(authMethod: AuthMethod) {
    if (authMethod != AuthMethod.Jwt) {
        throw ForbiddenException(
            "API tokens cannot be managed with an API token",
            code = "api_token.requires_session",
        )
    }
}
