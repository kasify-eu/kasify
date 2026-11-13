package eu.kasify.routes.exceptions

class InternalServerException(
    message: String,
    val code: String = "internal_error",
    cause: Throwable? = null,
) : Exception(message, cause)
