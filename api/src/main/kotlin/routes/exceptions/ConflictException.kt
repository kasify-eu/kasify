package eu.kasify.routes.exceptions

class ConflictException(
    message: String,
    val code: String = "conflict",
    cause: Throwable? = null,
) : Exception(message, cause)
