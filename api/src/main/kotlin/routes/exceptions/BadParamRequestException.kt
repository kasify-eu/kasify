package eu.kasify.routes.exceptions

import io.ktor.server.plugins.BadRequestException

class BadParamRequestException(
    message: String,
    val code: String = "invalid_parameter",
    cause: Throwable? = null,
) : BadRequestException(message, cause)
