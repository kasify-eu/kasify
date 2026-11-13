package eu.kasify.routes.exceptions

class NotAuthorizedException(
    message: String,
    val code: String = "unauthorized",
    cause: Throwable? = null,
) : Exception(message, cause)
