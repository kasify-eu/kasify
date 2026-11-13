package eu.kasify.routes.exceptions

class NotImplementedException(
    message: String,
    val code: String = "not_implemented",
    cause: Throwable? = null,
) : Exception(message, cause)
