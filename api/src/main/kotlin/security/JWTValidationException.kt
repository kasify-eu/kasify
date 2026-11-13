package eu.kasify.security

/**
 * Exception thrown when JWT validation fails.
 *
 * @param message The error message describing why validation failed
 * @param cause The underlying cause of the validation failure, if any
 */
class JWTValidationException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
