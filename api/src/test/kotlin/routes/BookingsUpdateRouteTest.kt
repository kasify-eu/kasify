package eu.kasify.routes
import eu.kasify.TEST_ACCOUNT_1_PROPERTY_1
import eu.kasify.TEST_ACCOUNT_1_USER_1
import eu.kasify.TEST_SPAIN_COUNTRY_ID
import eu.kasify.extensions.configureTestModules
import eu.kasify.extensions.createClient
import eu.kasify.helpers.insertBooking
import eu.kasify.helpers.insertTask
import eu.kasify.model.api.requests.BookingData
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
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
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.uuid.Uuid

class BookingsUpdateRouteTest : BaseRouteDBTests() {
    private val testBookingId = Uuid.parse("40000000-0000-0000-0000-000000000099")

    override fun setupData() {
        testDBConnection.insertBooking(
            bookingId = testBookingId,
            propertyId = TEST_ACCOUNT_1_PROPERTY_1.id,
            from = LocalDateTime.parse("2026-01-01T17:00:00"),
            to = LocalDateTime.parse("2026-01-05T11:00:00"),
            adultsCount = 2,
            childrenCount = 0,
            infantsCount = 0,
        )
    }

    private fun insertGuest(
        bookingId: Uuid,
        firstName: String,
    ) {
        testDBConnection.executeQuery(
            "INSERT INTO guests (booking_id, property_id, first_name, last_name, nationality_country_id) " +
                "VALUES ('$bookingId', '${TEST_ACCOUNT_1_PROPERTY_1.id}', '$firstName', 'Doe', '$TEST_SPAIN_COUNTRY_ID')",
        )
    }

    private fun markCheckinCompleted(bookingId: Uuid) {
        testDBConnection.executeQuery(
            "UPDATE bookings SET checkin_completed_at = now() WHERE id = '$bookingId'",
        )
    }

    private fun markSesSubmitted(bookingId: Uuid) {
        // SES "submitted" state now lives in ses_communication: an accepted PV alta.
        testDBConnection.executeQuery(
            "INSERT INTO ses_communication " +
                "(booking_id, communication_type, operation, status, lote, created_at, updated_at) " +
                "VALUES ('$bookingId', 'pv', 'alta', 'accepted', 'TEST-BATCH', now(), now())",
        )
    }

    private fun fetchCheckinCompletedAt(bookingId: Uuid): String? {
        var result: String? = null
        testDBConnection.createStatement().use { stmt ->
            val rs = stmt.executeQuery("SELECT checkin_completed_at FROM bookings WHERE id = '$bookingId'")
            if (rs.next()) result = rs.getString(1)
        }
        return result
    }

    @Test
    fun `Cannot change guest counts after SES submission`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            insertGuest(testBookingId, "Alice")
            insertGuest(testBookingId, "Bob")
            markCheckinCompleted(testBookingId)
            markSesSubmitted(testBookingId)

            val response =
                client.patch("/v1/bookings/$testBookingId") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                    contentType(ContentType.Application.Json)
                    setBody(BookingData(adultsCount = 3))
                }

            assertEquals(HttpStatusCode.Conflict, response.status)
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            assertEquals("booking.ses_already_submitted", body["code"]?.jsonPrimitive?.content)
        }

    @Test
    fun `Repeating same guest count after SES submission is allowed`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            markSesSubmitted(testBookingId)

            // Existing adultsCount is 2; sending the same value should be a no-op, not a 409.
            val response =
                client.patch("/v1/bookings/$testBookingId") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                    contentType(ContentType.Application.Json)
                    setBody(BookingData(adultsCount = 2, notes = "still editable"))
                }

            assertEquals(HttpStatusCode.OK, response.status)
        }

    @Test
    fun `Non-guest-count fields remain editable after SES submission`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            markSesSubmitted(testBookingId)

            val response =
                client.patch("/v1/bookings/$testBookingId") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                    contentType(ContentType.Application.Json)
                    setBody(BookingData(notes = "Updated notes after SES"))
                }

            assertEquals(HttpStatusCode.OK, response.status)

            val getResp =
                client.get("/v1/bookings/$testBookingId") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                }
            val notes =
                Json
                    .parseToJsonElement(getResp.bodyAsText())
                    .jsonObject["notes"]
                    ?.jsonPrimitive
                    ?.content
            assertEquals("Updated notes after SES", notes)
        }

    @Test
    fun `Increasing guest count clears checkin_completed_at`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            insertGuest(testBookingId, "Alice")
            insertGuest(testBookingId, "Bob")
            markCheckinCompleted(testBookingId)

            assertNotNull(fetchCheckinCompletedAt(testBookingId))

            val response =
                client.patch("/v1/bookings/$testBookingId") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                    contentType(ContentType.Application.Json)
                    setBody(BookingData(adultsCount = 3))
                }

            assertEquals(HttpStatusCode.OK, response.status)
            assertNull(fetchCheckinCompletedAt(testBookingId))
        }

    @Test
    fun `Decreasing guest count to match registered sets checkin_completed_at`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            // Override seed: 3 expected, 2 registered, checkin not yet complete.
            testDBConnection.executeQuery(
                "UPDATE bookings SET adults_count = 3, checkin_completed_at = NULL WHERE id = '$testBookingId'",
            )
            insertGuest(testBookingId, "Alice")
            insertGuest(testBookingId, "Bob")

            assertNull(fetchCheckinCompletedAt(testBookingId))

            val response =
                client.patch("/v1/bookings/$testBookingId") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                    contentType(ContentType.Application.Json)
                    setBody(BookingData(adultsCount = 2))
                }

            assertEquals(HttpStatusCode.OK, response.status)
            assertNotNull(fetchCheckinCompletedAt(testBookingId))
        }

    @Test
    fun `Updating only non-guest-count fields does not touch checkin_completed_at`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            insertGuest(testBookingId, "Alice")
            insertGuest(testBookingId, "Bob")
            markCheckinCompleted(testBookingId)

            val before = fetchCheckinCompletedAt(testBookingId)
            assertNotNull(before)

            val response =
                client.patch("/v1/bookings/$testBookingId") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                    contentType(ContentType.Application.Json)
                    setBody(BookingData(notes = "just notes"))
                }

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(before, fetchCheckinCompletedAt(testBookingId))
        }

    @Test
    fun `PATCH response includes registered guests`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            insertGuest(testBookingId, "Alice")
            insertGuest(testBookingId, "Bob")

            val response =
                client.patch("/v1/bookings/$testBookingId") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                    contentType(ContentType.Application.Json)
                    setBody(BookingData(guestFirstName = "Updated"))
                }

            assertEquals(HttpStatusCode.OK, response.status)
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            val guests = body["guests"]?.jsonArray
            assertNotNull(guests, "guests array should be present in PATCH response")
            assertEquals(2, guests.size)
            val firstNames = guests.map { it.jsonObject["first_name"]?.jsonPrimitive?.content }.toSet()
            assertEquals(setOf("Alice", "Bob"), firstNames)
        }

    @Test
    fun `PATCH response includes cleaning task when one exists`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val taskId =
                testDBConnection.insertTask(
                    propertyId = TEST_ACCOUNT_1_PROPERTY_1.id,
                    bookingId = testBookingId,
                    taskType = "cleaning",
                    assignedTo = TEST_ACCOUNT_1_USER_1.id,
                    scheduledDate = LocalDateTime.parse("2026-01-05T11:00:00"),
                    notes = "Deep clean after stay",
                )

            val response =
                client.patch("/v1/bookings/$testBookingId") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                    contentType(ContentType.Application.Json)
                    setBody(BookingData(guestFirstName = "Updated"))
                }

            assertEquals(HttpStatusCode.OK, response.status)
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            val cleaningTask = body["cleaning_task"]?.jsonObject
            assertNotNull(cleaningTask, "cleaning_task should be present in PATCH response")
            assertEquals(taskId.toString(), cleaningTask["id"]?.jsonPrimitive?.content)
            assertEquals("Deep clean after stay", cleaningTask["notes"]?.jsonPrimitive?.content)
        }

    @Test
    fun `PATCH response includes access codes for the booking`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val smartlockId = Uuid.parse("50000000-0000-0000-0000-000000000099")
            testDBConnection.executeQuery(
                "INSERT INTO public.property_smartlocks " +
                    "(id, property_id, vendor, name, created_at, updated_at) " +
                    "VALUES('$smartlockId', '${TEST_ACCOUNT_1_PROPERTY_1.id}', 'nuki', 'Front Door', now(), now())",
            )
            testDBConnection.executeQuery(
                "INSERT INTO public.booking_access_codes " +
                    "(id, booking_id, smartlock_id, access_code, vendor_code_id, " +
                    "code_start_datetime, code_end_datetime, created_at, updated_at) " +
                    "VALUES(gen_random_uuid(), '$testBookingId', '$smartlockId', '424242', " +
                    "'vendor-1', '2026-01-01 17:00:00', '2026-01-05 11:00:00', now(), now())",
            )

            val response =
                client.patch("/v1/bookings/$testBookingId") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                    contentType(ContentType.Application.Json)
                    setBody(BookingData(guestFirstName = "Updated"))
                }

            assertEquals(HttpStatusCode.OK, response.status)
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            val accessCodes = body["access_codes"]?.jsonArray
            assertNotNull(accessCodes, "access_codes should be present in PATCH response")
            assertEquals(1, accessCodes.size)
            val codeEntry = accessCodes[0].jsonObject
            assertEquals("424242", codeEntry["access_code"]?.jsonPrimitive?.content)
            assertEquals(smartlockId.toString(), codeEntry["smartlock_id"]?.jsonPrimitive?.content)
            assertEquals("Front Door", codeEntry["smartlock_name"]?.jsonPrimitive?.content)
        }

    @Test
    fun `Owner can set guest review fields`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val response =
                client.patch("/v1/bookings/$testBookingId") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                    contentType(ContentType.Application.Json)
                    setBody(BookingData(wouldHostAgain = true, guestReviewNotes = "Left the house spotless"))
                }

            assertEquals(HttpStatusCode.OK, response.status)
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            assertEquals("true", body["would_host_again"]?.jsonPrimitive?.content)
            assertEquals("Left the house spotless", body["guest_review_notes"]?.jsonPrimitive?.content)

            val getResp =
                client.get("/v1/bookings/$testBookingId") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                }
            val getBody = Json.parseToJsonElement(getResp.bodyAsText()).jsonObject
            assertEquals("true", getBody["would_host_again"]?.jsonPrimitive?.content)
            assertEquals("Left the house spotless", getBody["guest_review_notes"]?.jsonPrimitive?.content)
        }

    @Test
    fun `Updating other fields leaves guest review untouched`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            testDBConnection.executeQuery(
                "UPDATE bookings SET would_host_again = false, guest_review_notes = 'Broke a chair' " +
                    "WHERE id = '$testBookingId'",
            )

            val response =
                client.patch("/v1/bookings/$testBookingId") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                    contentType(ContentType.Application.Json)
                    setBody(BookingData(notes = "operational notes"))
                }

            assertEquals(HttpStatusCode.OK, response.status)
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            assertEquals("false", body["would_host_again"]?.jsonPrimitive?.content)
            assertEquals("Broke a chair", body["guest_review_notes"]?.jsonPrimitive?.content)
        }
}
