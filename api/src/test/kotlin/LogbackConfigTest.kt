package eu.kasify

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.joran.JoranConfigurator
import org.junit.Assume.assumeTrue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * Loads the production `logback.xml` into an isolated [LoggerContext] (the global one is untouched)
 * to check that the application loggers follow `LOGBACK_LEVEL` instead of being pinned to DEBUG.
 */
class LogbackConfigTest {
    private fun <T> withConfiguredContext(
        logbackLevel: String?,
        block: (LoggerContext) -> T,
    ): T {
        val context = LoggerContext()
        // Context properties take precedence over system properties and env vars in logback's lookup.
        logbackLevel?.let { context.putProperty("LOGBACK_LEVEL", it) }
        try {
            JoranConfigurator()
                .apply { this.context = context }
                .doConfigure(requireNotNull(javaClass.classLoader.getResource("logback.xml")))
            return block(context)
        } finally {
            context.stop()
        }
    }

    @Test
    fun `app loggers honor LOGBACK_LEVEL=INFO so their DEBUG lines are not emitted`() {
        withConfiguredContext("INFO") { context ->
            assertEquals(Level.INFO, context.getLogger("eu.kasify").effectiveLevel)
            assertFalse(context.getLogger("eu.kasify.plugins.unifi.UnifiAccessPluginV2").isDebugEnabled)
            assertFalse(context.getLogger("eu.kasify.plugins.nuki.NukiPlugin").isDebugEnabled)
        }
    }

    @Test
    fun `app loggers default to DEBUG when LOGBACK_LEVEL is unset`() {
        assumeTrue(System.getenv("LOGBACK_LEVEL") == null && System.getProperty("LOGBACK_LEVEL") == null)
        withConfiguredContext(null) { context ->
            assertEquals(Level.DEBUG, context.getLogger("eu.kasify").effectiveLevel)
        }
    }
}
