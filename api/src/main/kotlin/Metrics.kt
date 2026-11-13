package eu.kasify

import eu.kasify.services.notifications.NotificationMetrics
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.metrics.micrometer.MicrometerMetrics
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.openapi.hide
import io.ktor.server.routing.routing
import io.ktor.utils.io.ExperimentalKtorApi
import io.micrometer.core.instrument.binder.jvm.ClassLoaderMetrics
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics
import io.micrometer.core.instrument.binder.system.ProcessorMetrics
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import java.time.Duration

private val logger = KotlinLogging.logger {}

@OptIn(ExperimentalKtorApi::class)
fun Application.configureMetrics() {
    val metricsToken = System.getenv("METRICS_TOKEN")
    if (metricsToken.isNullOrBlank()) {
        logger.warn { "METRICS_TOKEN not set — /metrics endpoint disabled" }
        return
    }

    val prometheusMeterRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
    // Expose the shared registry to background workers (notification delivery counters, etc.).
    NotificationMetrics.registry = prometheusMeterRegistry

    install(MicrometerMetrics) {
        registry = prometheusMeterRegistry
        distributionStatisticConfig =
            DistributionStatisticConfig
                .builder()
                .percentilesHistogram(true)
                .percentiles(0.5, 0.9, 0.95, 0.99)
                .serviceLevelObjectives(
                    Duration.ofMillis(100).toNanos().toDouble(),
                    Duration.ofMillis(500).toNanos().toDouble(),
                    Duration.ofSeconds(1).toNanos().toDouble(),
                ).build()
        meterBinders =
            listOf(
                JvmMemoryMetrics(),
                JvmGcMetrics(),
                JvmThreadMetrics(),
                ClassLoaderMetrics(),
                ProcessorMetrics(),
            )
    }

    routing {
        // Internal Prometheus scrape target — kept out of the public OpenAPI spec.
        get("/metrics") {
            val token = call.request.queryParameters["token"]
            if (token != metricsToken) {
                call.respond(HttpStatusCode.NotFound, "")
                return@get
            }
            call.respond(prometheusMeterRegistry.scrape())
        }.hide()
    }
}
