package eu.kasify.plugins.airbnb

import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AirbnbCalendarSyncPluginTest {
    private val plugin = AirbnbCalendarSyncPlugin()

    @Test
    fun `should parse calendar with multiple events`() =
        runBlocking {
            val calendarContent = getTestCalendarContent()
            val events = plugin.parseICalData(calendarContent)

            assertTrue(events.isNotEmpty(), "Should parse at least one event")
            // Note: Only events with reservation details are parsed, not "Not available" events
            assertEquals(13, events.size)
        }

    @Test
    fun `should parse event with multiline description correctly`() =
        runBlocking {
            val calendarContent = getTestCalendarContent()
            val events = plugin.parseICalData(calendarContent)

            val firstEvent = events.first()
            assertNotNull(firstEvent.description, "Description should not be null")
            assertTrue(
                firstEvent.description!!.contains("HMTD8T9QYX"),
                "Description should contain the full reservation ID",
            )
            assertTrue(
                firstEvent.description!!.contains("Phone Number (Last 4 Digits): 4787"),
                "Description should contain the complete phone number line",
            )
        }

    @Test
    fun `should extract reservation ID from description`() =
        runBlocking {
            val calendarContent = getTestCalendarContent()
            val events = plugin.parseICalData(calendarContent)

            val expectedReservationIds =
                listOf(
                    "HMTD8T9QYX",
                    "HMHCKQS4QB",
                    "HM5BAYWSPS",
                    "HMSRPMWDB4",
                    "HMNMCQFK3F",
                    "HM5R2SDNZZ",
                    "HMKJ4ZQD8J",
                    "HMJZ4ZCTJW",
                    "HMQ5ENTHY8",
                    "HM8PHEZDKP",
                    "HMHNXP9P2R",
                    "HMXXQRZSDH",
                    "HMZ2SF8MXW",
                )

            val actualIds = events.map { it.id }

            // Check some specific IDs that should definitely be there
            expectedReservationIds.forEach { expectedId ->
                assertTrue(
                    actualIds.contains(expectedId),
                    "Should extract reservation ID: $expectedId. Actual IDs: $actualIds",
                )
            }
        }

    @Test
    fun `should parse dates correctly`() =
        runBlocking {
            val calendarContent = getTestCalendarContent()
            val events = plugin.parseICalData(calendarContent)

            val firstEvent = events.first()

            // Dates should be parsed as start of day in UTC
            assertNotNull(firstEvent.startDateTime, "Start date should be parsed")
            assertNotNull(firstEvent.endDateTime, "End date should be parsed")
            assertTrue(firstEvent.startDateTime < firstEvent.endDateTime, "Start should be before end")
        }

    @Test
    fun `should parse all event properties correctly`() =
        runBlocking {
            val calendarContent = getTestCalendarContent()
            val events = plugin.parseICalData(calendarContent)

            val firstEvent = events.first()

            // Check that the first event has expected structure
            assertNotNull(firstEvent.id, "Event should have an ID")
            assertTrue(firstEvent.id.isNotEmpty(), "Event ID should not be empty")
            assertNotNull(firstEvent.uid, "Event should have a UID")
            assertEquals("Reserved", firstEvent.summary)
            assertNotNull(firstEvent.description)
            assertTrue(firstEvent.description!!.contains("Reservation URL"), "Description should contain reservation URL")
        }

    @Test
    fun `should parse calendar with only not-available events as empty`() =
        runBlocking {
            val calendarContent = File("src/test/resources/airbnb-calendar-lof.ics").readText()
            val events = plugin.parseICalData(calendarContent)

            assertTrue(
                events.isEmpty(),
                "Calendar with only 'Not available' blocks should yield no bookings. Got: $events",
            )
        }

    @Test
    fun `should handle events without reservation details`() =
        runBlocking {
            val simpleEventContent =
                """
                BEGIN:VCALENDAR
                VERSION:2.0
                BEGIN:VEVENT
                UID:test-uid@airbnb.com
                DTSTART;VALUE=DATE:20250630
                DTEND;VALUE=DATE:20250707
                SUMMARY:Airbnb (Not available)
                END:VEVENT
                END:VCALENDAR
                """.trimIndent()

            val events = plugin.parseICalData(simpleEventContent)

            assertTrue(events.isEmpty(), "Should not return events without reservation details")
        }

    @Test
    fun `should handle malformed calendar data gracefully`() =
        runBlocking {
            val malformedContent =
                """
                BEGIN:VCALENDAR
                BEGIN:VEVENT
                UID:test
                DTSTART;VALUE=DATE:invalid-date
                DTEND;VALUE=DATE:20250707
                SUMMARY:Test
                DESCRIPTION:No reservation details
                END:VEVENT
                END:VCALENDAR
                """.trimIndent()

            val events = plugin.parseICalData(malformedContent)

            assertTrue(events.isEmpty(), "Should handle malformed data gracefully")
        }

    @Test
    fun `should parse date formats correctly`() {
        val plugin = AirbnbCalendarSyncPlugin()

        // Test DATE format (YYYYMMDD)
        val dateOnly = plugin.parseDateTime("20250630")
        assertNotNull(dateOnly)
        assertEquals(2025, dateOnly.year)
        assertEquals(6, dateOnly.monthValue)
        assertEquals(30, dateOnly.dayOfMonth)

        // Test DATETIME with Z (UTC)
        val dateTimeUtc = plugin.parseDateTime("20250630T120000Z")
        assertNotNull(dateTimeUtc)
        assertEquals(12, dateTimeUtc.hour)

        // Test malformed date
        val malformed = plugin.parseDateTime("invalid")
        assertNull(malformed, "Should return null for invalid date")
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
    fun `should handle multiline properties correctly`() =
        runBlocking {
            val multilineContent =
                """
                BEGIN:VCALENDAR
                VERSION:2.0
                BEGIN:VEVENT
                UID:test@airbnb.com
                DTSTART;VALUE=DATE:20250630
                DTEND;VALUE=DATE:20250707
                SUMMARY:Reserved
                DESCRIPTION:Reservation URL: https://www.airbnb.com/hosting/reservations/de
                 tails/TESTID123\nPhone Number (Last 4 Digits): 1234
                END:VEVENT
                END:VCALENDAR
                """.trimIndent()

            val events = plugin.parseICalData(multilineContent)

            assertEquals(1, events.size, "Should parse exactly 1 event")
            val event = events.first()
            assertEquals("TESTID123", event.id, "Should extract correct reservation ID")
            assertTrue(event.description!!.contains("details/TESTID123"), "Description should contain reservation details")
            assertTrue(
                event.description!!.contains("Phone Number (Last 4 Digits): 1234"),
                "Description should contain phone number",
            )
        }

    private fun getTestCalendarContent(): String = File("src/test/resources/airbnb-calendar.ics").readText()
}
