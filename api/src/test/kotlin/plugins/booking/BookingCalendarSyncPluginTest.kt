package eu.kasify.plugins.booking

import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.io.File
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BookingCalendarSyncPluginTest {
    private val plugin = BookingCalendarSyncPlugin()

    @Test
    fun `should parse calendar with blocked events`() =
        runBlocking {
            val calendarContent = getTestCalendarContent()
            val events = plugin.parseICalData(calendarContent)

            // Booking.com exposes every reservation as a blocked VEVENT, so each one is a booking.
            assertEquals(1, events.size)
        }

    @Test
    fun `should derive booking id from the UID local-part`() =
        runBlocking {
            val calendarContent = getTestCalendarContent()
            val events = plugin.parseICalData(calendarContent)

            val event = events.first()
            assertEquals("a95f04f150e74b10ad6b7628b69280a2", event.id)
            assertEquals("a95f04f150e74b10ad6b7628b69280a2@booking.com", event.uid)
        }

    @Test
    fun `should parse all event properties correctly`() =
        runBlocking {
            val calendarContent = getTestCalendarContent()
            val events = plugin.parseICalData(calendarContent)

            val event = events.first()
            assertNotNull(event.id, "Event should have an ID")
            assertTrue(event.id.isNotEmpty(), "Event ID should not be empty")
            assertNotNull(event.uid, "Event should have a UID")
            assertEquals("CLOSED - Not available", event.summary)
        }

    @Test
    fun `should parse dates correctly`() =
        runBlocking {
            val calendarContent = getTestCalendarContent()
            val events = plugin.parseICalData(calendarContent)

            val event = events.first()
            assertEquals(LocalDate.of(2026, 6, 26), event.startDateTime)
            assertEquals(LocalDate.of(2026, 6, 27), event.endDateTime)
            assertTrue(event.startDateTime < event.endDateTime, "Start should be before end")
        }

    @Test
    fun `should parse multiple blocked events`() =
        runBlocking {
            val multiEventContent =
                """
                BEGIN:VCALENDAR
                VERSION:2.0
                PRODID:-//admin.booking.com//NONSGML v1.0//EN
                BEGIN:VEVENT
                DTSTAMP:20260617T110811Z
                DTSTART;VALUE=DATE:20260626
                DTEND;VALUE=DATE:20260627
                UID:a95f04f150e74b10ad6b7628b69280a2@booking.com
                SUMMARY:CLOSED - Not available
                END:VEVENT
                BEGIN:VEVENT
                DTSTAMP:20260617T110811Z
                DTSTART;VALUE=DATE:20260701
                DTEND;VALUE=DATE:20260705
                UID:b14c2f9a7e3d4c01bb5a8d62f0917e44@booking.com
                SUMMARY:CLOSED - Not available
                END:VEVENT
                END:VCALENDAR
                """.trimIndent()

            val events = plugin.parseICalData(multiEventContent)

            assertEquals(2, events.size)
            assertEquals(
                listOf("a95f04f150e74b10ad6b7628b69280a2", "b14c2f9a7e3d4c01bb5a8d62f0917e44"),
                events.map { it.id },
            )
        }

    @Test
    fun `should skip events without a UID`() =
        runBlocking {
            val noUidContent =
                """
                BEGIN:VCALENDAR
                VERSION:2.0
                BEGIN:VEVENT
                DTSTART;VALUE=DATE:20260626
                DTEND;VALUE=DATE:20260627
                SUMMARY:CLOSED - Not available
                END:VEVENT
                END:VCALENDAR
                """.trimIndent()

            val events = plugin.parseICalData(noUidContent)

            assertTrue(events.isEmpty(), "Should skip events without a UID")
        }

    @Test
    fun `should handle malformed dates gracefully`() =
        runBlocking {
            val malformedContent =
                """
                BEGIN:VCALENDAR
                BEGIN:VEVENT
                UID:test@booking.com
                DTSTART;VALUE=DATE:invalid-date
                DTEND;VALUE=DATE:20260627
                SUMMARY:CLOSED - Not available
                END:VEVENT
                END:VCALENDAR
                """.trimIndent()

            val events = plugin.parseICalData(malformedContent)

            assertTrue(events.isEmpty(), "Should handle malformed data gracefully")
        }

    @Test
    fun `should handle empty calendar`() =
        runBlocking {
            val emptyContent =
                """
                BEGIN:VCALENDAR
                VERSION:2.0
                END:VCALENDAR
                """.trimIndent()

            val events = plugin.parseICalData(emptyContent)

            assertTrue(events.isEmpty(), "Should handle empty calendar")
        }

    @Test
    fun `should parse date formats correctly`() {
        // Test DATE format (YYYYMMDD)
        val dateOnly = plugin.parseDateTime("20260626")
        assertNotNull(dateOnly)
        assertEquals(2026, dateOnly.year)
        assertEquals(6, dateOnly.monthValue)
        assertEquals(26, dateOnly.dayOfMonth)

        // Test DATETIME with Z (UTC)
        val dateTimeUtc = plugin.parseDateTime("20260626T120000Z")
        assertNotNull(dateTimeUtc)
        assertEquals(12, dateTimeUtc.hour)

        // Test malformed date
        val malformed = plugin.parseDateTime("invalid")
        assertNull(malformed, "Should return null for invalid date")
    }

    private fun getTestCalendarContent(): String = File("src/test/resources/booking-calendar.ics").readText()
}
