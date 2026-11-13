package eu.kasify.plugins.ses.internal

import eu.kasify.plugins.ses.SesEnvironment
import eu.kasify.plugins.ses.SesHospedajesException
import eu.kasify.plugins.ses.SesHospedajesUnauthorizedException
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import java.util.Base64

/**
 * Thin SOAP-over-HTTP transport for SES Hospedajes. Handles Basic-auth header construction,
 * TLS trust pinned to the FNMT-RCM CAs that issue the SES certificates ([SesTrustManager]), and
 * the 401 → typed exception mapping. Knows nothing about the SOAP envelopes or response shapes.
 */
internal class SesSoapClient(
    username: String,
    password: String,
    private val environment: SesEnvironment,
    httpClient: HttpClient? = null,
    endpointOverride: String? = System.getenv("SES_ENDPOINT_URL"),
) {
    private val logger = KotlinLogging.logger {}

    /**
     * Where SOAP envelopes are POSTed. Normally [SesEnvironment.baseUrl]; the `SES_ENDPOINT_URL`
     * env var overrides it so a local mock (see "SES Hospedajes mock server" in `api/README.md`)
     * can stand in for SES during development. Never set it in production.
     */
    private val endpointUrl: String =
        endpointOverride?.takeIf { it.isNotBlank() }?.also {
            logger.warn { "SES_ENDPOINT_URL override active: POSTing to $it instead of ${environment.name} (${environment.baseUrl})" }
        } ?: environment.baseUrl

    /** Whether we constructed the HttpClient ourselves and should therefore close it. */
    private val ownsHttpClient: Boolean = httpClient == null

    private val basicAuthToken: String =
        Base64.getEncoder().encodeToString("$username:$password".toByteArray())

    private val client: HttpClient =
        httpClient ?: HttpClient(CIO) {
            install(HttpTimeout) {
                requestTimeoutMillis = 30_0000
                connectTimeoutMillis = 10_000
                socketTimeoutMillis = 30_0000
            }
            engine {
                https {
                    // SES serves an incomplete chain that the JVM default trust store can't
                    // validate, so trust only the bundled FNMT-RCM CAs (see SesTrustManager).
                    // The CIO handshake still verifies the hostname against the certificate.
                    trustManager = SesTrustManager.trustManager
                }
            }
        }

    /**
     * POSTs the given SOAP envelope to the SES endpoint and returns the response body.
     * Throws [SesHospedajesUnauthorizedException] on HTTP 401 and [SesHospedajesException]
     * on any other non-2xx status.
     */
    suspend fun post(soapEnvelope: String): String {
        val bodyBytes = soapEnvelope.toByteArray(Charsets.UTF_8)
        val startNanos = System.nanoTime()
        logger.info {
            "POST to SES ${environment.name} ($endpointUrl), body ${bodyBytes.size} bytes"
        }

        val response =
            try {
                client.post(endpointUrl) {
                    contentType(ContentType.Text.Xml.withParameter("charset", "utf-8"))
                    headers {
                        append(HttpHeaders.Authorization, "Basic $basicAuthToken")
                        append("SOAPAction", "")
                    }
                    // Send a ByteArray (not a String): it carries a known length, so CIO emits a
                    // definite Content-Length and never falls back to Transfer-Encoding: chunked,
                    // which the SES gateway rejects by closing the socket.
                    setBody(bodyBytes)
                }
            } catch (e: Throwable) {
                val elapsedMs = (System.nanoTime() - startNanos) / 1_000_000
                logger.error(e) {
                    "SES POST to ${environment.name} ($endpointUrl) failed after ${elapsedMs}ms " +
                        "before a response was read (body ${bodyBytes.size} bytes): ${e.message}"
                }
                throw e
            }

        val statusCode = response.status.value

        if (statusCode == 401) {
            throw SesHospedajesUnauthorizedException()
        }

        if (statusCode !in 200..299) {
            val errorBody = response.bodyAsText()
            logger.error { "SES Hospedajes API error: $statusCode, Response: $errorBody" }
            throw SesHospedajesException("SES Hospedajes API error ($statusCode): $errorBody")
        }

        return response.bodyAsText()
    }

    /** Closes the underlying HttpClient only if this instance created it. */
    fun close() {
        if (ownsHttpClient) {
            client.close()
        }
    }
}
