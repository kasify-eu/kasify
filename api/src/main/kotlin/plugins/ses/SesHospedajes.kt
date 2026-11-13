package eu.kasify.plugins.ses

/**
 * Public contract for talking to the SES Hospedajes SOAP service.
 *
 * Concrete implementations: [SesHospedajesPlugin] (the production HTTP-backed client).
 * Tests that don't care about the wire format should depend on this interface and provide
 * their own fake; tests that exercise the SOAP layer use [SesHospedajesPlugin] with a mock
 * HTTP engine.
 */
interface SesHospedajes {
    /**
     * Queries the SES catalog for reference data.
     *
     * @param catalogName The catalog table to query (e.g., "TIPO_DOCUMENTO", "SEXO", "TIPO_PAGO").
     *                    If `null`, returns the list of available catalog tables.
     */
    suspend fun getCatalog(catalogName: String? = null): List<SesCatalogEntry>

    /**
     * Submits a Parte de Viajeros (PV) — the SES communication type used for hospedaje
     * check-ins under RD 933/2021. Returns the batch ID (`lote`) and per-guest results.
     */
    suspend fun submitParteViajeros(request: SesParteViajerosRequest): SesCommunicationResponse

    /**
     * Submits a Reserva de Hospedaje (RH) — the SES communication sent when a hospedaje booking is
     * confirmed. Returns the batch ID (`lote`) and per-communication results, same as PV.
     */
    suspend fun submitReservaHospedaje(request: SesReservaHospedajeRequest): SesCommunicationResponse

    /**
     * Anula (annuls) a previously-filed communication identified by its `codigoComunicacion`.
     * Uses the same `comunicacion` SOAP call as an alta but with `tipoOperacion=B`; the response
     * carries a `lote` and is polled via [consultBatch] exactly like an alta.
     */
    suspend fun cancelCommunication(request: SesCancelRequest): SesCommunicationResponse

    /**
     * Queries one or more batches (lotes) by their codes and returns per-batch metadata
     * plus the per-communication results registered under each batch.
     */
    suspend fun consultBatch(loteCodes: List<String>): SesBatchQueryResponse

    /**
     * Verifies that the configured credentials are accepted by SES by performing the lightest
     * possible authenticated call ([getCatalog] with no filter, which lists the available
     * catalog tables).
     *
     * Returns normally if SES accepted the credentials. Throws
     * [SesHospedajesUnauthorizedException] on HTTP 401 (bad arrendador / username / password
     * combination) and other [SesHospedajesException]s on any other failure — callers can
     * surface those to the user as a credential-validation error in the dashboard.
     */
    suspend fun verifyCredentials()

    /** Releases the underlying HTTP client if this implementation owns one. */
    fun close()
}
