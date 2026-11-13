package eu.kasify.plugins.resend

class ResendException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
