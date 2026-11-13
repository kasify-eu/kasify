package eu.kasify.services

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

private val logger = KotlinLogging.logger {}

/**
 * Retry [block] up to [maxAttempts] times with exponential backoff. Used to absorb transient PG
 * failures on writes that must not be lost (post-SES-success persistence in
 * [SesHospedajesService], the one-shot owner notification in [SesCredentialsGuard]). Re-throws
 * the last exception after exhaustion; per-attempt failures are logged at WARN, the exhaustion at
 * ERROR. [label] is included in log lines so multi-step retry traces are readable.
 */
internal suspend fun <T> retryDb(
    label: String,
    maxAttempts: Int = 5,
    initialDelayMs: Long = 100,
    maxDelayMs: Long = 2_000,
    block: suspend () -> T,
): T {
    var attempt = 0
    var delayMs = initialDelayMs
    while (true) {
        attempt++
        try {
            return block()
        } catch (e: Exception) {
            if (attempt >= maxAttempts) {
                logger.error(e) { "retryDb '$label' exhausted after $attempt attempts" }
                throw e
            }
            logger.warn(e) { "retryDb '$label' attempt $attempt failed, retrying in ${delayMs}ms" }
            delay(delayMs.milliseconds)
            delayMs = (delayMs * 2).coerceAtMost(maxDelayMs)
        }
    }
}
