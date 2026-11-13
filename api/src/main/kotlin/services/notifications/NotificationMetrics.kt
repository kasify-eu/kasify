package eu.kasify.services.notifications

import io.micrometer.core.instrument.MeterRegistry

/**
 * Delivery counters for the notifications outbox, exported via the same Prometheus registry as the
 * rest of the app. The registry is wired in `configureMetrics()` when `METRICS_TOKEN` is set; until
 * then (and in tests) every call is a no-op, so the dispatch worker can record metrics
 * unconditionally without a hard dependency on metrics being enabled.
 */
object NotificationMetrics {
    @Volatile
    var registry: MeterRegistry? = null

    fun sent(channel: String) = increment("notifications_sent_total", channel)

    fun failed(channel: String) = increment("notifications_failed_total", channel)

    fun dead(channel: String) = increment("notifications_dead_total", channel)

    private fun increment(
        metric: String,
        channel: String,
    ) {
        registry?.counter(metric, "channel", channel)?.increment()
    }
}
