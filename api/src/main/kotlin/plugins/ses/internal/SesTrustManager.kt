package eu.kasify.plugins.ses.internal

import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * TLS trust for the SES Hospedajes endpoints: only the FNMT-RCM CAs bundled in
 * [CA_BUNDLE_RESOURCE] are trusted, with standard PKIX validation of the server certificate.
 *
 * SES serves its leaf certificate without the issuing FNMT-RCM intermediate, so the chain cannot
 * be built from the JVM default trust store. Bundling that intermediate (and its root) as trust
 * anchors lets the chain validate without trusting arbitrary certificates. Hostname verification
 * is performed on top of this by the Ktor CIO TLS handshake.
 */
internal object SesTrustManager {
    internal const val CA_BUNDLE_RESOURCE = "/ses/fnmt-ca-bundle.pem"

    /** Shared, thread-safe trust manager built from [CA_BUNDLE_RESOURCE]. */
    val trustManager: X509TrustManager by lazy { fromBundle() }

    private fun fromBundle(): X509TrustManager {
        val stream =
            requireNotNull(SesTrustManager::class.java.getResourceAsStream(CA_BUNDLE_RESOURCE)) {
                "SES CA bundle $CA_BUNDLE_RESOURCE not found on the classpath"
            }
        val certificates =
            stream.use {
                CertificateFactory
                    .getInstance("X.509")
                    .generateCertificates(it)
                    .filterIsInstance<X509Certificate>()
            }
        check(certificates.isNotEmpty()) { "SES CA bundle $CA_BUNDLE_RESOURCE contains no certificates" }

        val keyStore =
            KeyStore.getInstance(KeyStore.getDefaultType()).apply {
                load(null, null)
                certificates.forEachIndexed { index, certificate ->
                    setCertificateEntry("ses-ca-$index", certificate)
                }
            }
        val factory =
            TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply {
                init(keyStore)
            }
        return factory.trustManagers.filterIsInstance<X509TrustManager>().first()
    }
}
