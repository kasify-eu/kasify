package eu.kasify.routes
import eu.kasify.TEST_ACCOUNT_1_PROPERTY_1
import eu.kasify.TEST_ACCOUNT_1_USER_1
import eu.kasify.TEST_ACCOUNT_2_USER_3
import eu.kasify.TEST_ACCOUNT_ID_1
import eu.kasify.extensions.configureTestModules
import eu.kasify.extensions.createClient
import eu.kasify.helpers.grantCrewAccess
import eu.kasify.model.api.requests.BookingData
import eu.kasify.utils.createLocalTimeUtc
import io.ktor.client.request.delete
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
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.uuid.Uuid

class AccessCodesRouteTest : BaseRouteDBTests() {
    private val testBookingId = Uuid.Companion.parse("40000000-0000-0000-0000-000000000001")
    private val testBookingWithCodeId = Uuid.Companion.parse("40000000-0000-0000-0000-000000000002")
    private val testSmartlockId = Uuid.Companion.parse("50000000-0000-0000-0000-000000000001")
    private val testNukiSmartlockId = Uuid.Companion.parse("60000000-0000-0000-0000-000000000001")

    private val tomorrow = LocalDate.now().plusDays(1)
    private val tomorrowPlus5 = tomorrow.plusDays(5)
    private val checkinTime = tomorrow.atTime(17, 0)
    private val checkoutTime = tomorrowPlus5.atTime(11, 0)
    private val dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")

    override fun setupData() {
        // Insert calendar event for first booking
        val sqlEvent1 =
            "INSERT INTO public.calendar_events " +
                "(id, property_id, from_datetime, to_datetime, \"type\", created_at, updated_at)\n" +
                "VALUES('30000000-0000-0000-0000-000000000001', '${TEST_ACCOUNT_1_PROPERTY_1.id}', '${checkinTime.format(dtf)}', '${checkoutTime.format(dtf)}', 'booking', now(), now());"
        testDBConnection.executeQuery(sqlEvent1)

        // Insert first booking without access code
        val sqlBooking1 =
            "INSERT INTO public.bookings " +
                "(id, calendar_event_id, property_id, channel, channel_id, status, guest_first_name, guest_last_name, adults_count,  created_at, updated_at) " +
                "VALUES('$testBookingId', '30000000-0000-0000-0000-000000000001', '${TEST_ACCOUNT_1_PROPERTY_1.id}', 'direct', 'abc', 'confirmed', 'name', 'lastname', 2, now(), now())"
        testDBConnection.executeQuery(sqlBooking1)

        // Insert calendar event for second booking
        val sqlEvent2 =
            "INSERT INTO public.calendar_events " +
                "(id, property_id, from_datetime, to_datetime, \"type\", created_at, updated_at)\n" +
                "VALUES('30000000-0000-0000-0000-000000000002', '${TEST_ACCOUNT_1_PROPERTY_1.id}', '${checkinTime.format(dtf)}', '${checkoutTime.format(dtf)}', 'booking', now(), now());"
        testDBConnection.executeQuery(sqlEvent2)

        // Insert second booking with existing access code
        val sqlBooking2 =
            "INSERT INTO public.bookings " +
                "(id, calendar_event_id, property_id, channel, channel_id, status, guest_first_name, guest_last_name, adults_count, created_at, updated_at) " +
                "VALUES('$testBookingWithCodeId', '30000000-0000-0000-0000-000000000002', '${TEST_ACCOUNT_1_PROPERTY_1.id}', 'direct', 'abc', 'confirmed', 'name', 'lastname', 2, now(), now())"
        testDBConnection.executeQuery(sqlBooking2)

        // Insert a smartlock for the property
        val sqlSmartlock =
            "INSERT INTO public.property_smartlocks " +
                "(id, property_id, vendor, name, created_at, updated_at) " +
                "VALUES('$testSmartlockId', '${TEST_ACCOUNT_1_PROPERTY_1.id}', 'nuki', 'Main Door', now(), now())"
        testDBConnection.executeQuery(sqlSmartlock)

        // Insert account integration for Nuki
        val sqlAccountIntegration =
            "INSERT INTO public.account_integrations " +
                "(id, account_id, vendor, created_at, updated_at) " +
                "VALUES(gen_random_uuid(), '${TEST_ACCOUNT_ID_1}', 'nuki', now(), now());"
        testDBConnection.executeQuery(sqlAccountIntegration)

        // Insert Nuki Config
        val sqlNukiConfig =
            "INSERT INTO public.account_nuki_integrations " +
                "(id, account_integration_id, nuki_token, created_at, updated_at) " +
                "VALUES(gen_random_uuid(), (SELECT id FROM account_integrations WHERE account_id = '${TEST_ACCOUNT_ID_1}' AND vendor = 'nuki' LIMIT 1), 'test_token', now(), now());"
        testDBConnection.executeQuery(sqlNukiConfig)

        // Insert Nuki smartlock details
        val sqlNukiSmartlock =
            "INSERT INTO public.property_nuki_smartlocks " +
                "(id, property_smartlock_id, nuki_integration_id, nuki_smartlock_id, created_at, updated_at) " +
                "VALUES('$testNukiSmartlockId', '$testSmartlockId', (SELECT id FROM account_nuki_integrations LIMIT 1), '123456789', now(), now())"
        testDBConnection.executeQuery(sqlNukiSmartlock)

        // Insert access code for the second booking
        val sqlAccessCode =
            "INSERT INTO public.booking_access_codes " +
                "(id, booking_id, smartlock_id, access_code, vendor_code_id, code_start_datetime, code_end_datetime, created_at, updated_at) " +
                "VALUES(gen_random_uuid(), '$testBookingWithCodeId', '$testSmartlockId', '999999', '12345', '${checkinTime.format(dtf)}', '${checkoutTime.format(dtf)}', now(), now())"
        testDBConnection.executeQuery(sqlAccessCode)
    }

    @Test
    fun testCreateAccessCodeSuccess() =
        testApplication {
            application { configureTestModules() }

            val client = createClient()

            val response =
                client.post("/v1/bookings/$testBookingId/accesscode") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                }

            assertEquals(HttpStatusCode.Companion.OK, response.status)
            val responseText = response.bodyAsText()
            val jsonResponse = Json.Default.parseToJsonElement(responseText).jsonObject

            // Verify response contains access code
            val accessCode = jsonResponse["accessCode"]?.jsonPrimitive?.content
            assertNotNull(accessCode)

            // Verify per-lock status is included
            assertNotNull(jsonResponse["accessCodes"])
        }

    @Test
    fun testCreateAccessCodeRecordsSuccessForEachSmartlock() =
        testApplication {
            // Set up a property with a Unifi smartlock (goes through getSmartLockPlugin → MockSmartLockPlugin → always succeeds)
            val propertyId = Uuid.parse("30000000-0000-0000-0000-000000000010")
            val eventId = Uuid.parse("E0000000-0000-0000-0000-000000000010")
            val bookingId = Uuid.parse("40000000-0000-0000-0000-000000000010")
            val smartlockId = Uuid.parse("50000000-0000-0000-0000-000000000010")

            testDBConnection.insertProperty(propertyId, TEST_ACCOUNT_ID_1, "Property with Unifi")
            testDBConnection.executeQuery(
                "INSERT INTO public.calendar_events (id, property_id, from_datetime, to_datetime, \"type\", created_at, updated_at) " +
                    "VALUES('$eventId', '$propertyId', '${checkinTime.format(dtf)}', '${checkoutTime.format(dtf)}', 'booking', now(), now())",
            )
            testDBConnection.executeQuery(
                "INSERT INTO public.bookings (id, calendar_event_id, property_id, channel, channel_id, status, guest_first_name, guest_last_name, adults_count, created_at, updated_at) " +
                    "VALUES('$bookingId', '$eventId', '$propertyId', 'direct', 'ref1', 'confirmed', 'Ana', 'Garcia', 2, now(), now())",
            )
            testDBConnection.executeQuery(
                "INSERT INTO public.property_smartlocks (id, property_id, vendor, name, created_at, updated_at) " +
                    "VALUES('$smartlockId', '$propertyId', 'unifi', 'Main Gate', now(), now())",
            )

            application { configureTestModules() }
            val client = createClient()

            val response =
                client.post("/v1/bookings/$bookingId/accesscode") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                }

            assertEquals(HttpStatusCode.OK, response.status)
            val json = Json.Default.parseToJsonElement(response.bodyAsText()).jsonObject
            val accessCodes = json["accessCodes"]!!.jsonArray
            assertEquals(1, accessCodes.size)
        }

    @Test
    fun testCreateAccessCodeUnauthorized() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val response = client.post("/v1/bookings/$testBookingId/accesscode")

            assertEquals(HttpStatusCode.Companion.Unauthorized, response.status)
        }

    @Test
    fun testCreateAccessCodeInvalidToken() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val response =
                client.post("/v1/bookings/$testBookingId/accesscode") {
                    header(HttpHeaders.Authorization, "Bearer invalid-token")
                }

            assertEquals(HttpStatusCode.Companion.Unauthorized, response.status)
        }

    @Test
    fun testCreateAccessCodeWrongAccount() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val response =
                client.post("/v1/bookings/$testBookingId/accesscode") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_2_USER_3.token}")
                }

            assertEquals(HttpStatusCode.Companion.Forbidden, response.status)
        }

    @Test
    fun testCreateAccessCodeContractorDenied() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            testDBConnection.grantCrewAccess(
                userId = TEST_ACCOUNT_2_USER_3.id,
                propertyId = TEST_ACCOUNT_1_PROPERTY_1.id,
                service = "cleaning",
                accessLevel = "contractor",
            )

            val response =
                client.post("/v1/bookings/$testBookingId/accesscode") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_2_USER_3.token}")
                }

            assertEquals(HttpStatusCode.Companion.Forbidden, response.status)
        }

    @Test
    fun testCreateAccessCodeStaffDenied() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            testDBConnection.grantCrewAccess(
                userId = TEST_ACCOUNT_2_USER_3.id,
                propertyId = TEST_ACCOUNT_1_PROPERTY_1.id,
                service = "cleaning",
                accessLevel = "staff",
            )

            val response =
                client.post("/v1/bookings/$testBookingId/accesscode") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_2_USER_3.token}")
                }

            assertEquals(HttpStatusCode.Companion.Forbidden, response.status)
        }

    @Test
    fun testCreateAccessCodeManagerAllowed() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            testDBConnection.grantCrewAccess(
                userId = TEST_ACCOUNT_2_USER_3.id,
                propertyId = TEST_ACCOUNT_1_PROPERTY_1.id,
                accessLevel = "manager",
            )

            val response =
                client.post("/v1/bookings/$testBookingId/accesscode") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_2_USER_3.token}")
                }

            assertEquals(HttpStatusCode.Companion.OK, response.status)
        }

    @Test
    fun testCreateAccessCodeInvalidBookingId() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val response =
                client.post("/v1/bookings/invalid-uuid/accesscode") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                }

            assertEquals(HttpStatusCode.Companion.BadRequest, response.status)
        }

    @Test
    fun testCreateAccessCodeBookingNotFound() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val nonExistentBookingId = Uuid.Companion.parse("99999999-9999-9999-9999-999999999999")
            val response =
                client.post("/v1/bookings/$nonExistentBookingId/accesscode") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                }

            assertEquals(HttpStatusCode.Companion.NotFound, response.status)
        }

    @Test
    fun testCreateAccessCodeAlreadyExists() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val response =
                client.post("/v1/bookings/$testBookingWithCodeId/accesscode") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                }

            assertEquals(HttpStatusCode.Conflict, response.status)
            assertContains(response.bodyAsText(), Errors.ERROR_ACCESS_CODES_ALREADY_EXIST_IN_BOOKING)
        }

    @Test
    fun testCreateAccessCodeNoDoorsConfigured() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            // Create a new property without smartlocks
            val propertyId = Uuid.Companion.parse("30000000-0000-0000-0000-000000000099")
            testDBConnection.insertProperty(propertyId, TEST_ACCOUNT_ID_1, "Property without smartlocks")

            // Create a calendar event for this property
            val eventId = Uuid.Companion.parse("30000000-0000-0000-0000-000000000099")
            val sqlEvent =
                "INSERT INTO public.calendar_events " +
                    "(id, property_id, from_datetime, to_datetime, \"type\", created_at, updated_at)\n" +
                    "VALUES('$eventId', '$propertyId', '${checkinTime.format(dtf)}', '${checkoutTime.format(dtf)}', 'booking', now(), now());"
            testDBConnection.executeQuery(sqlEvent)

            // Create a booking for this property
            val bookingId = Uuid.Companion.parse("40000000-0000-0000-0000-000000000099")
            val sqlBooking =
                "INSERT INTO public.bookings " +
                    "(id, calendar_event_id, property_id, channel, channel_id, status, guest_first_name, guest_last_name, adults_count,  created_at, updated_at) " +
                    "VALUES('$bookingId', '$eventId', '$propertyId', 'direct', 'airbnbid', 'confirmed', 'name', 'lastname', 2, now(), now())"
            testDBConnection.executeQuery(sqlBooking)

            val response =
                client.post("/v1/bookings/$bookingId/accesscode") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                }

            assertEquals(HttpStatusCode.Companion.NotAcceptable, response.status)
            assertContains(response.bodyAsText(), "Smart locks not configured")
        }

    @Test
    fun testCreateAccessCodeUpdatesBooking() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            // Create access code
            val response =
                client.post("/v1/bookings/$testBookingId/accesscode") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                }

            assertEquals(HttpStatusCode.OK, response.status)

            // Verify the booking was updated with the access code
            // Try to create another access code for the same booking
            val secondResponse =
                client.post("/v1/bookings/$testBookingId/accesscode") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                }

            assertEquals(HttpStatusCode.Conflict, secondResponse.status)
        }

    @Test
    fun testCreateAccessCodeResponseShape() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val response =
                client.post("/v1/bookings/$testBookingId/accesscode") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                }

            assertEquals(HttpStatusCode.OK, response.status)
            val json = Json.Default.parseToJsonElement(response.bodyAsText()).jsonObject

            // Verify accessCode is a non-empty string
            val accessCode = json["accessCode"]?.jsonPrimitive?.content
            assertNotNull(accessCode)
            assert(accessCode.isNotEmpty())

            // Verify accessCodes array has entries matching the number of smartlocks
            val accessCodes = json["accessCodes"]!!.jsonArray
            assertEquals(1, accessCodes.size)

            // Verify each access code entry has expected fields
            val codeEntry = accessCodes[0].jsonObject
            assertNotNull(codeEntry["id"])
            assertEquals(testBookingId.toString(), codeEntry["booking_id"]?.jsonPrimitive?.content)
            assertEquals(testSmartlockId.toString(), codeEntry["smartlock_id"]?.jsonPrimitive?.content)
            assertEquals(accessCode, codeEntry["access_code"]?.jsonPrimitive?.content)
            assertNotNull(codeEntry["code_start_datetime"])
            assertNotNull(codeEntry["code_end_datetime"])
            assertNotNull(codeEntry["created_at"])
            assertNotNull(codeEntry["updated_at"])

            // Verify failedSmartlocks is present and empty on success
            val failedSmartlocks = json["failedSmartlocks"]!!.jsonArray
            assertEquals(0, failedSmartlocks.size)
        }

    @Test
    fun testCreateAccessCodePartialFailure() =
        testApplication {
            // Set up a property with two smartlocks: one Nuki (succeeds) and one Yale (fails in MockPluginFactory)
            val propertyId = Uuid.parse("30000000-0000-0000-0000-000000000020")
            val eventId = Uuid.parse("E0000000-0000-0000-0000-000000000020")
            val bookingId = Uuid.parse("40000000-0000-0000-0000-000000000020")
            val nukiSmartlockId = Uuid.parse("50000000-0000-0000-0000-000000000020")
            val yaleSmartlockId = Uuid.parse("50000000-0000-0000-0000-000000000021")
            val nukiDetailId = Uuid.parse("60000000-0000-0000-0000-000000000020")

            testDBConnection.insertProperty(propertyId, TEST_ACCOUNT_ID_1, "Property with mixed locks")

            testDBConnection.executeQuery(
                "INSERT INTO public.calendar_events (id, property_id, from_datetime, to_datetime, \"type\", created_at, updated_at) " +
                    "VALUES('$eventId', '$propertyId', '${checkinTime.format(dtf)}', '${checkoutTime.format(dtf)}', 'booking', now(), now())",
            )
            testDBConnection.executeQuery(
                "INSERT INTO public.bookings (id, calendar_event_id, property_id, channel, channel_id, status, guest_first_name, guest_last_name, adults_count, created_at, updated_at) " +
                    "VALUES('$bookingId', '$eventId', '$propertyId', 'direct', 'ref2', 'confirmed', 'Ana', 'Garcia', 2, now(), now())",
            )

            // Nuki smartlock (will succeed)
            testDBConnection.executeQuery(
                "INSERT INTO public.property_smartlocks (id, property_id, vendor, name, created_at, updated_at) " +
                    "VALUES('$nukiSmartlockId', '$propertyId', 'nuki', 'Front Door', now(), now())",
            )
            testDBConnection.executeQuery(
                "INSERT INTO public.account_integrations (id, account_id, vendor, created_at, updated_at) " +
                    "VALUES(gen_random_uuid(), '${TEST_ACCOUNT_ID_1}', 'nuki', now(), now()) ON CONFLICT DO NOTHING",
            )
            testDBConnection.executeQuery(
                "INSERT INTO public.account_nuki_integrations (id, account_integration_id, nuki_token, created_at, updated_at) " +
                    "VALUES(gen_random_uuid(), (SELECT id FROM account_integrations WHERE account_id = '${TEST_ACCOUNT_ID_1}' AND vendor = 'nuki' LIMIT 1), 'test_token', now(), now()) " +
                    "ON CONFLICT DO NOTHING",
            )
            testDBConnection.executeQuery(
                "INSERT INTO public.property_nuki_smartlocks (id, property_smartlock_id, nuki_integration_id, nuki_smartlock_id, created_at, updated_at) " +
                    "VALUES('$nukiDetailId', '$nukiSmartlockId', (SELECT id FROM account_nuki_integrations LIMIT 1), '123456789', now(), now())",
            )

            // Yale smartlock (will fail — MockPluginFactory throws for Yale)
            testDBConnection.executeQuery(
                "INSERT INTO public.property_smartlocks (id, property_id, vendor, name, created_at, updated_at) " +
                    "VALUES('$yaleSmartlockId', '$propertyId', 'yale', 'Back Door', now(), now())",
            )

            application { configureTestModules() }
            val client = createClient()

            val response =
                client.post("/v1/bookings/$bookingId/accesscode") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                }

            assertEquals(HttpStatusCode.OK, response.status)
            val json = Json.Default.parseToJsonElement(response.bodyAsText()).jsonObject

            // accessCode should still be returned
            assertNotNull(json["accessCode"]?.jsonPrimitive?.content)

            // One smartlock succeeded
            val accessCodes = json["accessCodes"]!!.jsonArray
            assertEquals(1, accessCodes.size)

            // One smartlock failed
            val failedSmartlocks = json["failedSmartlocks"]!!.jsonArray
            assertEquals(1, failedSmartlocks.size)
            val failedLock = failedSmartlocks[0].jsonObject
            assertEquals(yaleSmartlockId.toString(), failedLock["id"]?.jsonPrimitive?.content)
            assertEquals("Yale", failedLock["vendor"]?.jsonPrimitive?.content)
            assertEquals("Back Door", failedLock["name"]?.jsonPrimitive?.content)
        }

    @Test
    fun testCreateAccessCodeCompletedBooking() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            // Create a booking with past dates
            val propertyId = TEST_ACCOUNT_1_PROPERTY_1.id
            val pastCheckin = LocalDate.now().minusDays(10).atTime(17, 0)
            val pastCheckout = LocalDate.now().minusDays(5).atTime(11, 0)
            val eventId = Uuid.parse("30000000-0000-0000-0000-000000000030")
            val bookingId = Uuid.parse("40000000-0000-0000-0000-000000000030")

            testDBConnection.executeQuery(
                "INSERT INTO public.calendar_events (id, property_id, from_datetime, to_datetime, \"type\", created_at, updated_at) " +
                    "VALUES('$eventId', '$propertyId', '${pastCheckin.format(dtf)}', '${pastCheckout.format(dtf)}', 'booking', now(), now())",
            )
            testDBConnection.executeQuery(
                "INSERT INTO public.bookings (id, calendar_event_id, property_id, channel, channel_id, status, guest_first_name, guest_last_name, adults_count, created_at, updated_at) " +
                    "VALUES('$bookingId', '$eventId', '$propertyId', 'direct', 'past1', 'confirmed', 'Past', 'Guest', 2, now(), now())",
            )

            val response =
                client.post("/v1/bookings/$bookingId/accesscode") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                }

            assertEquals(HttpStatusCode.Forbidden, response.status)
            assertContains(response.bodyAsText(), Errors.ERROR_BOOKING_ALREADY_COMPLETED)
        }

    // DELETE /accesscodes/{id} tests

    @Test
    fun testDeleteAccessCodeSuccess() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            // Get the access code ID for the booking that has one
            val accessCodeId =
                testDBConnection.createStatement().use { stmt ->
                    val rs = stmt.executeQuery("SELECT id FROM booking_access_codes WHERE booking_id = '$testBookingWithCodeId' LIMIT 1")
                    rs.next()
                    rs.getString("id")
                }

            val response =
                client.delete("/v1/accesscodes/$accessCodeId") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                }

            assertEquals(HttpStatusCode.NoContent, response.status)
        }

    @Test
    fun testDeleteAccessCodeUnauthorized() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val accessCodeId =
                testDBConnection.createStatement().use { stmt ->
                    val rs = stmt.executeQuery("SELECT id FROM booking_access_codes WHERE booking_id = '$testBookingWithCodeId' LIMIT 1")
                    rs.next()
                    rs.getString("id")
                }

            val response = client.delete("/v1/accesscodes/$accessCodeId")

            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }

    @Test
    fun testDeleteAccessCodeWrongAccount() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val accessCodeId =
                testDBConnection.createStatement().use { stmt ->
                    val rs = stmt.executeQuery("SELECT id FROM booking_access_codes WHERE booking_id = '$testBookingWithCodeId' LIMIT 1")
                    rs.next()
                    rs.getString("id")
                }

            val response =
                client.delete("/v1/accesscodes/$accessCodeId") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_2_USER_3.token}")
                }

            assertEquals(HttpStatusCode.Forbidden, response.status)
        }

    @Test
    fun testDeleteAccessCodeContractorDenied() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            testDBConnection.grantCrewAccess(
                userId = TEST_ACCOUNT_2_USER_3.id,
                propertyId = TEST_ACCOUNT_1_PROPERTY_1.id,
                service = "cleaning",
                accessLevel = "contractor",
            )

            val accessCodeId =
                testDBConnection.createStatement().use { stmt ->
                    val rs = stmt.executeQuery("SELECT id FROM booking_access_codes WHERE booking_id = '$testBookingWithCodeId' LIMIT 1")
                    rs.next()
                    rs.getString("id")
                }

            val response =
                client.delete("/v1/accesscodes/$accessCodeId") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_2_USER_3.token}")
                }

            assertEquals(HttpStatusCode.Forbidden, response.status)

            // The code must still exist — nothing was revoked
            val stillExists =
                testDBConnection.createStatement().use { stmt ->
                    val rs = stmt.executeQuery("SELECT count(*) FROM booking_access_codes WHERE id = '$accessCodeId'")
                    rs.next()
                    rs.getInt(1)
                }
            assertEquals(1, stillExists)
        }

    @Test
    fun testDeleteAccessCodeStaffDenied() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            testDBConnection.grantCrewAccess(
                userId = TEST_ACCOUNT_2_USER_3.id,
                propertyId = TEST_ACCOUNT_1_PROPERTY_1.id,
                service = "cleaning",
                accessLevel = "staff",
            )

            val accessCodeId =
                testDBConnection.createStatement().use { stmt ->
                    val rs = stmt.executeQuery("SELECT id FROM booking_access_codes WHERE booking_id = '$testBookingWithCodeId' LIMIT 1")
                    rs.next()
                    rs.getString("id")
                }

            val response =
                client.delete("/v1/accesscodes/$accessCodeId") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_2_USER_3.token}")
                }

            assertEquals(HttpStatusCode.Forbidden, response.status)

            // The code must still exist — nothing was revoked
            val stillExists =
                testDBConnection.createStatement().use { stmt ->
                    val rs = stmt.executeQuery("SELECT count(*) FROM booking_access_codes WHERE id = '$accessCodeId'")
                    rs.next()
                    rs.getInt(1)
                }
            assertEquals(1, stillExists)
        }

    @Test
    fun testDeleteAccessCodeManagerAllowed() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            testDBConnection.grantCrewAccess(
                userId = TEST_ACCOUNT_2_USER_3.id,
                propertyId = TEST_ACCOUNT_1_PROPERTY_1.id,
                accessLevel = "manager",
            )

            val accessCodeId =
                testDBConnection.createStatement().use { stmt ->
                    val rs = stmt.executeQuery("SELECT id FROM booking_access_codes WHERE booking_id = '$testBookingWithCodeId' LIMIT 1")
                    rs.next()
                    rs.getString("id")
                }

            val response =
                client.delete("/v1/accesscodes/$accessCodeId") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_2_USER_3.token}")
                }

            assertEquals(HttpStatusCode.NoContent, response.status)
        }

    @Test
    fun testDeleteAccessCodeNotFound() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val nonExistentId = Uuid.parse("99999999-9999-9999-9999-999999999999")
            val response =
                client.delete("/v1/accesscodes/$nonExistentId") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                }

            assertEquals(HttpStatusCode.NotFound, response.status)
        }

    // PATCH /bookings/{id} — date changes should propagate to existing access codes

    private fun fetchAccessCodeWindow(bookingId: Uuid): Pair<OffsetDateTime, OffsetDateTime> =
        testDBConnection.createStatement().use { stmt ->
            val rs =
                stmt.executeQuery(
                    "SELECT code_start_datetime, code_end_datetime FROM booking_access_codes " +
                        "WHERE booking_id = '$bookingId' AND deleted_at IS NULL LIMIT 1",
                )
            rs.next()
            rs.getObject("code_start_datetime", OffsetDateTime::class.java) to
                rs.getObject("code_end_datetime", OffsetDateTime::class.java)
        }

    @Test
    fun testUpdateBookingDatesPropagatesToAccessCodes() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val newFrom = tomorrow.plusDays(10)
            val newTo = tomorrow.plusDays(15)
            val newCheckinTime = LocalTime.of(15, 0)
            val newCheckoutTime = LocalTime.of(10, 0)

            val response =
                client.patch("/v1/bookings/$testBookingWithCodeId") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                    contentType(ContentType.Application.Json)
                    setBody(
                        BookingData(
                            from = newFrom,
                            to = newTo,
                            checkinTime = newCheckinTime,
                            checkoutTime = newCheckoutTime,
                        ),
                    )
                }

            assertEquals(HttpStatusCode.OK, response.status)

            val (start, end) = fetchAccessCodeWindow(testBookingWithCodeId)
            val madrid = ZoneId.of("Europe/Madrid")
            val expectedStart = createLocalTimeUtc(newFrom, newCheckinTime, "Europe/Madrid").atZone(madrid).toInstant()
            val expectedEnd = createLocalTimeUtc(newTo, newCheckoutTime, "Europe/Madrid").atZone(madrid).toInstant()
            assertEquals(expectedStart, start.toInstant())
            assertEquals(expectedEnd, end.toInstant())
        }

    @Test
    fun testUpdateBookingNonDateFieldsLeavesAccessCodesUntouched() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val (originalStart, originalEnd) = fetchAccessCodeWindow(testBookingWithCodeId)

            val response =
                client.patch("/v1/bookings/$testBookingWithCodeId") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                    contentType(ContentType.Application.Json)
                    setBody(BookingData(notes = "just a note, no date change"))
                }

            assertEquals(HttpStatusCode.OK, response.status)

            val (start, end) = fetchAccessCodeWindow(testBookingWithCodeId)
            assertEquals(originalStart.toInstant(), start.toInstant())
            assertEquals(originalEnd.toInstant(), end.toInstant())
        }

    @Test
    fun testDeleteAccessCodeInvalidId() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val response =
                client.delete("/v1/accesscodes/invalid-uuid") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                }

            assertEquals(HttpStatusCode.BadRequest, response.status)
        }
}
