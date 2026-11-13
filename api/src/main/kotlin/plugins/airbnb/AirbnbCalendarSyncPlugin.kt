package eu.kasify.plugins.airbnb

import eu.kasify.plugins.ical.IcalFeedFetcher
import io.github.oshai.kotlinlogging.KotlinLogging
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

data class AirbnbIcalEvent(
    val id: String,
    val uid: String,
    val summary: String,
    val description: String?,
    val startDateTime: LocalDate,
    val endDateTime: LocalDate,
)

const val PLUGIN_NAME = "AirbnbCalendarSync"

class AirbnbCalendarSyncPlugin(
    private val feedFetcher: IcalFeedFetcher = IcalFeedFetcher(),
) {
    private val logger = KotlinLogging.logger {}

    suspend fun getBookings(icalUrl: String): List<AirbnbIcalEvent> {
        try {
            logger.info { "Starting calendar sync" }

            val icalData = feedFetcher.fetch(icalUrl)
            val events = parseICalData(icalData)
            if (events.isEmpty()) {
                // Distinguishes a truly empty calendar (vevents=0) from a feed whose events were
                // all filtered out by parseEvent (vevents>0), e.g. an unexpected DESCRIPTION format.
                val vevents = Regex("BEGIN:VEVENT").findAll(icalData).count()
                logger.warn { "calendar.feed.no_events vevents=$vevents body_bytes=${icalData.length}" }
            }
            return events
        } catch (e: Exception) {
            logger.error(e) { "Calendar sync failed" }
            throw e
        }
    }

    fun parseICalData(icalData: String): List<AirbnbIcalEvent> {
        val events = mutableListOf<AirbnbIcalEvent>()
        val lines = icalData.lines()

        var currentEvent: MutableMap<String, String>? = null
        var multiLineProperty: String? = null
        val multiLineValue = StringBuilder()

        for (line in lines) {
            val trimmedLine = line.trim()

            if (trimmedLine.startsWith("BEGIN:VEVENT")) {
                currentEvent = mutableMapOf()
                continue
            }

            if (trimmedLine.startsWith("END:VEVENT")) {
                currentEvent?.let { event ->
                    // Finish any pending multiline property before processing
                    if (multiLineProperty != null) {
                        event[multiLineProperty] = multiLineValue.toString()
                        multiLineProperty = null
                        multiLineValue.clear()
                    }

                    parseEvent(event)?.let {
                        events.add(it)
                    }
                }
                currentEvent = null
                continue
            }

            if (currentEvent != null) {
                if (line.startsWith(" ") || line.startsWith("\t")) {
                    // Continuation line - append to current multiline value
                    multiLineValue.append(line.substring(1))
                } else {
                    // Finish any pending multiline property
                    if (multiLineProperty != null) {
                        currentEvent[multiLineProperty] = multiLineValue.toString()
                        multiLineProperty = null
                        multiLineValue.clear()
                    }

                    val colonIndex = trimmedLine.indexOf(':')
                    if (colonIndex > 0) {
                        val key = trimmedLine.take(colonIndex)
                        val value = trimmedLine.substring(colonIndex + 1)

                        // Start a new property (potentially multiline)
                        multiLineProperty = key
                        multiLineValue.append(value)
                    }
                }
            }
        }

        if (multiLineProperty != null && currentEvent != null) {
            currentEvent[multiLineProperty] = multiLineValue.toString()
        }

        return events
    }

    private fun parseEvent(eventData: Map<String, String>): AirbnbIcalEvent? {
        val uid = eventData["UID"] ?: return null
        val summary = eventData["SUMMARY"] ?: "Airbnb Booking"
        val startStr = eventData["DTSTART;VALUE=DATE"] ?: return null
        val endStr = eventData["DTEND;VALUE=DATE"] ?: return null
        val description = eventData["DESCRIPTION"] ?: ""

        // Only process events with reservation details (skip "Not available" events)
        if (!description.contains("Reservation URL")) {
            return null
        }

        val reservationIdMatch = """details/([A-Z0-9]+)""".toRegex().find(description)
        val reservationId = reservationIdMatch?.groupValues?.get(1) ?: return null

        val startDateTime =
            LocalDate.parse("${startStr.take(4)}-${startStr.substring(4, 6)}-${startStr.substring(6, 8)}")
        val endDateTime =
            LocalDate.parse("${endStr.take(4)}-${endStr.substring(4, 6)}-${endStr.substring(6, 8)}")

        return AirbnbIcalEvent(reservationId, uid, summary, description, startDateTime, endDateTime)
    }

    fun parseDateTime(dateStr: String): OffsetDateTime? =
        try {
            when {
                dateStr.contains('T') && dateStr.endsWith('Z') -> {
                    val localDateTime =
                        LocalDateTime.parse(dateStr.dropLast(1), DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss"))
                    localDateTime.atOffset(ZoneOffset.UTC)
                }

                dateStr.contains('T') -> {
                    val localDateTime = LocalDateTime.parse(dateStr, DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss"))
                    localDateTime.atOffset(ZoneOffset.UTC)
                }

                else -> {
                    val localDate =
                        LocalDateTime.parse(dateStr + "T00:00:00", DateTimeFormatter.ofPattern("yyyyMMdd'T'HH:mm:ss"))
                    localDate.atOffset(ZoneOffset.UTC)
                }
            }
        } catch (e: Exception) {
            logger.warn(e) { "Failed to parse date: $dateStr" }
            null
        }
}
