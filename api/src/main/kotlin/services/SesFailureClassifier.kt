package eu.kasify.services

/**
 * The scope of a SES Hospedajes failure — what is actually broken, and therefore where the
 * blocking state must be recorded and what un-sticks it:
 *
 * - [IntegrationCredentials]: the account's SES portal configuration / WS credentials are wrong.
 *   Recorded on the integration row ([SesCredentialsGuard]); pauses all of that integration's
 *   assigned properties until the credentials are fixed or a later SES call succeeds.
 * - [PropertyConfig]: the property's SES configuration (establishment code) is wrong. Recorded on
 *   the property row ([SesPropertyConfigGuard]); pauses only that property until the code is
 *   re-saved or a later SES filing for the property succeeds.
 * - [BookingData]: the communicated data itself was rejected. Recorded on the comm row as
 *   `rejected`; re-picked only when the booking/guest data changes (the rejected-alta retry gate).
 *
 * Transport/SOAP failures never reach this classifier — they are caught before a response exists
 * and recorded as `failed` (retried every tick).
 */
sealed interface SesFailureCause {
    data class IntegrationCredentials(
        val codigo: Int,
    ) : SesFailureCause

    data object PropertyConfig : SesFailureCause

    data object BookingData : SesFailureCause
}

/**
 * Single source of truth for classifying SES Hospedajes failures into a [SesFailureCause].
 * Every SES outcome — synchronous submit códigos and asynchronous consultaLote per-communication
 * error texts — funnels through here, so the código sets and text patterns live in one auditable
 * place instead of being scattered across the service, the jobs, and the guards.
 *
 * Classification is deliberately conservative: only a high-confidence match promotes a failure out
 * of [SesFailureCause.BookingData]. SES returns no structured código for estado-6 per-communication
 * errors, so [PROPERTY_CONFIG_ERROR_PATTERNS] matches free text — an unrecognized error must degrade
 * to the booking-scoped `rejected` path (the pre-classifier behavior), never to a pause.
 */
object SesFailureClassifier {
    /**
     * Layer-1 códigos that mean the SES portal configuration/credentials are wrong
     * (the "credential-family" errors):
     * 10103 landlord code doesn't exist / doesn't belong to the user, 10107 bad user/password,
     * 10119 registration doesn't allow this communication type, 10120 web-service access
     * not enabled in the portal.
     */
    val CREDENTIAL_ERROR_CODES = setOf(10103, 10107, 10119, 10120)

    /**
     * Estado-6 per-communication error texts that mean the property's establishment code is wrong.
     * SES: "No existe un establecimiento para ese código. Solo puede enviar comunicaciones a sus
     * establecimientos. …" — matched case-insensitively on the stable leading phrase.
     */
    private val PROPERTY_CONFIG_ERROR_PATTERNS =
        listOf(
            "no existe un establecimiento",
        )

    fun isCredentialError(codigo: Int): Boolean = codigo in CREDENTIAL_ERROR_CODES

    /** True when [errorText] (a submit descripción or a poll per-communication error) identifies a wrong establishment code. */
    fun isPropertyConfigError(errorText: String?): Boolean {
        if (errorText.isNullOrBlank()) return false
        val lower = errorText.lowercase()
        return PROPERTY_CONFIG_ERROR_PATTERNS.any { lower.contains(it) }
    }

    /** Classify a synchronous submit/cancel rejection (a non-zero Layer-1 código). */
    fun classifyRejection(
        codigo: Int,
        descripcion: String?,
    ): SesFailureCause =
        when {
            isCredentialError(codigo) -> SesFailureCause.IntegrationCredentials(codigo)
            isPropertyConfigError(descripcion) -> SesFailureCause.PropertyConfig
            else -> SesFailureCause.BookingData
        }
}
