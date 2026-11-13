package eu.kasify.routes.exceptions

class ForbiddenException(
    message: String,
    val code: String = "forbidden",
    cause: Throwable? = null,
) : Exception(message, cause)
