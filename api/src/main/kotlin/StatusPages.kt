package eu.kasify
import eu.kasify.routes.exceptions.BadParamRequestException
import eu.kasify.routes.exceptions.ConflictException
import eu.kasify.routes.exceptions.ForbiddenException
import eu.kasify.routes.exceptions.InternalServerException
import eu.kasify.routes.exceptions.NotAcceptableException
import eu.kasify.routes.exceptions.NotAuthorizedException
import eu.kasify.routes.exceptions.NotFoundException
import eu.kasify.routes.exceptions.NotImplementedException
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.ContentTransformationException
import io.ktor.server.plugins.requestvalidation.RequestValidationException
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.httpMethod
import io.ktor.server.request.uri
import io.ktor.server.response.respond
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import java.util.UUID

private val logger = KotlinLogging.logger {}

/**
 * Extracts a user-friendly error message from BadRequestException.
 * Looks at nested causes to find serialization errors.
 */
private fun extractBadRequestError(cause: io.ktor.server.plugins.BadRequestException): String {
    // Check for nested serialization exceptions
    var current: Throwable? = cause.cause
    while (current != null) {
        if (current is SerializationException) {
            return extractSerializationMessage(current.message)
        }
        current = current.cause
    }
    return cause.message ?: "Invalid request"
}

/**
 * Extracts a user-friendly error message from serialization exceptions.
 * Handles common cases like missing fields, type mismatches, and malformed JSON.
 */
private fun extractSerializationError(cause: ContentTransformationException): String {
    val rootCause = cause.cause
    if (rootCause is SerializationException) {
        return extractSerializationMessage(rootCause.message)
    }
    return cause.message ?: "Failed to parse request body"
}

/**
 * Parses a kotlinx.serialization error message and returns a user-friendly version.
 */
private fun extractSerializationMessage(message: String?): String {
    if (message == null) return "Invalid request body"

    // Multiple missing fields: "Fields [field1, field2] are required ... but they were missing"
    val missingFieldsRegex = Regex("Fields \\[([^\\]]+)\\] are required .* but they were missing")
    missingFieldsRegex.find(message)?.let { match ->
        return "Missing required fields: ${match.groupValues[1]}"
    }

    // Single missing field: "Field 'fieldName' is required ... but it was missing"
    val missingFieldRegex = Regex("Field '([^']+)' is required .* but it was missing")
    missingFieldRegex.find(message)?.let { match ->
        return "Missing required field: '${match.groupValues[1]}'"
    }

    // Unknown field: "Encountered an unknown key 'fieldName'"
    val unknownKeyRegex = Regex("Encountered an unknown key '([^']+)'")
    unknownKeyRegex.find(message)?.let { match ->
        return "Unknown field: '${match.groupValues[1]}'"
    }

    // Type mismatch: "Expected ... but had ..."
    if (message.contains("Expected") && message.contains("but had")) {
        return "Invalid field type: $message"
    }

    // JSON syntax error
    if (message.contains("Unexpected JSON token") || message.contains("Expected")) {
        return "Invalid JSON format: $message"
    }

    return message
}

/**
 * Standard error response format.
 *
 * - [code]    Stable, machine-readable error code. The frontend should switch on this
 *             value; it will never change once shipped. Format: `domain.error_name`
 *             (e.g. `checkin.duplicate_document`) or a plain generic code (`not_found`).
 * - [message] Human-readable description for debugging / fallback display. May change.
 * - [errorId] Present only on 5xx errors; used for support / log correlation.
 */
@Serializable
data class ErrorResponse(
    val code: String,
    val message: String,
    @SerialName("error_id")
    val errorId: String? = null,
    val timestamp: String =
        java.time.Instant
            .now()
            .toString(),
)

fun Application.configureStatusPages() {
    install(StatusPages) {
        // Specific exception handlers with appropriate HTTP status codes

        exception<NotFoundException> { call, cause ->
            logger.debug { "Not found: ${cause.message} - Path: ${call.request.uri}" }
            call.respond(
                HttpStatusCode.NotFound,
                ErrorResponse(code = cause.code, message = cause.message ?: "Resource not found"),
            )
        }

        exception<IllegalArgumentException> { call, cause ->
            logger.debug { "Bad request: ${cause.message} - Path: ${call.request.uri}" }
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse(code = "invalid_parameter", message = cause.message ?: "Invalid request parameters"),
            )
        }

        exception<RequestValidationException> { call, cause ->
            logger.debug { "Validation error: ${cause.reasons} - Path: ${call.request.uri}" }
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse(code = "validation_error", message = cause.reasons.joinToString()),
            )
        }

        exception<BadParamRequestException> { call, cause ->
            logger.debug { "Bad parameter: ${cause.message} - Path: ${call.request.uri}" }
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse(code = cause.code, message = cause.message ?: "Invalid parameter"),
            )
        }

        exception<NotAuthorizedException> { call, cause ->
            logger.warn { "Authorization failed: ${cause.message} - Path: ${call.request.uri}" }
            call.respond(
                HttpStatusCode.Unauthorized,
                ErrorResponse(code = cause.code, message = cause.message ?: "Access denied"),
            )
        }

        exception<ForbiddenException> { call, cause ->
            logger.warn { "Forbidden: ${cause.message} - Path: ${call.request.uri}" }
            call.respond(
                HttpStatusCode.Forbidden,
                ErrorResponse(code = cause.code, message = cause.message ?: "Forbidden"),
            )
        }

        exception<ConflictException> { call, cause ->
            logger.debug { "Conflict: ${cause.message} - Path: ${call.request.uri}" }
            call.respond(
                HttpStatusCode.Conflict,
                ErrorResponse(code = cause.code, message = cause.message ?: "Resource conflict"),
            )
        }

        exception<NotAcceptableException> { call, cause ->
            logger.debug { "Not acceptable: ${cause.message} - Path: ${call.request.uri}" }
            call.respond(
                HttpStatusCode.NotAcceptable,
                ErrorResponse(code = cause.code, message = cause.message ?: "Not acceptable"),
            )
        }

        exception<NotImplementedException> { call, cause ->
            logger.debug { "Not implemented: ${cause.message} - Path: ${call.request.uri}" }
            call.respond(
                HttpStatusCode.NotImplemented,
                ErrorResponse(code = cause.code, message = cause.message ?: "Not implemented"),
            )
        }

        exception<InternalServerException> { call, cause ->
            val errorId = UUID.randomUUID().toString()
            logger.error(cause) { "Internal error [ID: $errorId] - Path: ${call.request.uri}" }
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponse(
                    code = cause.code,
                    message = cause.message ?: "Internal server error",
                    errorId = errorId,
                ),
            )
        }

        // Rate-limit 429s: Ktor's RateLimit plugin replies with bare 429.
        // Wrap with our standard error envelope. Retry-After is already set
        // by the plugin's modifyResponse callback.
        status(HttpStatusCode.TooManyRequests) { call, status ->
            logger.warn {
                "Rate limit exceeded - Path: ${call.request.uri} Method: ${call.request.httpMethod.value}"
            }
            val retryAfter = call.response.headers["Retry-After"]?.toLongOrNull()
            val message =
                if (retryAfter != null) {
                    "Too many requests. Retry after $retryAfter seconds."
                } else {
                    "Too many requests."
                }
            call.respond(
                status,
                ErrorResponse(code = "rate_limit.exceeded", message = message),
            )
        }

        exception<io.ktor.server.plugins.BadRequestException> { call, cause ->
            val errorMessage = extractBadRequestError(cause)
            logger.debug { "Bad parameter: $errorMessage - Path: ${call.request.uri}" }
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse(code = "invalid_request_body", message = errorMessage),
            )
        }

        exception<ContentTransformationException> { call, cause ->
            val errorMessage = extractSerializationError(cause)
            logger.debug { "Content transformation error: $errorMessage - Path: ${call.request.uri}" }
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse(code = "invalid_request_body", message = errorMessage),
            )
        }

        // Catch-all handler for any unhandled exceptions
        // This prevents stack traces from leaking to clients
        exception<Throwable> { call, cause ->
            val errorId = UUID.randomUUID().toString()

            // Log the full exception with stack trace for debugging
            logger.error(cause) {
                "Unhandled exception [ID: $errorId] - " +
                    "Method: ${call.request.httpMethod.value} " +
                    "Path: ${call.request.uri} " +
                    "Message: ${cause.message}"
            }

            // Determine if we should expose error details based on environment
            val isDevelopment = System.getenv("ENVIRONMENT") != "production"

            val errorMessage =
                when {
                    isDevelopment -> "Internal server error: ${cause.message}"
                    else -> "An unexpected error occurred. Please contact support with error ID: $errorId"
                }

            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponse(
                    code = "internal_error",
                    message = errorMessage,
                    errorId = errorId,
                ),
            )
        }
    }
}
