package eu.kasify.routes.exceptions

class NotFoundException(
    message: String,
    val code: String = "not_found",
    cause: Throwable? = null,
) : Exception(message, cause)
