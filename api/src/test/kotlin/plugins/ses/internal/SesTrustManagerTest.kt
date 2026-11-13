package eu.kasify.plugins.ses.internal

import org.junit.Test
import java.security.cert.CertPathValidator
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.PKIXParameters
import java.security.cert.TrustAnchor
import java.security.cert.X509Certificate
import java.time.Instant
import java.util.Date
import kotlin.test.assertFailsWith

class SesTrustManagerTest {
    private fun loadCertificate(resource: String): X509Certificate =
        requireNotNull(javaClass.getResourceAsStream(resource)) { "missing test fixture $resource" }.use {
            CertificateFactory.getInstance("X.509").generateCertificate(it) as X509Certificate
        }

    @Test
    fun `SES certificate chains to the bundled trust anchors`() {
        // SES serves only its leaf (no intermediate). Validate the captured leaf at a fixed date
        // inside its validity window so the test doesn't break when SES rotates the certificate.
        val leaf = loadCertificate("/ses/tls/ses_production_leaf.pem")
        val anchors =
            SesTrustManager.trustManager.acceptedIssuers
                .map { TrustAnchor(it, null) }
                .toSet()
        val params =
            PKIXParameters(anchors).apply {
                isRevocationEnabled = false
                date = Date.from(Instant.parse("2026-10-26T00:00:00Z"))
            }
        val path = CertificateFactory.getInstance("X.509").generateCertPath(listOf(leaf))

        CertPathValidator.getInstance("PKIX").validate(path, params)
    }

    @Test
    fun `rejects a self-signed certificate impersonating SES`() {
        val impostor = loadCertificate("/ses/tls/self_signed_impostor.pem")

        // Ktor CIO passes "ECDHE_ECDSA" for ECDHE suites and "RSA" for RSA key exchange.
        listOf("ECDHE_ECDSA", "RSA").forEach { authType ->
            assertFailsWith<CertificateException> {
                SesTrustManager.trustManager.checkServerTrusted(arrayOf(impostor), authType)
            }
        }
    }
}
