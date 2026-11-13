package eu.kasify

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.compression.Compression
import io.ktor.server.plugins.compression.deflate
import io.ktor.server.plugins.compression.gzip
import io.ktor.server.plugins.compression.minimumSize
import io.ktor.server.plugins.defaultheaders.DefaultHeaders
import io.ktor.server.plugins.forwardedheaders.XForwardedHeaders

fun Application.configureHTTP() {
    // Trust X-Forwarded-* set by upstream proxies (the LB and the dashboard's
    // /api/* proxy at dashboard/app/routes/api.$.tsx). With this installed,
    // call.request.origin.{remoteHost, scheme, host} reflect the original
    // client instead of the immediate hop. Required for per-IP rate limiting.
    install(XForwardedHeaders)

    install(DefaultHeaders) {
        header("X-Content-Type-Options", "nosniff")
        header("X-Frame-Options", "DENY")
        header("Strict-Transport-Security", "max-age=31536000; includeSubDomains")
        header("Referrer-Policy", "strict-origin-when-cross-origin")
    }
    install(Compression) {
        gzip {
            priority = 1.0
            minimumSize(1024)
        }
        deflate {
            priority = 0.9
            minimumSize(1024)
        }
    }
}
