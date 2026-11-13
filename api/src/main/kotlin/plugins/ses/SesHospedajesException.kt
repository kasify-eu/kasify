package eu.kasify.plugins.ses

open class SesHospedajesException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

/**
 * Thrown when the SES Hospedajes API responds with HTTP 401.
 *
 * Per the SES docs, a 401 means the combination of `codigoArrendador`, username and password
 * is invalid — all three must match. Catchable as the parent [SesHospedajesException] for code
 * that just wants to treat all SES failures uniformly.
 */
class SesHospedajesUnauthorizedException(
    message: String = "Unauthorized access to SES Hospedajes API",
    cause: Throwable? = null,
) : SesHospedajesException(message, cause)
