package eu.kasify.routes
import eu.kasify.TEST_ACCOUNT_1_PROPERTY_1
import eu.kasify.TEST_ACCOUNT_1_USER_1
import eu.kasify.TEST_ACCOUNT_2_USER_3
import eu.kasify.extensions.configureTestModules
import eu.kasify.extensions.createClient
import eu.kasify.helpers.grantCrewAccess
import eu.kasify.helpers.insertBooking
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Test
import java.time.LocalDateTime
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.uuid.Uuid

/**
 * Tests for role-based access control on the account-scoped GET /bookings endpoint.
 * Verifies that:
 * - Owner/manager see all bookings with full PII fields
 * - Staff see all bookings without MANAGER-level fields (guest PII, notes, channel)
 * - Contractors receive an empty bookings list
 */
class BookingsListAccessControlTest : BaseRouteDBTests() {
    private val testBookingId = Uuid.parse("40000000-0000-0000-0000-000000000010")

    override fun setupData() {
        testDBConnection.insertBooking(
            bookingId = testBookingId,
            propertyId = TEST_ACCOUNT_1_PROPERTY_1.id,
            from = LocalDateTime.of(2025, 3, 1, 17, 0),
            to = LocalDateTime.of(2025, 3, 10, 11, 0),
            guestFirstName = "Maria",
            guestLastName = "Garcia",
            guestEmail = "maria@email.com",
            guestPhone = "+34612345678",
            notes = "Private notes for owner",
            channelId = "airbnb-channel-123",
            adultsCount = 2,
            childrenCount = 1,
            infantsCount = 0,
            specialRequests = "Early check-in requested",
        )
    }

    // ============================================================
    // OWNER - full booking data
    // ============================================================

    @Test
    fun `Owner sees all bookings with full details in GET bookings`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val response =
                client.get("/v1/bookings") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                }

            assertEquals(HttpStatusCode.OK, response.status)
            val responseText = response.bodyAsText()

            // Owner should see all PII fields
            assertContains(responseText, "Maria")
            assertContains(responseText, "Garcia")
            assertContains(responseText, "maria@email.com")
            assertContains(responseText, "+34612345678")
            assertContains(responseText, "Private notes for owner")
            assertContains(responseText, "Early check-in requested")
            assertContains(responseText, "airbnb-channel-123")

            val bookings =
                Json
                    .parseToJsonElement(responseText)
                    .jsonObject["bookings"]!!
                    .jsonArray
            assertEquals(1, bookings.size)

            val booking = bookings[0].jsonObject
            assertNotNull(booking["id"])
            assertNotNull(booking["property"])
            assertNotNull(booking["from"])
            assertNotNull(booking["to"])
            assertNotNull(booking["status"])
            assertNotNull(booking["channel"])
            assertNotNull(booking["channel_id"])
            assertNotNull(booking["checkin_code"])
            assertNotNull(booking["guest_first_name"])
            assertNotNull(booking["guest_last_name"])
            assertNotNull(booking["guest_email"])
            assertNotNull(booking["guest_phone"])
            assertNotNull(booking["notes"])
            assertNotNull(booking["special_requests"])
        }

    // ============================================================
    // MANAGER - full booking data (manager access on another account)
    // ============================================================

    @Test
    fun `Manager crew sees all bookings with full details in GET bookings`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            testDBConnection.grantCrewAccess(
                userId = TEST_ACCOUNT_2_USER_3.id,
                propertyId = TEST_ACCOUNT_1_PROPERTY_1.id,
                accessLevel = "manager",
            )

            val response =
                client.get("/v1/bookings") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_2_USER_3.token}")
                }

            assertEquals(HttpStatusCode.OK, response.status)
            val responseText = response.bodyAsText()

            // Manager should see MANAGER-level fields
            assertContains(responseText, "Maria")
            assertContains(responseText, "Garcia")
            assertContains(responseText, "maria@email.com")
            assertContains(responseText, "+34612345678")
            assertContains(responseText, "Private notes for owner")
            assertContains(responseText, "airbnb-channel-123")

            val bookings =
                Json
                    .parseToJsonElement(responseText)
                    .jsonObject["bookings"]!!
                    .jsonArray
            assertEquals(1, bookings.size)

            val booking = bookings[0].jsonObject
            assertNotNull(booking["guest_last_name"], "manager should see guest_last_name")
            assertNotNull(booking["guest_email"], "manager should see guest_email")
            assertNotNull(booking["guest_phone"], "manager should see guest_phone")
            assertNotNull(booking["notes"], "manager should see notes")
            assertNotNull(booking["channel"], "manager should see channel")
            assertNotNull(booking["channel_id"], "manager should see channel_id")
            assertNotNull(booking["checkin_code"], "manager should see checkin_code")
        }

    // ============================================================
    // STAFF - filtered booking data (no MANAGER-level fields)
    // ============================================================

    @Test
    fun `Staff crew sees all bookings without security fields in GET bookings`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            testDBConnection.grantCrewAccess(
                userId = TEST_ACCOUNT_2_USER_3.id,
                propertyId = TEST_ACCOUNT_1_PROPERTY_1.id,
                accessLevel = "staff",
            )

            val response =
                client.get("/v1/bookings") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_2_USER_3.token}")
                }

            assertEquals(HttpStatusCode.OK, response.status)
            val responseText = response.bodyAsText()

            // Staff should see PUBLIC + CONTRACTOR-level fields
            assertContains(responseText, "Maria")
            assertContains(responseText, "2025-03-01")
            assertContains(responseText, "2025-03-10")
            assertContains(responseText, "Early check-in requested")

            // Staff should NOT see MANAGER-level fields
            assertFalse(responseText.contains("Garcia"), "staff should not see guest last name")
            assertFalse(responseText.contains("maria@email.com"), "staff should not see guest email")
            assertFalse(responseText.contains("+34612345678"), "staff should not see guest phone")
            assertFalse(responseText.contains("Private notes"), "staff should not see notes")
            assertFalse(responseText.contains("airbnb-channel-123"), "staff should not see channel ID")

            val bookings =
                Json
                    .parseToJsonElement(responseText)
                    .jsonObject["bookings"]!!
                    .jsonArray
            assertEquals(1, bookings.size)

            val booking = bookings[0].jsonObject

            // Fields that SHOULD be present for staff
            assertNotNull(booking["id"], "id should be present")
            assertNotNull(booking["property"], "property should be present")
            assertNotNull(booking["from"], "from should be present")
            assertNotNull(booking["to"], "to should be present")
            assertNotNull(booking["status"], "status should be present")
            assertNotNull(booking["guest_first_name"], "guest_first_name should be present")
            assertNotNull(booking["adults_count"], "adults_count should be present")
            assertNotNull(booking["children_count"], "children_count should be present")
            assertNotNull(booking["infants_count"], "infants_count should be present")
            assertNotNull(booking["special_requests"], "special_requests should be present for staff")

            // Fields that should NOT be present for staff (MANAGER or higher)
            assertNull(booking["guest_last_name"], "guest_last_name should not be present for staff")
            assertNull(booking["guest_email"], "guest_email should not be present for staff")
            assertNull(booking["guest_phone"], "guest_phone should not be present for staff")
            assertNull(booking["notes"], "notes should not be present for staff")
            assertNull(booking["channel"], "channel should not be present for staff")
            assertNull(booking["channel_id"], "channel_id should not be present for staff")
            assertNull(booking["checkin_code"], "checkin_code should not be present for staff")
            assertNull(booking["access_codes"], "access_codes should not be present for staff")
            assertNull(booking["guests"], "guests should not be present for staff")
        }

    // ============================================================
    // CONTRACTOR - no bookings
    // ============================================================

    @Test
    fun `Contractor crew sees no bookings in GET bookings`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            testDBConnection.grantCrewAccess(
                userId = TEST_ACCOUNT_2_USER_3.id,
                propertyId = TEST_ACCOUNT_1_PROPERTY_1.id,
                accessLevel = "contractor",
            )

            val response =
                client.get("/v1/bookings") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_2_USER_3.token}")
                }

            assertEquals(HttpStatusCode.OK, response.status)
            val responseText = response.bodyAsText()

            // Contractor should see no bookings at all
            assertFalse(responseText.contains("Maria"), "contractor should not see booking data")
            assertFalse(responseText.contains(testBookingId.toString()), "contractor should not see booking id")

            val bookings =
                Json
                    .parseToJsonElement(responseText)
                    .jsonObject["bookings"]!!
                    .jsonArray
            assertEquals(0, bookings.size, "contractor should receive an empty bookings list")
        }

    // ============================================================
    // NO ACCESS - no bookings
    // ============================================================

    @Test
    fun `User with no access sees no bookings in GET bookings`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            // TEST_ACCOUNT_2_USER_3 has no crew grant and owns a different account
            val response =
                client.get("/v1/bookings") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_2_USER_3.token}")
                }

            assertEquals(HttpStatusCode.OK, response.status)
            val responseText = response.bodyAsText()

            val bookings =
                Json
                    .parseToJsonElement(responseText)
                    .jsonObject["bookings"]!!
                    .jsonArray
            assertEquals(0, bookings.size, "user without any access should receive an empty list")
        }
}
