package eu.kasify.helpers

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.spi.IThrowableProxy
import ch.qos.logback.core.read.ListAppender
import org.slf4j.LoggerFactory
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Characters that start a new line in a plain-text log file or in a log viewer. */
private val LOG_LINE_BREAKS = charArrayOf('\r', '\n', '\u0085', '\u2028', '\u2029')

private const val MAX_CHAIN_DEPTH = 20

private fun String.hasLogLineBreak(): Boolean = indexOfAny(LOG_LINE_BREAKS) >= 0

/**
 * Runs [block] with an in-memory appender on the root logger and returns the events that
 * Kasify's own loggers (`eu.kasify.*`) emitted while it ran.
 */
suspend fun captureKasifyLogs(block: suspend () -> Unit): List<ILoggingEvent> {
    val root = LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME) as Logger
    val appender = ListAppender<ILoggingEvent>().apply { start() }
    root.addAppender(appender)
    try {
        block()
    } finally {
        root.detachAppender(appender)
        appender.stop()
    }
    return appender.list.filter { it.loggerName.startsWith("eu.kasify") }
}

/** Class names and messages of [proxy], all of its causes and all suppressed throwables. */
fun throwableProxyTexts(
    proxy: IThrowableProxy?,
    depth: Int = 0,
): List<String> {
    if (proxy == null || depth > MAX_CHAIN_DEPTH) return emptyList()
    return listOfNotNull(proxy.className, proxy.message) +
        throwableProxyTexts(proxy.cause, depth + 1) +
        proxy.suppressed.orEmpty().flatMap { throwableProxyTexts(it, depth + 1) }
}

/** Class names and messages of [error], all of its causes and all suppressed throwables. */
fun throwableChainTexts(
    error: Throwable?,
    depth: Int = 0,
): List<String> {
    if (error == null || depth > MAX_CHAIN_DEPTH) return emptyList()
    return listOfNotNull(error.javaClass.name, error.message) +
        throwableChainTexts(error.cause, depth + 1) +
        error.suppressed.flatMap { throwableChainTexts(it, depth + 1) }
}

/**
 * Asserts that none of [events] can forge a log line (neither the formatted message nor the
 * class name / message of any throwable in the event's chain contains CR, LF, NEL, U+2028 or
 * U+2029) and that [marker], the attacker-supplied text, did reach the log, so the assertion
 * really covered the attacker's path.
 */
fun assertNoForgedLogLines(
    events: List<ILoggingEvent>,
    marker: String,
) {
    for (event in events) {
        assertFalse(
            event.formattedMessage.orEmpty().hasLogLineBreak(),
            "Log message from ${event.loggerName} contains a line break: ${event.formattedMessage}",
        )
        for (text in throwableProxyTexts(event.throwableProxy)) {
            assertFalse(
                text.hasLogLineBreak(),
                "Logged throwable chain from ${event.loggerName} contains a line break: $text",
            )
        }
    }
    assertTrue(
        events.any { event ->
            event.formattedMessage.orEmpty().contains(marker) ||
                throwableProxyTexts(event.throwableProxy).any { it.contains(marker) }
        },
        "Expected the attacker-supplied text '$marker' to be logged (escaped)",
    )
}

/** Asserts that neither [error] nor anything in its chain carries a line break in its message. */
fun assertNoLineBreaksInChain(error: Throwable) {
    for (text in throwableChainTexts(error)) {
        assertFalse(text.hasLogLineBreak(), "Exception chain contains a line break: $text")
    }
}
