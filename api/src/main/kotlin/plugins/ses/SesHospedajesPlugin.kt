package eu.kasify.plugins.ses

import eu.kasify.plugins.ses.internal.SesAnulacionSolicitudWriter
import eu.kasify.plugins.ses.internal.SesEnvelopes
import eu.kasify.plugins.ses.internal.SesPvSolicitudWriter
import eu.kasify.plugins.ses.internal.SesResponseParsers
import eu.kasify.plugins.ses.internal.SesRhSolicitudWriter
import eu.kasify.plugins.ses.internal.SesSoapClient
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import kotlinx.coroutines.CancellationException

/**
 * HTTP-backed [SesHospedajes]. Acts as a thin operations facade — the SOAP transport,
 * envelope writers, response parsers and PV solicitud builder all live in `internal/`.
 *
 * Kept `open` and with the legacy constructor signature so [services.SesHospedajesService]
 * and the existing tests don't have to change.
 */
open class SesHospedajesPlugin(
    sesUsername: String,
    sesPassword: String,
    environment: SesEnvironment = SesEnvironment.PRODUCTION,
    httpClient: HttpClient? = null,
    endpointOverride: String? = System.getenv("SES_ENDPOINT_URL"),
) : SesHospedajes {
    private val logger = KotlinLogging.logger {}

    private val client: SesSoapClient = //
        SesSoapClient(
            username = sesUsername,
            password = sesPassword,
            environment = environment,
            httpClient = httpClient,
            endpointOverride = endpointOverride,
        )

    override suspend fun getCatalog(catalogName: String?): List<SesCatalogEntry> {
        try {
            logger.info { "Fetching SES catalog${catalogName?.let { ": $it" } ?: " (list all tables)"}" }

            val responseText = client.post(SesEnvelopes.catalogo(catalogName))
            logger.debug { "SES catalog response: $responseText" }

            return SesResponseParsers.parseCatalog(responseText)
        } catch (e: SesHospedajesException) {
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            logger.error(e) { "Failed to fetch SES catalog" }
            throw SesHospedajesException("Error fetching SES catalog", e)
        }
    }

    override suspend fun submitParteViajeros(request: SesParteViajerosRequest): SesCommunicationResponse {
        try {
            logger.info { "Submitting Parte de Viajeros for establishment ${request.establishmentCode}" }

            val encodedSolicitud = SesPvSolicitudWriter.buildEncodedSolicitud(request)
            val envelope =
                SesEnvelopes.comunicacionAlta(
                    arrendadorCode = request.arrendadorCode,
                    aplicacion = APLICACION,
                    tipoComunicacion = TIPO_COMUNICACION_PV,
                    encodedSolicitud = encodedSolicitud,
                )

            val responseText = client.post(envelope)
            logger.debug { "SES communication response: $responseText" }
            return SesResponseParsers.parseComunicacion(responseText)
        } catch (e: SesHospedajesException) {
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            logger.error(e) { "Failed to submit Parte de Viajeros" }
            throw SesHospedajesException("Error submitting Parte de Viajeros", e)
        }
    }

    override suspend fun submitReservaHospedaje(request: SesReservaHospedajeRequest): SesCommunicationResponse {
        try {
            logger.info { "Submitting Reserva de Hospedaje for establishment ${request.establishmentCode}" }

            val encodedSolicitud = SesRhSolicitudWriter.buildEncodedSolicitud(request)
            val envelope =
                SesEnvelopes.comunicacionAlta(
                    arrendadorCode = request.arrendadorCode,
                    aplicacion = APLICACION,
                    tipoComunicacion = TIPO_COMUNICACION_RH,
                    encodedSolicitud = encodedSolicitud,
                )

            val responseText = client.post(envelope)
            logger.debug { "SES communication response: $responseText" }
            return SesResponseParsers.parseComunicacion(responseText)
        } catch (e: SesHospedajesException) {
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            logger.error(e) { "Failed to submit Reserva de Hospedaje" }
            throw SesHospedajesException("Error submitting Reserva de Hospedaje", e)
        }
    }

    override suspend fun cancelCommunication(request: SesCancelRequest): SesCommunicationResponse {
        try {
            logger.info {
                "Cancelling SES ${request.tipoComunicacion} communication ${request.codigoComunicacion}"
            }

            val encodedSolicitud = SesAnulacionSolicitudWriter.buildEncodedSolicitud(listOf(request.codigoComunicacion))
            val envelope =
                SesEnvelopes.comunicacionAnulacion(
                    arrendadorCode = request.arrendadorCode,
                    aplicacion = APLICACION,
                    tipoComunicacion = request.tipoComunicacion,
                    encodedSolicitud = encodedSolicitud,
                )

            val responseText = client.post(envelope)
            logger.debug { "SES anulación response: $responseText" }
            return SesResponseParsers.parseComunicacion(responseText)
        } catch (e: SesHospedajesException) {
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            logger.error(e) { "Failed to cancel SES communication" }
            throw SesHospedajesException("Error cancelling SES communication", e)
        }
    }

    override suspend fun consultBatch(loteCodes: List<String>): SesBatchQueryResponse {
        require(loteCodes.isNotEmpty()) { "loteCodes must not be empty" }
        require(loteCodes.all { it.length <= 36 }) { "Each lote code must be at most 36 characters" }

        try {
            logger.info { "Querying SES batches: ${loteCodes.joinToString(",")}" }

            val responseText = client.post(SesEnvelopes.consultaLote(loteCodes))
            logger.debug { "SES consultaLote response: $responseText" }

            return SesResponseParsers.parseConsultaLote(responseText)
        } catch (e: SesHospedajesException) {
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            logger.error(e) { "Failed to query SES batches" }
            throw SesHospedajesException("Error querying SES batches", e)
        }
    }

    override suspend fun verifyCredentials() {
        // getCatalog() is the cheapest authenticated call SES exposes and already maps 401 to
        // SesHospedajesUnauthorizedException, so verification is just "did this not throw?".
        getCatalog()
    }

    override fun close() {
        client.close()
    }

    private companion object {
        const val APLICACION = "Kasify"
        const val TIPO_COMUNICACION_PV = "PV"
        const val TIPO_COMUNICACION_RH = "RH"
    }
}
