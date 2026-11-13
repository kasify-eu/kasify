package eu.kasify.jobs
import eu.kasify.db.BookingsRepository
import eu.kasify.db.GuestsRepository
import eu.kasify.db.PropertiesRepository
import eu.kasify.db.PropertyChannelIntegrationsRepository
import eu.kasify.db.TasksRepository
import eu.kasify.db.model.BookingChannel
import eu.kasify.db.model.BookingStatus
import eu.kasify.model.PropertyChannelIntegration
import eu.kasify.model.api.requests.BookingData
import eu.kasify.plugins.airbnb.AirbnbCalendarSyncPlugin
import eu.kasify.plugins.booking.BookingCalendarSyncPlugin
import eu.kasify.services.BookingService
import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.oshai.kotlinlogging.withLoggingContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.slf4j.MDCContext
import kotlinx.coroutines.withContext
import org.quartz.Job
import org.quartz.JobExecutionContext
import java.net.URI
import java.time.LocalDate
import kotlin.uuid.Uuid

private val logger = KotlinLogging.logger {}

/**
 * Channel-agnostic iCal import job. Each due [PropertyChannelIntegration] is dispatched to the
 * calendar plugin that matches its [PropertyChannelIntegration.channel], and bookings are upserted
 * (and stale ones cancelled) under that same channel. Adding a new iCal channel only requires a new
 * branch in [fetchEvents].
 */
class CalendarImportJob : Job {
    val bookingsRepository = BookingsRepository()
    val propertyChannelIntegrationsRepository = PropertyChannelIntegrationsRepository()
    val bookingService =
        BookingService(
            bookingsRepository = bookingsRepository,
            tasksRepository = TasksRepository(),
            propertiesRepository = PropertiesRepository(),
            guestsRepository = GuestsRepository(),
        )

    private val airbnbPlugin = AirbnbCalendarSyncPlugin()
    private val bookingPlugin = BookingCalendarSyncPlugin()

    override fun execute(p0: JobExecutionContext?) {
        val fireId = p0?.fireInstanceId ?: "unknown"
        // MDCContext snapshots the MDC map at construction and re-installs that snapshot on every
        // coroutine resumption — so it must be created *after* withLoggingContext has populated the
        // MDC, or the entries vanish at the first suspension (e.g. dbQuery's thread switch).
        runBlocking {
            withLoggingContext("fire_id" to fireId) {
                withContext(MDCContext()) {
                    val integrations = propertyChannelIntegrationsRepository.getIntegrationsNeedingExecution()

                    logger.info { "calendar.sync.start integrations=${integrations.size}" }

                    var succeeded = 0
                    var failed = 0
                    val startedAt = System.currentTimeMillis()
                    integrations.forEach { integration ->
                        if (syncIntegration(integration)) succeeded++ else failed++
                    }
                    val durationMs = System.currentTimeMillis() - startedAt

                    logger.info { "calendar.sync.done succeeded=$succeeded failed=$failed duration_ms=$durationMs" }
                }
            }
        }
    }

    private suspend fun syncIntegration(integration: PropertyChannelIntegration): Boolean =
        withLoggingContext(
            "integration_id" to integration.id.toString(),
            "property_id" to integration.propertyId.toString(),
            "channel" to integration.channel.name,
        ) {
            // Fresh MDCContext so the integration's MDC entries survive suspensions inside this
            // block (fetch, dbQuery); the outer context's snapshot predates them.
            withContext(MDCContext()) {
                val host = redactedHost(integration.url)
                val integrationStartedAt = System.currentTimeMillis()
                logger.info { "calendar.sync.integration.start host=$host channel=${integration.channel}" }

                try {
                    val fetchStartedAt = System.currentTimeMillis()
                    val events = fetchEvents(integration)
                    if (events == null) {
                        logger.warn { "calendar.sync.integration.unsupported channel=${integration.channel} host=$host" }
                        return@withContext false
                    }
                    val fetchMs = System.currentTimeMillis() - fetchStartedAt
                    val feedChannelIds = events.map { it.id }.toSet()

                    var inserted = 0
                    var updated = 0
                    var unchanged = 0
                    var cancelled = 0

                    events.forEach { event ->
                        when (upsertBooking(integration.propertyId, integration.channel, event)) {
                            UpsertOutcome.Inserted -> inserted++
                            UpsertOutcome.Updated -> updated++
                            UpsertOutcome.Unchanged -> unchanged++
                        }
                    }

                    // Empty feed almost always means upstream/parser failure rather than a property
                    // legitimately having zero future bookings. Skip the cancellation pass so a
                    // transient blip can't wipe out every confirmed booking on the property.
                    if (events.isEmpty()) {
                        logger.warn { "calendar.sync.feed.empty host=$host skipping_cancellation=true" }
                    } else {
                        val futureBookings =
                            bookingsRepository.getFutureConfirmedBookingsByPropertyAndChannel(
                                integration.propertyId,
                                integration.channel,
                            )
                        futureBookings.forEach { existingBooking ->
                            if (existingBooking.channelId !in feedChannelIds) {
                                bookingsRepository.cancelBooking(existingBooking.id)
                                cancelled++
                                logger.warn {
                                    "calendar.sync.booking.cancel booking_id=${existingBooking.id} " +
                                        "channel_id=${existingBooking.channelId}"
                                }
                            }
                        }
                    }

                    propertyChannelIntegrationsRepository.markSyncSucceeded(integration.id)
                    val totalMs = System.currentTimeMillis() - integrationStartedAt
                    logger.info {
                        "calendar.sync.integration.done inserted=$inserted updated=$updated " +
                            "unchanged=$unchanged cancelled=$cancelled feed_size=${events.size} " +
                            "fetch_ms=$fetchMs total_ms=$totalMs next_in_min=${integration.syncIntervalMinutes}"
                    }
                    true
                } catch (e: Exception) {
                    val totalMs = System.currentTimeMillis() - integrationStartedAt
                    val errorMessage = (e.message ?: e::class.simpleName ?: "Unknown error").take(1000)
                    logger.error(e) {
                        "calendar.sync.integration.failed host=$host " +
                            "error_class=${e::class.simpleName} total_ms=$totalMs"
                    }
                    propertyChannelIntegrationsRepository.markSyncFailed(integration.id, errorMessage)
                    false
                }
            }
        }

    /**
     * Dispatches to the calendar plugin matching the integration's channel and normalizes its
     * events. Returns null for channels that have no iCal plugin (so the caller can skip them).
     */
    private suspend fun fetchEvents(integration: PropertyChannelIntegration): List<CalendarEvent>? =
        when (integration.channel) {
            BookingChannel.Airbnb ->
                airbnbPlugin.getBookings(integration.url).map {
                    CalendarEvent(it.id, it.summary, it.description, it.startDateTime, it.endDateTime)
                }

            BookingChannel.Booking ->
                bookingPlugin.getBookings(integration.url).map {
                    CalendarEvent(it.id, it.summary, it.description, it.startDateTime, it.endDateTime)
                }

            BookingChannel.Direct, BookingChannel.Vrbo, BookingChannel.Google -> null
        }

    private suspend fun upsertBooking(
        propertyId: Uuid,
        channel: BookingChannel,
        event: CalendarEvent,
    ): UpsertOutcome {
        val importedBookingData =
            BookingData(
                from = event.startDateTime,
                to = event.endDateTime,
                channel = channel,
                channelId = event.id,
                notes = listOfNotNull(event.summary, event.description).joinToString("; "),
                status = BookingStatus.Confirmed,
            )
        val existingBooking = bookingsRepository.getBookingByPropertyAndChannelId(propertyId, event.id)

        if (existingBooking == null) {
            bookingService.createBookingWithCleaningTask(importedBookingData, propertyId)
            logger.debug { "calendar.sync.booking.upsert action=inserted channel_id=${event.id}" }
            return UpsertOutcome.Inserted
        }
        if (!existingBooking.isEquals(importedBookingData)) {
            bookingsRepository.updateBooking(existingBooking.id, importedBookingData)
            logger.debug {
                "calendar.sync.booking.upsert action=updated booking_id=${existingBooking.id} " +
                    "channel_id=${event.id}"
            }
            return UpsertOutcome.Updated
        }
        logger.debug {
            "calendar.sync.booking.upsert action=noop booking_id=${existingBooking.id} " +
                "channel_id=${event.id}"
        }
        return UpsertOutcome.Unchanged
    }

    private fun redactedHost(url: String): String = runCatching { URI(url).host ?: "unknown" }.getOrDefault("invalid")

    /** Normalized shape shared by every calendar plugin so the upsert logic stays channel-agnostic. */
    private data class CalendarEvent(
        val id: String,
        val summary: String,
        val description: String?,
        val startDateTime: LocalDate,
        val endDateTime: LocalDate,
    )

    private enum class UpsertOutcome { Inserted, Updated, Unchanged }
}
