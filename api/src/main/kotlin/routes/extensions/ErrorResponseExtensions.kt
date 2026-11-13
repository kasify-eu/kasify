package eu.kasify.routes.extensions

import eu.kasify.ErrorResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond

// Extension functions for standardized error responses across all endpoints.
// All error responses use the ErrorResponse data class format for consistency.

/**
 * Responds with 400 Bad Request
 */
suspend fun ApplicationCall.respondBadRequest(
    message: String,
    code: String = "invalid_parameter",
) {
    respond(HttpStatusCode.BadRequest, ErrorResponse(code = code, message = message))
}

/**
 * Responds with 404 Not Found
 */
suspend fun ApplicationCall.respondNotFound(
    message: String = "Resource not found",
    code: String = "not_found",
) {
    respond(HttpStatusCode.NotFound, ErrorResponse(code = code, message = message))
}

/**
 * Responds with 409 Conflict
 */
suspend fun ApplicationCall.respondConflict(
    message: String,
    code: String = "conflict",
) {
    respond(HttpStatusCode.Conflict, ErrorResponse(code = code, message = message))
}

/**
 * Responds with 500 Internal Server Error
 */
suspend fun ApplicationCall.respondInternalError(
    message: String,
    code: String = "internal_error",
) {
    respond(HttpStatusCode.InternalServerError, ErrorResponse(code = code, message = message))
}

/**
 * Responds with 501 Not Implemented
 */
suspend fun ApplicationCall.respondNotImplemented(
    message: String,
    code: String = "not_implemented",
) {
    respond(HttpStatusCode.NotImplemented, ErrorResponse(code = code, message = message))
}

/**
 * Responds with 406 Not Acceptable
 */
suspend fun ApplicationCall.respondNotAcceptable(
    message: String,
    code: String = "not_acceptable",
) {
    respond(HttpStatusCode.NotAcceptable, ErrorResponse(code = code, message = message))
}

suspend fun ApplicationCall.respondForbidden(
    message: String,
    code: String = "forbidden",
) {
    respond(HttpStatusCode.Forbidden, ErrorResponse(code = code, message = message))
}
