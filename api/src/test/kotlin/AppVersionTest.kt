package eu.kasify

import kotlin.test.Test
import kotlin.test.assertTrue

class AppVersionTest {
    @Test
    fun `version is the one generated from the build, not a placeholder`() {
        assertTrue(Regex("""\d+\.\d+\.\d+.*""").matches(AppVersion.current), "Unexpected version: ${AppVersion.current}")
    }
}
