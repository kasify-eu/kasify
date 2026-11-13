package eu.kasify.routes

import eu.kasify.TEST_ACCOUNT_1_PROPERTY_1
import eu.kasify.TEST_ACCOUNT_1_USER_1
import eu.kasify.TEST_ACCOUNT_2_USER_3
import eu.kasify.db.model.PropertyAccessLevel
import eu.kasify.extensions.configureTestModules
import eu.kasify.extensions.createClient
import eu.kasify.model.api.requests.CreatePropertyInvitationRequest
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for role-based access control on calendar endpoints.
 * Verifies that:
 * - Owners see full calendar data (including nested booking.channelId)
 * - Crew members see filtered calendar data (no nested booking.channelId)
 */
class CalendarAccessControlTest : BaseRouteDBTests() {
    override fun setupData() {
        // The /calendar endpoint defaults to a window of today..today+1month, so
        // the event must fall inside that window or it gets filtered out.
        val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
        val from = LocalDateTime.now().plusDays(2).format(fmt)
        val to = LocalDateTime.now().plusDays(7).format(fmt)
        val createdAt = LocalDateTime.now().format(fmt)

        val sqlEvent =
            """
            INSERT INTO public.calendar_events
            (id, property_id, from_datetime, to_datetime, "type", created_at, updated_at)
            VALUES('50000000-0000-0000-0000-000000000001', '${TEST_ACCOUNT_1_PROPERTY_1.id}',
            '$from', '$to', 'booking',
            '$createdAt', '$createdAt');
            """.trimIndent()
        testDBConnection.executeQuery(sqlEvent)

        val sqlBooking =
            """
            INSERT INTO public.bookings
            (id, calendar_event_id, property_id, channel, channel_id, status,
            guest_first_name, guest_last_name, guest_email, guest_phone,
            adults_count, created_at, updated_at)
            VALUES('60000000-0000-0000-0000-000000000001', '50000000-0000-0000-0000-000000000001',
            '${TEST_ACCOUNT_1_PROPERTY_1.id}', 'airbnb', 'secret-channel-123', 'confirmed',
            'Maria', 'Garcia', 'maria@email.com', '+34612345678',
            2, '$createdAt', '$createdAt');
            """.trimIndent()
        testDBConnection.executeQuery(sqlBooking)
    }

    private suspend fun grantCrewAccess(client: io.ktor.client.HttpClient): String {
        // Create an invitation as owner with Staff access (not Contractor, which filters by task assignments)
        val createRequest = CreatePropertyInvitationRequest(maxUses = 5, accessLevel = PropertyAccessLevel.Staff)
        val createResponse =
            client.post("/v1/properties/${TEST_ACCOUNT_1_PROPERTY_1.id}/invitations") {
                header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                contentType(ContentType.Application.Json)
                setBody(createRequest)
            }

        val token =
            Json
                .parseToJsonElement(createResponse.bodyAsText())
                .jsonObject["token"]
                ?.jsonPrimitive
                ?.content!!

        // Accept invitation as crew user
        client.post("/v1/invitations/$token/accept") {
            header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_2_USER_3.token}")
        }

        return token
    }

    // ============================================================
    // Property Calendar Endpoint Tests
    // ============================================================

    @Test
    fun `Owner sees nested booking channelId in calendar event`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val response =
                client.get("/v1/properties/${TEST_ACCOUNT_1_PROPERTY_1.id}/calendar") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                }

            assertEquals(HttpStatusCode.OK, response.status)
            val responseText = response.bodyAsText()
            val json = Json.parseToJsonElement(responseText).jsonObject
            val events = json["events"]?.jsonArray

            assertNotNull(events)
            assertTrue(events.isNotEmpty(), "Should have calendar events")

            val event = events[0].jsonObject
            val booking = event["booking"]?.jsonObject
            assertNotNull(booking, "Event should have booking info")

            // CRITICAL: Owner should see the nested channelId
            assertNotNull(booking["channelId"], "Owner should see channelId in nested booking")
            assertEquals("secret-channel-123", booking["channelId"]?.jsonPrimitive?.content)
        }

    @Test
    fun `Crew does NOT see nested booking channelId in calendar event`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            // Grant crew access
            grantCrewAccess(client)

            val response =
                client.get("/v1/properties/${TEST_ACCOUNT_1_PROPERTY_1.id}/calendar") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_2_USER_3.token}")
                }

            assertEquals(HttpStatusCode.OK, response.status)
            val responseText = response.bodyAsText()
            val json = Json.parseToJsonElement(responseText).jsonObject
            val events = json["events"]?.jsonArray

            assertNotNull(events)
            assertTrue(events.isNotEmpty(), "Should have calendar events")

            val event = events[0].jsonObject
            val booking = event["booking"]?.jsonObject

            // Booking object should exist but be filtered
            assertNotNull(booking, "Event should have booking info object")

            // CRITICAL: Crew should NOT see the nested channelId
            // This was the bug that was fixed - nested objects weren't being filtered
            assertNull(
                booking["channelId"],
                "Crew should NOT see channelId in nested booking - nested filtering must work",
            )
        }

    @Test
    fun `Crew response doesn't contain secret channel ID in raw text`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            // Grant crew access
            grantCrewAccess(client)

            val response =
                client.get("/v1/properties/${TEST_ACCOUNT_1_PROPERTY_1.id}/calendar") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_2_USER_3.token}")
                }

            assertEquals(HttpStatusCode.OK, response.status)
            val responseText = response.bodyAsText()

            // Double check: the secret should not appear anywhere in the response
            assertFalse(
                responseText.contains("secret-channel-123"),
                "Secret channel ID should not appear anywhere in crew response",
            )
        }
}
