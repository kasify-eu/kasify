package eu.kasify.db

import eu.kasify.TEST_ACCOUNT_1_PROPERTY_1
import eu.kasify.db.model.BookingChannel
import eu.kasify.extensions.formatLocalDateTime
import eu.kasify.routes.BaseRouteDBTests
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class PropertyChannelIntegrationsRepositoryTest : BaseRouteDBTests() {
    private val service = PropertyChannelIntegrationsRepository()

    private val testIntegration1Id = Uuid.Companion.parse("50000000-0000-0000-0000-000000000001")
    private val testIntegration2Id = Uuid.Companion.parse("50000000-0000-0000-0000-000000000002")
    private val testIntegration3Id = Uuid.Companion.parse("50000000-0000-0000-0000-000000000003")

    override fun setupData() {
        val now = LocalDateTime.now()
        val oneHourAgo = now.minusHours(1)
        val oneHourFromNow = now.plusHours(1)

        val nowFormatted = now.formatLocalDateTime()
        val oneHourAgoFormatted = oneHourAgo.formatLocalDateTime()
        val oneHourFromNowFormatted = oneHourFromNow.formatLocalDateTime()

        // Integration 1: Enabled, never synced (should be included)
        testDBConnection.executeQuery(
            """
            INSERT INTO property_channel_integrations 
            (id, property_id, channel, url, sync_enabled, sync_interval_minutes, last_sync_at, next_sync_at, last_error, error_count, created_at, updated_at) 
            VALUES(
                '$testIntegration1Id', 
                '${TEST_ACCOUNT_1_PROPERTY_1.id}', 
                'airbnb', 
                'https://calendar.airbnb.com/calendar/ical/123.ics',
                true, 
                60, 
                NULL, 
                NULL, 
                NULL, 
                0, 
                '$nowFormatted', 
                '$nowFormatted'
            )
            """.trimIndent(),
        )

        // Integration 2: Enabled, next sync is in the past (should be included)
        testDBConnection.executeQuery(
            """
            INSERT INTO property_channel_integrations 
            (id, property_id, channel, url, sync_enabled, sync_interval_minutes, last_sync_at, next_sync_at, last_error, error_count, created_at, updated_at) 
            VALUES(
                '$testIntegration2Id', 
                '${TEST_ACCOUNT_1_PROPERTY_1.id}', 
                'booking', 
                'https://secure-supply-xml.booking.com/hotels/ical/123.ics',
                true, 
                120, 
                '$oneHourAgoFormatted', 
                '$oneHourAgoFormatted', 
                NULL, 
                0, 
                '$nowFormatted', 
                '$nowFormatted'
            )
            """.trimIndent(),
        )

        // Integration 3: Enabled, but next sync is in the future (should NOT be included)
        testDBConnection.executeQuery(
            """
            INSERT INTO property_channel_integrations 
            (id, property_id, channel, url, sync_enabled, sync_interval_minutes, last_sync_at, next_sync_at, last_error, error_count, created_at, updated_at) 
            VALUES(
                '$testIntegration3Id', 
                '${TEST_ACCOUNT_1_PROPERTY_1.id}', 
                'vrbo', 
                'https://www.vrbo.com/calendar/ical/123.ics',
                true, 
                180, 
                '$nowFormatted', 
                '$oneHourFromNowFormatted', 
                NULL, 
                0, 
                '$nowFormatted', 
                '$nowFormatted'
            )
            """.trimIndent(),
        )
    }

    @Test
    fun testGetIntegrationsNeedingExecution() =
        runBlocking {
            connectKasifyTestsDB()
            val integrations = service.getIntegrationsNeedingExecution()

            // Should return integration 1 (never synced) and integration 2 (past due)
            assertEquals(2, integrations.size)

            val integration1 = integrations.find { it.id == testIntegration1Id }
            val integration2 = integrations.find { it.id == testIntegration2Id }

            assertTrue(integration1 != null, "Integration 1 should be included (never synced)")
            assertTrue(integration2 != null, "Integration 2 should be included (past due)")

            // Verify integration 1 details
            assertEquals(TEST_ACCOUNT_1_PROPERTY_1.id, integration1.propertyId)
            assertEquals(BookingChannel.Airbnb, integration1.channel)
            assertEquals("https://calendar.airbnb.com/calendar/ical/123.ics", integration1.url)
            assertEquals(true, integration1.syncEnabled)
            assertEquals(60, integration1.syncIntervalMinutes)
            assertEquals(null, integration1.lastSyncAt)
            assertEquals(0, integration1.errorCount)

            // Verify integration 2 details
            assertEquals(TEST_ACCOUNT_1_PROPERTY_1.id, integration2.propertyId)
            assertEquals(BookingChannel.Booking, integration2.channel)
            assertEquals("https://secure-supply-xml.booking.com/hotels/ical/123.ics", integration2.url)
            assertEquals(true, integration2.syncEnabled)
            assertEquals(120, integration2.syncIntervalMinutes)
            assertTrue(integration2.lastSyncAt != null, "Integration 2 should have lastSyncAt")
            assertEquals(0, integration2.errorCount)
        }

    @Test
    fun testGetAllIntegrations() =
        runBlocking {
            connectKasifyTestsDB()

            val integrations = service.getAllIntegrations()

            // Should return all 3 integrations
            assertEquals(3, integrations.size)

            val channels = integrations.map { it.channel }.toSet()
            assertEquals(setOf(BookingChannel.Airbnb, BookingChannel.Booking, BookingChannel.Vrbo), channels)
        }

    @Test
    fun testGetIntegrationsByPropertyId() =
        runBlocking {
            connectKasifyTestsDB()

            val integrations = service.getIntegrationsByPropertyId(TEST_ACCOUNT_1_PROPERTY_1.id)

            // Should return all 3 integrations for this property
            assertEquals(3, integrations.size)

            // All should belong to the same property
            assertTrue(integrations.all { it.propertyId == TEST_ACCOUNT_1_PROPERTY_1.id })
        }

    @Test
    fun testUpsertWithChangedUrlResetsSyncSchedule() =
        runBlocking {
            connectKasifyTestsDB()

            // Integration 3 (vrbo) has a future next_sync_at, so it is not due.
            assertTrue(service.getIntegrationsNeedingExecution().none { it.id == testIntegration3Id })

            val updated =
                service.upsertPropertyChannelIntegration(
                    "https://www.vrbo.com/calendar/ical/456-new.ics",
                    TEST_ACCOUNT_1_PROPERTY_1.id,
                    BookingChannel.Vrbo,
                )

            assertEquals(testIntegration3Id, updated.id)
            assertEquals("https://www.vrbo.com/calendar/ical/456-new.ics", updated.url)
            assertEquals(null, updated.lastSyncAt, "Changed URL should clear lastSyncAt")
            assertEquals(null, updated.nextSyncAt, "Changed URL should clear nextSyncAt")
            assertEquals(0, updated.errorCount)

            // The cleared schedule makes the new URL due immediately.
            assertTrue(service.getIntegrationsNeedingExecution().any { it.id == testIntegration3Id })
        }

    @Test
    fun testFailedFirstSyncAfterUrlChangeHonorsRetryBackoff() =
        runBlocking {
            connectKasifyTestsDB()

            service.upsertPropertyChannelIntegration(
                "https://www.vrbo.com/calendar/ical/456-new.ics",
                TEST_ACCOUNT_1_PROPERTY_1.id,
                BookingChannel.Vrbo,
            )
            assertTrue(service.getIntegrationsNeedingExecution().any { it.id == testIntegration3Id })

            // First sync of the new URL fails: the retry must wait out the interval
            // instead of re-running on every job tick just because lastSyncAt is null.
            service.markSyncFailed(testIntegration3Id, "boom")

            assertTrue(
                service.getIntegrationsNeedingExecution().none { it.id == testIntegration3Id },
                "a failed first sync should be rescheduled by markSyncFailed, not retried on every tick",
            )
        }

    @Test
    fun testUpsertWithSameUrlKeepsSyncSchedule() =
        runBlocking {
            connectKasifyTestsDB()

            val updated =
                service.upsertPropertyChannelIntegration(
                    "https://www.vrbo.com/calendar/ical/123.ics",
                    TEST_ACCOUNT_1_PROPERTY_1.id,
                    BookingChannel.Vrbo,
                )

            assertEquals(testIntegration3Id, updated.id)
            assertTrue(updated.lastSyncAt != null, "Unchanged URL should keep lastSyncAt")
            assertTrue(updated.nextSyncAt != null, "Unchanged URL should keep nextSyncAt")
            assertTrue(service.getIntegrationsNeedingExecution().none { it.id == testIntegration3Id })
        }

    @Test
    fun testGraceWindowIncludesIntegrationsDueMomentsAfterTheTick() =
        runBlocking {
            connectKasifyTestsDB()

            val dueWithinGraceId = Uuid.parse("50000000-0000-0000-0000-000000000004")
            val dueBeyondGraceId = Uuid.parse("50000000-0000-0000-0000-000000000005")

            // next_sync_at = NOW() + interval lands moments after the quartz tick one interval
            // later; without the grace window this integration would slip a whole extra tick.
            testDBConnection.executeQuery(
                """
                INSERT INTO property_channel_integrations
                (id, property_id, channel, url, sync_enabled, sync_interval_minutes, last_sync_at, next_sync_at, last_error, error_count, created_at, updated_at)
                VALUES(
                    '$dueWithinGraceId',
                    '${TEST_ACCOUNT_1_PROPERTY_1.id}',
                    'google',
                    'https://calendar.google.com/calendar/ical/123.ics',
                    true,
                    60,
                    NOW() - INTERVAL '60 minutes',
                    NOW() + INTERVAL '30 seconds',
                    NULL,
                    0,
                    NOW(),
                    NOW()
                )
                """.trimIndent(),
            )
            testDBConnection.executeQuery(
                """
                INSERT INTO property_channel_integrations
                (id, property_id, channel, url, sync_enabled, sync_interval_minutes, last_sync_at, next_sync_at, last_error, error_count, created_at, updated_at)
                VALUES(
                    '$dueBeyondGraceId',
                    '${TEST_ACCOUNT_1_PROPERTY_1.id}',
                    'direct',
                    'https://example.com/calendar/ical/123.ics',
                    true,
                    60,
                    NOW() - INTERVAL '55 minutes',
                    NOW() + INTERVAL '5 minutes',
                    NULL,
                    0,
                    NOW(),
                    NOW()
                )
                """.trimIndent(),
            )

            val due = service.getIntegrationsNeedingExecution().map { it.id }

            assertTrue(dueWithinGraceId in due, "Integration due within the grace window should be included")
            assertTrue(dueBeyondGraceId !in due, "Integration due beyond the grace window should not be included")
        }

    @Test
    fun testGetIntegrationsByPropertyIdEmpty() =
        runBlocking {
            connectKasifyTestsDB()

            val nonExistentPropertyId = Uuid.Companion.random()
            val integrations = service.getIntegrationsByPropertyId(nonExistentPropertyId)

            // Should return empty list for non-existent property
            assertEquals(0, integrations.size)
        }
}
