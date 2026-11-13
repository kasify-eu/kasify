package eu.kasify.routes
import eu.kasify.TEST_ACCOUNT_1_PROPERTY_1
import eu.kasify.TEST_ACCOUNT_1_USER_1
import eu.kasify.TEST_ACCOUNT_2_USER_3
import eu.kasify.db.model.BookingChannel
import eu.kasify.db.model.BookingStatus
import eu.kasify.extensions.configureTestModules
import eu.kasify.extensions.createClient
import eu.kasify.helpers.grantCrewAccess
import eu.kasify.helpers.insertBooking
import eu.kasify.helpers.insertTask
import eu.kasify.model.api.requests.BookingData
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.uuid.Uuid

/**
 * Tests for role-based access control on booking endpoints.
 * Verifies that:
 * - Owners see full booking data (including PII)
 * - Crew members see filtered booking data (no PII)
 * - Only owners can create/modify bookings
 */
class BookingsPropertiesAccessControlTest : BaseRouteDBTests() {
    private val testBookingId = Uuid.parse("40000000-0000-0000-0000-000000000002")

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
    // GET BOOKINGS LIST - Access Control Tests
    // ============================================================

    @Test
    fun `Owner sees full booking data in list`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val response =
                client.get("/v1/properties/${TEST_ACCOUNT_1_PROPERTY_1.id}/bookings") {
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
        }

    @Test
    fun `Crew sees filtered booking data in list`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            // Grant crew access to the property with Limited access level
            testDBConnection.grantCrewAccess(
                userId = TEST_ACCOUNT_2_USER_3.id,
                propertyId = TEST_ACCOUNT_1_PROPERTY_1.id,
                accessLevel = "staff",
            )

            val response =
                client.get("/v1/properties/${TEST_ACCOUNT_1_PROPERTY_1.id}/bookings") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_2_USER_3.token}")
                }

            assertEquals(HttpStatusCode.OK, response.status)
            val responseText = response.bodyAsText()

            // Crew should see basic info
            assertContains(responseText, "Maria") // First name is allowed
            assertContains(responseText, "2025-03-01") // Dates are allowed
            assertContains(responseText, "2025-03-10")
            assertContains(responseText, "Confirmed") // Status is allowed

            // Staff should see CONTRACTOR-level fields
            assertContains(responseText, "Early check-in requested") // special_requests is CONTRACTOR level

            // Staff should NOT see MANAGER-level fields
            assertFalse(responseText.contains("Garcia"), "Staff should not see guest last name")
            assertFalse(responseText.contains("maria@email.com"), "Staff should not see guest email")
            assertFalse(responseText.contains("+34612345678"), "Staff should not see guest phone")
            assertFalse(responseText.contains("Private notes"), "Staff should not see notes")
            assertFalse(responseText.contains("airbnb-channel-123"), "Staff should not see channel ID")
        }

    // ============================================================
    // GET SINGLE BOOKING - Access Control Tests
    // ============================================================

    @Test
    fun `Owner sees full booking data when getting single booking`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val response =
                client.get("/v1/bookings/$testBookingId") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                }

            assertEquals(HttpStatusCode.OK, response.status)
            val responseText = response.bodyAsText()

            // Owner should see all fields
            assertContains(responseText, "Garcia")
            assertContains(responseText, "maria@email.com")
            assertContains(responseText, "+34612345678")
            assertContains(responseText, "Private notes for owner")
        }

    @Test
    fun `Crew sees filtered booking data when getting single booking`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            // Grant crew access to the property with Limited access level
            testDBConnection.grantCrewAccess(
                userId = TEST_ACCOUNT_2_USER_3.id,
                propertyId = TEST_ACCOUNT_1_PROPERTY_1.id,
                accessLevel = "staff",
            )

            val response =
                client.get("/v1/bookings/$testBookingId") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_2_USER_3.token}")
                }

            assertEquals(HttpStatusCode.OK, response.status)
            val responseText = response.bodyAsText()

            // Crew should see basic info
            assertContains(responseText, "Maria")

            // Crew should NOT see PII
            assertFalse(responseText.contains("Garcia"))
            assertFalse(responseText.contains("maria@email.com"))
            assertFalse(responseText.contains("+34612345678"))
            assertFalse(responseText.contains("Private notes"))
        }

    // ============================================================
    // GET CURRENT BOOKING - Access Control Tests
    // ============================================================

    @Test
    fun `Owner sees full data in current booking`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            // Create a current booking (started yesterday, ends tomorrow)
            val from = LocalDateTime.now().minusDays(1)
            val to = LocalDateTime.now().plusDays(1)

            testDBConnection.insertBooking(
                propertyId = TEST_ACCOUNT_1_PROPERTY_1.id,
                from = from,
                to = to,
                guestFirstName = "Current",
                guestLastName = "Guest",
                guestEmail = "current@email.com",
                guestPhone = "+34999888777",
            )

            val response =
                client.get("/v1/properties/${TEST_ACCOUNT_1_PROPERTY_1.id}/bookings/current") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                }

            assertEquals(HttpStatusCode.OK, response.status)
            val responseText = response.bodyAsText()

            // Owner should see all fields
            assertContains(responseText, "Current")
            assertContains(responseText, "Guest")
            assertContains(responseText, "current@email.com")
        }

    @Test
    fun `Crew sees filtered data in current booking`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            testDBConnection.grantCrewAccess(
                userId = TEST_ACCOUNT_2_USER_3.id,
                propertyId = TEST_ACCOUNT_1_PROPERTY_1.id,
                accessLevel = "staff",
            )

            // Create a current booking
            val from = LocalDateTime.now().minusDays(1)
            val to = LocalDateTime.now().plusDays(1)

            testDBConnection.insertBooking(
                propertyId = TEST_ACCOUNT_1_PROPERTY_1.id,
                from = from,
                to = to,
                guestFirstName = "Current",
                guestLastName = "Guest",
                guestEmail = "current@email.com",
                guestPhone = "+34999888777",
            )

            val response =
                client.get("/v1/properties/${TEST_ACCOUNT_1_PROPERTY_1.id}/bookings/current") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_2_USER_3.token}")
                }

            assertEquals(HttpStatusCode.OK, response.status)
            val responseText = response.bodyAsText()

            // Crew should see first name
            assertContains(responseText, "Current")

            // Crew should NOT see PII
            assertFalse(responseText.contains("\"Guest\""), "Crew should not see last name")
            assertFalse(responseText.contains("current@email.com"))
        }

    @Test
    fun `TaskOnly crew gets 403 on current booking endpoint`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            testDBConnection.grantCrewAccess(
                userId = TEST_ACCOUNT_2_USER_3.id,
                propertyId = TEST_ACCOUNT_1_PROPERTY_1.id,
                accessLevel = "contractor",
            )

            // Create a current booking
            testDBConnection.insertBooking(
                propertyId = TEST_ACCOUNT_1_PROPERTY_1.id,
                from = LocalDateTime.now().minusDays(1),
                to = LocalDateTime.now().plusDays(1),
                guestFirstName = "Current",
                guestLastName = "Guest",
            )

            val response =
                client.get("/v1/properties/${TEST_ACCOUNT_1_PROPERTY_1.id}/bookings/current") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_2_USER_3.token}")
                }
            assertEquals(HttpStatusCode.Forbidden, response.status)
        }

    @Test
    fun `Manager sees current booking with MANAGER-level fields`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            testDBConnection.grantCrewAccess(
                userId = TEST_ACCOUNT_2_USER_3.id,
                propertyId = TEST_ACCOUNT_1_PROPERTY_1.id,
                accessLevel = "manager",
            )

            testDBConnection.insertBooking(
                propertyId = TEST_ACCOUNT_1_PROPERTY_1.id,
                from = LocalDateTime.now().minusDays(1),
                to = LocalDateTime.now().plusDays(1),
                guestFirstName = "Current",
                guestLastName = "FullGuest",
                guestEmail = "fullcrew@email.com",
                guestPhone = "+34111222333",
                notes = "Manager visible notes",
            )

            val response =
                client.get("/v1/properties/${TEST_ACCOUNT_1_PROPERTY_1.id}/bookings/current") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_2_USER_3.token}")
                }

            assertEquals(HttpStatusCode.OK, response.status)
            val responseText = response.bodyAsText()
            // Manager can see PUBLIC fields
            assertContains(responseText, "Current")
            // Manager can see MANAGER-level fields
            assertContains(responseText, "FullGuest")
            assertContains(responseText, "fullcrew@email.com")
            assertContains(responseText, "+34111222333")
            assertContains(responseText, "Manager visible notes")
        }

    // ============================================================
    // CREATE BOOKING - Access Control Tests
    // ============================================================

    @Test
    fun `Owner can create bookings`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val bookingData =
                BookingData(
                    from = LocalDate.parse("2025-05-01"),
                    to = LocalDate.parse("2025-05-05"),
                    channelId = "new-channel-id",
                    channel = BookingChannel.Direct,
                    status = BookingStatus.Confirmed,
                )

            val response =
                client.post("/v1/properties/${TEST_ACCOUNT_1_PROPERTY_1.id}/bookings") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                    contentType(ContentType.Application.Json)
                    setBody(bookingData)
                }

            assertEquals(HttpStatusCode.OK, response.status)
        }

    @Test
    fun `Crew cannot create bookings`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            testDBConnection.grantCrewAccess(
                userId = TEST_ACCOUNT_2_USER_3.id,
                propertyId = TEST_ACCOUNT_1_PROPERTY_1.id,
            )

            val bookingData =
                BookingData(
                    from = LocalDate.parse("2025-06-01"),
                    to = LocalDate.parse("2025-06-05"),
                    channelId = "crew-booking-attempt",
                    channel = BookingChannel.Direct,
                    status = BookingStatus.Confirmed,
                )

            val response =
                client.post("/v1/properties/${TEST_ACCOUNT_1_PROPERTY_1.id}/bookings") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_2_USER_3.token}")
                    contentType(ContentType.Application.Json)
                    setBody(bookingData)
                }

            // Crew should be denied - they cannot create bookings
            assertEquals(HttpStatusCode.Forbidden, response.status)
        }

    // ============================================================
    // UPDATE BOOKING - Access Control Tests
    // ============================================================

    @Test
    fun `Owner can update bookings`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val bookingData =
                BookingData(
                    notes = "Updated notes by owner",
                )

            val response =
                client.patch("/v1/bookings/$testBookingId") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                    contentType(ContentType.Application.Json)
                    setBody(bookingData)
                }

            assertEquals(HttpStatusCode.OK, response.status)
            val responseText = response.bodyAsText()
            assertContains(responseText, "Updated notes by owner")
        }

    @Test
    fun `Crew cannot update bookings`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            testDBConnection.grantCrewAccess(
                userId = TEST_ACCOUNT_2_USER_3.id,
                propertyId = TEST_ACCOUNT_1_PROPERTY_1.id,
            )

            val bookingData =
                BookingData(
                    notes = "Crew trying to update notes",
                )

            val response =
                client.patch("/v1/bookings/$testBookingId") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_2_USER_3.token}")
                    contentType(ContentType.Application.Json)
                    setBody(bookingData)
                }

            // Crew should be denied - they cannot update bookings
            assertEquals(HttpStatusCode.Forbidden, response.status)
        }

    // ============================================================
    // VERIFY FILTERED RESPONSE STRUCTURE
    // ============================================================

    @Test
    fun `Crew response has correct fields present and absent`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            testDBConnection.grantCrewAccess(
                userId = TEST_ACCOUNT_2_USER_3.id,
                propertyId = TEST_ACCOUNT_1_PROPERTY_1.id,
                accessLevel = "staff",
            )

            val response =
                client.get("/v1/bookings/$testBookingId") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_2_USER_3.token}")
                }

            assertEquals(HttpStatusCode.OK, response.status)
            val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject

            // Fields that SHOULD be present for staff (PUBLIC + CONTRACTOR level)
            assertNotNull(json["id"], "id should be present")
            assertNotNull(json["property"], "property should be present")
            assertNotNull(json["from"], "from should be present")
            assertNotNull(json["to"], "to should be present")
            assertNotNull(json["status"], "status should be present")
            assertNotNull(json["guest_first_name"], "guest_first_name should be present")
            assertNotNull(json["adults_count"], "adults_count should be present")
            assertNotNull(json["children_count"], "children_count should be present")
            assertNotNull(json["infants_count"], "infants_count should be present")
            assertNotNull(json["special_requests"], "special_requests should be present for staff")

            // Fields that should NOT be present for staff (MANAGER or higher)
            assertNull(json["guest_last_name"], "guest_last_name should not be present for staff")
            assertNull(json["guest_email"], "guest_email should not be present for staff")
            assertNull(json["guest_phone"], "guest_phone should not be present for staff")
            assertNull(json["notes"], "notes should not be present for staff")
            assertNull(json["channel"], "channel should not be present for staff")
            assertNull(json["channel_id"], "channel_id should not be present for staff")
            assertNull(json["access_codes"], "access_codes should not be present for staff")
            assertNull(json["guests"], "guests should not be present for staff")
        }

    @Test
    fun `Owner response has PII fields present`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val response =
                client.get("/v1/bookings/$testBookingId") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                }

            assertEquals(HttpStatusCode.OK, response.status)
            val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject

            // Basic fields should be present for owner
            assertNotNull(json["id"])
            assertNotNull(json["property"])
            assertNotNull(json["from"])
            assertNotNull(json["to"])
            assertNotNull(json["status"])
            assertNotNull(json["channel"])

            // Owner-only PII fields should be present
            assertNotNull(json["channel_id"], "Owner should see channel_id")
            assertNotNull(json["guest_first_name"])
            assertNotNull(json["guest_last_name"], "Owner should see guest_last_name")
            assertNotNull(json["guest_email"], "Owner should see guest_email")
            assertNotNull(json["guest_phone"], "Owner should see guest_phone")
            assertNotNull(json["notes"], "Owner should see notes")
            assertNotNull(json["special_requests"], "Owner should see special_requests")
        }

    // ============================================================
    // ACCESS LEVEL TESTS
    // ============================================================

    @Test
    fun `Manager sees booking with MANAGER-level fields`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            testDBConnection.grantCrewAccess(
                userId = TEST_ACCOUNT_2_USER_3.id,
                propertyId = TEST_ACCOUNT_1_PROPERTY_1.id,
                accessLevel = "manager",
            )

            val response =
                client.get("/v1/bookings/$testBookingId") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_2_USER_3.token}")
                }

            assertEquals(HttpStatusCode.OK, response.status)
            val responseText = response.bodyAsText()

            // Manager can see PUBLIC fields
            assertContains(responseText, "Maria")
            // Manager can see MANAGER-level fields (guest PII, notes, channel)
            assertContains(responseText, "Garcia")
            assertContains(responseText, "maria@email.com")
            assertContains(responseText, "+34612345678")
            assertContains(responseText, "Private notes for owner")
            assertContains(responseText, "airbnb-channel-123")
        }

    // ============================================================
    // GUEST REVIEW - OWNER-ONLY VISIBILITY
    // ============================================================

    private fun setGuestReview(bookingId: Uuid) {
        testDBConnection.executeQuery(
            "UPDATE bookings SET would_host_again = true, " +
                "guest_review_notes = 'Spotless house, great communication' WHERE id = '$bookingId'",
        )
    }

    @Test
    fun `Owner sees guest review fields`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            setGuestReview(testBookingId)

            val response =
                client.get("/v1/bookings/$testBookingId") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                }

            assertEquals(HttpStatusCode.OK, response.status)
            val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            assertEquals("true", json["would_host_again"]?.jsonPrimitive?.content)
            assertEquals(
                "Spotless house, great communication",
                json["guest_review_notes"]?.jsonPrimitive?.content,
            )
        }

    @Test
    fun `Manager does not see guest review fields`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            testDBConnection.grantCrewAccess(
                userId = TEST_ACCOUNT_2_USER_3.id,
                propertyId = TEST_ACCOUNT_1_PROPERTY_1.id,
                accessLevel = "manager",
            )
            setGuestReview(testBookingId)

            val response =
                client.get("/v1/bookings/$testBookingId") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_2_USER_3.token}")
                }

            assertEquals(HttpStatusCode.OK, response.status)
            val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            assertNull(json["would_host_again"], "would_host_again should not be present for manager")
            assertNull(json["guest_review_notes"], "guest_review_notes should not be present for manager")
        }

    @Test
    fun `Staff does not see guest review fields`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            testDBConnection.grantCrewAccess(
                userId = TEST_ACCOUNT_2_USER_3.id,
                propertyId = TEST_ACCOUNT_1_PROPERTY_1.id,
                accessLevel = "staff",
            )
            setGuestReview(testBookingId)

            val response =
                client.get("/v1/bookings/$testBookingId") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_2_USER_3.token}")
                }

            assertEquals(HttpStatusCode.OK, response.status)
            val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            assertNull(json["would_host_again"], "would_host_again should not be present for staff")
            assertNull(json["guest_review_notes"], "guest_review_notes should not be present for staff")
        }

    @Test
    fun `Limited access crew sees filtered booking fields`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            testDBConnection.grantCrewAccess(
                userId = TEST_ACCOUNT_2_USER_3.id,
                propertyId = TEST_ACCOUNT_1_PROPERTY_1.id,
                accessLevel = "staff",
            )

            val response =
                client.get("/v1/bookings/$testBookingId") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_2_USER_3.token}")
                }

            assertEquals(HttpStatusCode.OK, response.status)
            val responseText = response.bodyAsText()

            // Limited crew should see basic info
            assertContains(responseText, "Maria")

            // Limited crew should NOT see PII
            assertFalse(responseText.contains("Garcia"), "Limited crew should not see guest last name")
            assertFalse(responseText.contains("maria@email.com"), "Limited crew should not see guest email")
            assertFalse(responseText.contains("+34612345678"), "Limited crew should not see guest phone")
        }

    @Test
    fun `TaskOnly crew gets 403 on booking list endpoint`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            testDBConnection.grantCrewAccess(
                userId = TEST_ACCOUNT_2_USER_3.id,
                propertyId = TEST_ACCOUNT_1_PROPERTY_1.id,
                accessLevel = "contractor",
            )

            val response =
                client.get("/v1/properties/${TEST_ACCOUNT_1_PROPERTY_1.id}/bookings") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_2_USER_3.token}")
                }
            assertEquals(HttpStatusCode.Forbidden, response.status)
        }

    @Test
    fun `TaskOnly crew gets 403 on single booking endpoint`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            testDBConnection.grantCrewAccess(
                userId = TEST_ACCOUNT_2_USER_3.id,
                propertyId = TEST_ACCOUNT_1_PROPERTY_1.id,
                accessLevel = "contractor",
            )

            val response =
                client.get("/v1/bookings/$testBookingId") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_2_USER_3.token}")
                }
            assertEquals(HttpStatusCode.Forbidden, response.status)
        }

    @Test
    fun `Full access crew can list bookings`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            testDBConnection.grantCrewAccess(
                userId = TEST_ACCOUNT_2_USER_3.id,
                propertyId = TEST_ACCOUNT_1_PROPERTY_1.id,
                accessLevel = "manager",
            )

            val response =
                client.get("/v1/properties/${TEST_ACCOUNT_1_PROPERTY_1.id}/bookings") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_2_USER_3.token}")
                }

            assertEquals(HttpStatusCode.OK, response.status)
        }

    // ============================================================
    // MANAGER JSON STRUCTURE TESTS
    // ============================================================

    @Test
    fun `Manager response has correct fields present and absent`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            testDBConnection.grantCrewAccess(
                userId = TEST_ACCOUNT_2_USER_3.id,
                propertyId = TEST_ACCOUNT_1_PROPERTY_1.id,
                accessLevel = "manager",
            )

            val response =
                client.get("/v1/bookings/$testBookingId") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_2_USER_3.token}")
                }

            assertEquals(HttpStatusCode.OK, response.status)
            val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject

            // PUBLIC fields
            assertNotNull(json["id"], "id should be present")
            assertNotNull(json["property"], "property should be present")
            assertNotNull(json["from"], "from should be present")
            assertNotNull(json["to"], "to should be present")
            assertNotNull(json["status"], "status should be present")
            assertNotNull(json["guest_first_name"], "guest_first_name should be present")
            assertNotNull(json["adults_count"], "adults_count should be present")
            assertNotNull(json["children_count"], "children_count should be present")
            assertNotNull(json["infants_count"], "infants_count should be present")

            // CONTRACTOR-level fields
            assertNotNull(json["special_requests"], "special_requests should be present for manager")

            // MANAGER-level fields should be present
            assertNotNull(json["guest_last_name"], "guest_last_name should be present for manager")
            assertNotNull(json["guest_email"], "guest_email should be present for manager")
            assertNotNull(json["guest_phone"], "guest_phone should be present for manager")
            assertNotNull(json["notes"], "notes should be present for manager")
            assertNotNull(json["channel"], "channel should be present for manager")
            assertNotNull(json["channel_id"], "channel_id should be present for manager")
            assertNotNull(json["created_at"], "created_at should be present for manager")
        }

    // ============================================================
    // NO ACCESS TESTS
    // ============================================================

    @Test
    fun `User with no access gets 403 on single booking`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            // TEST_ACCOUNT_2_USER_3 has no access to the property (no crew grant, different account)
            val response =
                client.get("/v1/bookings/$testBookingId") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_2_USER_3.token}")
                }

            assertEquals(HttpStatusCode.Forbidden, response.status)
        }

    @Test
    fun `User with no access gets 403 on booking list`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val response =
                client.get("/v1/properties/${TEST_ACCOUNT_1_PROPERTY_1.id}/bookings") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_2_USER_3.token}")
                }

            assertEquals(HttpStatusCode.Forbidden, response.status)
        }

    @Test
    fun `User with no access gets 403 on current booking`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            testDBConnection.insertBooking(
                propertyId = TEST_ACCOUNT_1_PROPERTY_1.id,
                from = LocalDateTime.now().minusDays(1),
                to = LocalDateTime.now().plusDays(1),
                guestFirstName = "Current",
            )

            val response =
                client.get("/v1/properties/${TEST_ACCOUNT_1_PROPERTY_1.id}/bookings/current") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_2_USER_3.token}")
                }

            assertEquals(HttpStatusCode.Forbidden, response.status)
        }

    // ============================================================
    // STAFF SPECIAL REQUESTS VISIBILITY
    // ============================================================

    @Test
    fun `Staff sees special_requests field in booking list`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            testDBConnection.grantCrewAccess(
                userId = TEST_ACCOUNT_2_USER_3.id,
                propertyId = TEST_ACCOUNT_1_PROPERTY_1.id,
                accessLevel = "staff",
            )

            val response =
                client.get("/v1/properties/${TEST_ACCOUNT_1_PROPERTY_1.id}/bookings") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_2_USER_3.token}")
                }

            assertEquals(HttpStatusCode.OK, response.status)
            val responseText = response.bodyAsText()

            // Staff should see special_requests (CONTRACTOR level, staff >= contractor)
            assertContains(responseText, "Early check-in requested")
        }

    @Test
    fun `Staff sees special_requests in single booking`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            testDBConnection.grantCrewAccess(
                userId = TEST_ACCOUNT_2_USER_3.id,
                propertyId = TEST_ACCOUNT_1_PROPERTY_1.id,
                accessLevel = "staff",
            )

            val response =
                client.get("/v1/bookings/$testBookingId") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_2_USER_3.token}")
                }

            assertEquals(HttpStatusCode.OK, response.status)
            val responseText = response.bodyAsText()

            assertContains(responseText, "Early check-in requested")
            // But still no MANAGER-level fields
            assertFalse(responseText.contains("Garcia"), "Staff should not see guest last name")
            assertFalse(responseText.contains("maria@email.com"), "Staff should not see guest email")
        }

    // ============================================================
    // EMBEDDED PROPERTY OBJECT TESTS
    // ============================================================

    @Test
    fun `Booking contains embedded property with id, name and role for owner`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val response =
                client.get("/v1/bookings/$testBookingId") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                }

            assertEquals(HttpStatusCode.OK, response.status)
            val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            val property = json["property"]?.jsonObject

            assertNotNull(property, "property object should be present")
            assertEquals(TEST_ACCOUNT_1_PROPERTY_1.id.toString(), property["id"]?.toString()?.trim('"'))
            assertEquals(TEST_ACCOUNT_1_PROPERTY_1.name, property["name"]?.toString()?.trim('"'))
            assertEquals("owner", property["role"]?.toString()?.trim('"'))
        }

    @Test
    fun `Booking contains embedded property with manager role for manager`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            testDBConnection.grantCrewAccess(
                userId = TEST_ACCOUNT_2_USER_3.id,
                propertyId = TEST_ACCOUNT_1_PROPERTY_1.id,
                accessLevel = "manager",
            )

            val response =
                client.get("/v1/bookings/$testBookingId") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_2_USER_3.token}")
                }

            assertEquals(HttpStatusCode.OK, response.status)
            val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            val property = json["property"]?.jsonObject

            assertNotNull(property, "property object should be present")
            assertEquals(TEST_ACCOUNT_1_PROPERTY_1.id.toString(), property["id"]?.toString()?.trim('"'))
            assertEquals(TEST_ACCOUNT_1_PROPERTY_1.name, property["name"]?.toString()?.trim('"'))
            assertEquals("manager", property["role"]?.toString()?.trim('"'))
        }

    @Test
    fun `Booking contains embedded property with staff role for staff`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            testDBConnection.grantCrewAccess(
                userId = TEST_ACCOUNT_2_USER_3.id,
                propertyId = TEST_ACCOUNT_1_PROPERTY_1.id,
                accessLevel = "staff",
            )

            val response =
                client.get("/v1/bookings/$testBookingId") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_2_USER_3.token}")
                }

            assertEquals(HttpStatusCode.OK, response.status)
            val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            val property = json["property"]?.jsonObject

            assertNotNull(property, "property object should be present")
            assertEquals(TEST_ACCOUNT_1_PROPERTY_1.id.toString(), property["id"]?.toString()?.trim('"'))
            assertEquals(TEST_ACCOUNT_1_PROPERTY_1.name, property["name"]?.toString()?.trim('"'))
            assertEquals("staff", property["role"]?.toString()?.trim('"'))
        }

    // ============================================================
    // CLEANING TASK VISIBILITY ON GET /bookings/{id}
    // ============================================================

    @Test
    fun `Owner sees cleaning_task on single booking`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            testDBConnection.insertTask(
                propertyId = TEST_ACCOUNT_1_PROPERTY_1.id,
                bookingId = testBookingId,
                taskType = "cleaning",
                assignedTo = TEST_ACCOUNT_2_USER_3.id,
                scheduledDate = LocalDateTime.of(2025, 3, 10, 11, 0),
            )

            val response =
                client.get("/v1/bookings/$testBookingId") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                }

            assertEquals(HttpStatusCode.OK, response.status)
            val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            assertNotNull(json["cleaning_task"], "Owner should see cleaning_task regardless of assignee")
        }

    @Test
    fun `Manager sees cleaning_task even when not the assignee`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            testDBConnection.grantCrewAccess(
                userId = TEST_ACCOUNT_2_USER_3.id,
                propertyId = TEST_ACCOUNT_1_PROPERTY_1.id,
                accessLevel = "manager",
            )
            // Task assigned to the owner, NOT to the manager making the request.
            testDBConnection.insertTask(
                propertyId = TEST_ACCOUNT_1_PROPERTY_1.id,
                bookingId = testBookingId,
                taskType = "cleaning",
                assignedTo = TEST_ACCOUNT_1_USER_1.id,
                scheduledDate = LocalDateTime.of(2025, 3, 10, 11, 0),
            )

            val response =
                client.get("/v1/bookings/$testBookingId") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_2_USER_3.token}")
                }

            assertEquals(HttpStatusCode.OK, response.status)
            val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            assertNotNull(json["cleaning_task"], "Manager should see cleaning_task regardless of assignee")
        }

    @Test
    fun `Staff sees cleaning_task when assigned to them`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            testDBConnection.grantCrewAccess(
                userId = TEST_ACCOUNT_2_USER_3.id,
                propertyId = TEST_ACCOUNT_1_PROPERTY_1.id,
                accessLevel = "staff",
            )
            // Task assigned to the requesting staff user.
            testDBConnection.insertTask(
                propertyId = TEST_ACCOUNT_1_PROPERTY_1.id,
                bookingId = testBookingId,
                taskType = "cleaning",
                assignedTo = TEST_ACCOUNT_2_USER_3.id,
                scheduledDate = LocalDateTime.of(2025, 3, 10, 11, 0),
            )

            val response =
                client.get("/v1/bookings/$testBookingId") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_2_USER_3.token}")
                }

            assertEquals(HttpStatusCode.OK, response.status)
            val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            assertNotNull(json["cleaning_task"], "Staff should see cleaning_task when they are the assignee")
        }

    @Test
    fun `Staff does not see cleaning_task when assigned to someone else`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            testDBConnection.grantCrewAccess(
                userId = TEST_ACCOUNT_2_USER_3.id,
                propertyId = TEST_ACCOUNT_1_PROPERTY_1.id,
                accessLevel = "staff",
            )
            // Task assigned to the owner, NOT to the requesting staff user.
            testDBConnection.insertTask(
                propertyId = TEST_ACCOUNT_1_PROPERTY_1.id,
                bookingId = testBookingId,
                taskType = "cleaning",
                assignedTo = TEST_ACCOUNT_1_USER_1.id,
                scheduledDate = LocalDateTime.of(2025, 3, 10, 11, 0),
            )

            val response =
                client.get("/v1/bookings/$testBookingId") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_2_USER_3.token}")
                }

            assertEquals(HttpStatusCode.OK, response.status)
            val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            assertNull(json["cleaning_task"], "Staff must not see cleaning_task when they are not the assignee")
        }
}
