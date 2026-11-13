package eu.kasify.routes.exceptions

class NotAcceptableException(
    message: String,
    val code: String = "not_acceptable",
    cause: Throwable? = null,
) : Exception(message, cause)
