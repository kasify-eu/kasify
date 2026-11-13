package eu.kasify

import kotlin.test.Test
import kotlin.test.assertEquals

class CorsTest {
    @Test
    fun `defaults to the local dev dashboard when unset or blank`() {
        assertEquals(listOf("localhost:5173"), parseAllowedHosts(null))
        assertEquals(listOf("localhost:5173"), parseAllowedHosts(" , "))
    }

    @Test
    fun `parses a comma separated list and trims entries`() {
        assertEquals(
            listOf("app.example.com", "localhost:5173"),
            parseAllowedHosts(" app.example.com , localhost:5173,"),
        )
    }
}
