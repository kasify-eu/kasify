package eu.kasify.routes
import eu.kasify.TEST_ACCOUNT_1_PROPERTY_1
import eu.kasify.TEST_ACCOUNT_1_USER_1
import eu.kasify.TEST_ACCOUNT_2_USER_3
import eu.kasify.db.model.BookingChannel
import eu.kasify.db.model.BookingStatus
import eu.kasify.extensions.configureTestModules
import eu.kasify.extensions.createClient
import eu.kasify.helpers.insertBooking
import eu.kasify.model.api.requests.BookingData
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
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.uuid.Uuid

class BookingsRouteTest : BaseRouteDBTests() {
    private val testBookingId = Uuid.Companion.parse("40000000-0000-0000-0000-000000000001")

    override fun setupData() {
        val sqlEvent =
            "INSERT INTO public.calendar_events " +
                "(id, property_id, from_datetime, to_datetime, \"type\", created_at, updated_at)\n" +
                "VALUES('30000000-0000-0000-0000-000000000001', '${TEST_ACCOUNT_1_PROPERTY_1.id}', '2025-01-01 17:00:00.000', '2025-02-01 11:00:00.000', 'booking', '2025-07-11 17:52:49.558', '2025-07-11 17:52:49.558');"
        testDBConnection.executeQuery(sqlEvent)

        val sql =
            "INSERT INTO public.bookings " +
                "(id, calendar_event_id, property_id, channel, channel_id, status, notes, created_at, updated_at) " +
                "VALUES('$testBookingId', '30000000-0000-0000-0000-000000000001', '${TEST_ACCOUNT_1_PROPERTY_1.id}', 'airbnb', 'airbnbId123', 'confirmed', 'My notes', '2025-07-11 17:52:49.558', '2025-07-11 17:52:49.558')"

        testDBConnection.executeQuery(sql)
    }

    @Test
    fun testGetBookingsSuccess() =
        testApplication {
            application { configureTestModules() }

            val client = createClient()

            val response =
                client.get("/v1/properties/${TEST_ACCOUNT_1_PROPERTY_1.id}/bookings") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                }

            val responseText = response.bodyAsText()

            assertEquals(HttpStatusCode.Companion.OK, response.status)
            assertContains(responseText, testBookingId.toString())
            assertContains(responseText, "2025-01-01")
            assertContains(responseText, "2025-02-01")
        }

    @Test
    fun testGetBookingsUnauthorized() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val response = client.get("/v1/properties/${TEST_ACCOUNT_1_PROPERTY_1.id}/bookings")

            assertEquals(HttpStatusCode.Companion.Unauthorized, response.status)
        }

    @Test
    fun testGetBookingsInvalidToken() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val response =
                client.get("/v1/properties/${TEST_ACCOUNT_1_PROPERTY_1.id}/bookings") {
                    header(HttpHeaders.Authorization, "Bearer invalid-token")
                }

            assertEquals(HttpStatusCode.Companion.Unauthorized, response.status)
        }

    @Test
    fun testGetBookingsWrongAccountIdNotFound() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val response =
                client.get("/v1/properties/${TEST_ACCOUNT_1_PROPERTY_1.id}/bookings") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_2_USER_3.token}")
                }

            assertEquals(HttpStatusCode.Companion.Forbidden, response.status)
        }

    @Test
    fun testGetBookingsInvalidUuid() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val response =
                client.get("/v1/properties/invalid-uuid/bookings") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                }

            assertEquals(HttpStatusCode.Companion.BadRequest, response.status)
        }

    @Test
    fun `Create booking successfully`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val bookingData =
                BookingData(
                    from = LocalDate.parse("2024-02-01"),
                    to = LocalDate.parse("2024-02-05"),
                    channelId = "test-channel-id",
                    channel = BookingChannel.Direct,
                    status = BookingStatus.Confirmed,
                    notes = "test notes",
                    guestFirstName = "First",
                    guestLastName = "Last",
                    guestEmail = "email@test.com",
                    guestPhone = "1234567890",
                    adultsCount = 9,
                    childrenCount = 6,
                    infantsCount = 3,
                    specialRequests = "test special requests",
                    arrivalTime = LocalTime.parse("10:00"),
                    departureTime = LocalTime.parse("17:00"),
                )

            val response =
                client.post("/v1/properties/${TEST_ACCOUNT_1_PROPERTY_1.id}/bookings") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                    contentType(ContentType.Application.Json)
                    setBody(bookingData)
                }

            val responseText = response.bodyAsText()
            assertEquals(HttpStatusCode.Companion.OK, response.status)
            assertContains(responseText, "2024-02-01")
            assertContains(responseText, "2024-02-05")
            assertContains(responseText, "test-channel-id")

            // check if we have 2 bookings
            val bookingsList =
                client.get("/v1/properties/${TEST_ACCOUNT_1_PROPERTY_1.id}/bookings") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                }
            val bookingsListJson = bookingsList.bodyAsText()
            val bookingsJsonArray =
                Json.Default
                    .parseToJsonElement(bookingsListJson)
                    .jsonObject["bookings"]!!
                    .jsonArray
            assertEquals(2, bookingsJsonArray.size)
            assertEquals("2024-02-01", bookingsJsonArray[0].jsonObject["from"]?.jsonPrimitive?.content)
            assertEquals("2024-02-05", bookingsJsonArray[0].jsonObject["to"]?.jsonPrimitive?.content)
            assertEquals("test-channel-id", bookingsJsonArray[0].jsonObject["channel_id"]?.jsonPrimitive?.content)
            assertEquals("Confirmed", bookingsJsonArray[0].jsonObject["status"]?.jsonPrimitive?.content)
            assertEquals("test notes", bookingsJsonArray[0].jsonObject["notes"]?.jsonPrimitive?.content)
            assertEquals("First", bookingsJsonArray[0].jsonObject["guest_first_name"]?.jsonPrimitive?.content)
            assertEquals("Last", bookingsJsonArray[0].jsonObject["guest_last_name"]?.jsonPrimitive?.content)
            assertEquals("email@test.com", bookingsJsonArray[0].jsonObject["guest_email"]?.jsonPrimitive?.content)
            assertEquals("1234567890", bookingsJsonArray[0].jsonObject["guest_phone"]?.jsonPrimitive?.content)
            assertEquals(
                9,
                bookingsJsonArray[0]
                    .jsonObject["adults_count"]
                    ?.jsonPrimitive
                    ?.content
                    ?.toInt(),
            )
            assertEquals(
                6,
                bookingsJsonArray[0]
                    .jsonObject["children_count"]
                    ?.jsonPrimitive
                    ?.content
                    ?.toInt(),
            )
            assertEquals(
                3,
                bookingsJsonArray[0]
                    .jsonObject["infants_count"]
                    ?.jsonPrimitive
                    ?.content
                    ?.toInt(),
            )
            assertEquals(
                "test special requests",
                bookingsJsonArray[0].jsonObject["special_requests"]?.jsonPrimitive?.content,
            )
            assertEquals("10:00", bookingsJsonArray[0].jsonObject["arrival_time"]?.jsonPrimitive?.content)
            assertEquals("17:00", bookingsJsonArray[0].jsonObject["departure_time"]?.jsonPrimitive?.content)
        }

    @Test
    fun testCreateBookingUnauthorized() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val bookingData =
                BookingData(
                    from = LocalDate.parse("2024-02-01"),
                    to = LocalDate.parse("2024-02-05"),
                    channelId = "test-channel-id",
                )

            val response =
                client.post("/v1/properties/${TEST_ACCOUNT_1_PROPERTY_1.id}/bookings") {
                    contentType(ContentType.Application.Json)
                    setBody(bookingData)
                }

            assertEquals(HttpStatusCode.Companion.Unauthorized, response.status)
        }

    @Test
    fun testCreateBookingInvalidToken() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val bookingData =
                BookingData(
                    from = LocalDate.parse("2024-02-01"),
                    to = LocalDate.parse("2024-02-05"),
                    channelId = "test-channel-id",
                )

            val response =
                client.post("/v1/properties/${TEST_ACCOUNT_1_PROPERTY_1.id}/bookings") {
                    header(HttpHeaders.Authorization, "Bearer invalid-token")
                    contentType(ContentType.Application.Json)
                    setBody(bookingData)
                }

            assertEquals(HttpStatusCode.Companion.Unauthorized, response.status)
        }

    @Test
    fun testCreateBookingWrongAccountIdNotFound() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val bookingData =
                BookingData(
                    from = LocalDate.parse("2024-02-01"),
                    to = LocalDate.parse("2024-02-05"),
                    channelId = "test-channel-id",
                )

            val response =
                client.post("/v1/properties/${TEST_ACCOUNT_1_PROPERTY_1.id}/bookings") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_2_USER_3.token}")
                    contentType(ContentType.Application.Json)
                    setBody(bookingData)
                }

            assertEquals(HttpStatusCode.Companion.Forbidden, response.status)
        }

    @Test
    fun testCreateBookingInvalidUuid() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val bookingData =
                BookingData(
                    from = LocalDate.parse("2024-02-01"),
                    to = LocalDate.parse("2024-02-05"),
                    channelId = "test-channel-id",
                )

            val response =
                client.post("/v1/properties/invalid-uuid/bookings") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                    contentType(ContentType.Application.Json)
                    setBody(bookingData)
                }

            assertEquals(HttpStatusCode.Companion.BadRequest, response.status)
        }

    @Test
    fun testCreateBookingInvalidJson() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val response =
                client.post("/v1/properties/${TEST_ACCOUNT_1_PROPERTY_1.id}/bookings") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                    contentType(ContentType.Application.Json)
                    setBody("{invalid json}")
                }

            assertEquals(HttpStatusCode.Companion.BadRequest, response.status)
        }

    @Test
    fun testCreateBookingMissingFields() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val response =
                client.post("/v1/properties/${TEST_ACCOUNT_1_PROPERTY_1.id}/bookings") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                    contentType(ContentType.Application.Json)
                    setBody("{}")
                }

            assertEquals(HttpStatusCode.Companion.BadRequest, response.status)
        }

    @Test
    fun testCurrentBookingNotFound() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val response =
                client.get("/v1/properties/${TEST_ACCOUNT_1_PROPERTY_1.id}/bookings/current") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                }

            assertEquals(HttpStatusCode.Companion.NotFound, response.status)
        }

    @Test
    fun testCurrentBookingFound() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val from = LocalDateTime.now().minusDays(1)
            val to = LocalDateTime.now().plusDays(1)

            testDBConnection.insertBooking(
                propertyId = TEST_ACCOUNT_1_PROPERTY_1.id,
                from = from,
                to = to,
            )

            val response =
                client.get("/v1/properties/${TEST_ACCOUNT_1_PROPERTY_1.id}/bookings/current") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                }

            assertEquals(HttpStatusCode.Companion.OK, response.status)
            val responseText = response.bodyAsText()
            assertContains(responseText, "\"from\":\"${from.toLocalDate()}\"")
            assertContains(responseText, "\"to\":\"${to.toLocalDate()}\"")
        }

    @Test
    fun `Creating 2 equal bookings should not be allowed`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val bookingData =
                BookingData(
                    from = LocalDate.parse("2024-02-01"),
                    to = LocalDate.parse("2024-02-05"),
                    channelId = "test-channel-id",
                    channel = BookingChannel.Direct,
                    status = BookingStatus.Confirmed,
                )

            var response =
                client.post("/v1/properties/${TEST_ACCOUNT_1_PROPERTY_1.id}/bookings") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                    contentType(ContentType.Application.Json)
                    setBody(bookingData)
                }

            assertEquals(HttpStatusCode.Companion.OK, response.status)

            response =
                client.post("/v1/properties/${TEST_ACCOUNT_1_PROPERTY_1.id}/bookings") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                    contentType(ContentType.Application.Json)
                    setBody(bookingData)
                }
            assertEquals(HttpStatusCode.Companion.Conflict, response.status)
        }

    @Test
    fun `Creating 1 booking inside another one should not be allowed`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            var bookingData =
                BookingData(
                    from = LocalDate.parse("2024-02-01"),
                    to = LocalDate.parse("2024-02-05"),
                    channelId = "test-channel-id",
                    channel = BookingChannel.Direct,
                    status = BookingStatus.Confirmed,
                )

            var response =
                client.post("/v1/properties/${TEST_ACCOUNT_1_PROPERTY_1.id}/bookings") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                    contentType(ContentType.Application.Json)
                    setBody(bookingData)
                }

            assertEquals(HttpStatusCode.Companion.OK, response.status)

            bookingData =
                BookingData(
                    from = LocalDate.parse("2024-02-02"),
                    to = LocalDate.parse("2024-02-04"),
                    channelId = "test-channel-id",
                    channel = BookingChannel.Direct,
                    status = BookingStatus.Confirmed,
                )

            response =
                client.post("/v1/properties/${TEST_ACCOUNT_1_PROPERTY_1.id}/bookings") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                    contentType(ContentType.Application.Json)
                    setBody(bookingData)
                }
            assertEquals(HttpStatusCode.Companion.Conflict, response.status)
        }

    @Test
    fun `Creating 1 booking with over another one should not be allowed`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            var bookingData =
                BookingData(
                    from = LocalDate.parse("2024-02-01"),
                    to = LocalDate.parse("2024-02-05"),
                    channelId = "test-channel-id",
                    channel = BookingChannel.Direct,
                    status = BookingStatus.Confirmed,
                )

            var response =
                client.post("/v1/properties/${TEST_ACCOUNT_1_PROPERTY_1.id}/bookings") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                    contentType(ContentType.Application.Json)
                    setBody(bookingData)
                }

            assertEquals(HttpStatusCode.Companion.OK, response.status)

            bookingData =
                BookingData(
                    from = LocalDate.parse("2024-01-01"),
                    to = LocalDate.parse("2024-02-07"),
                    channelId = "test-channel-id",
                    channel = BookingChannel.Direct,
                    status = BookingStatus.Confirmed,
                )

            response =
                client.post("/v1/properties/${TEST_ACCOUNT_1_PROPERTY_1.id}/bookings") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                    contentType(ContentType.Application.Json)
                    setBody(bookingData)
                }
            assertEquals(HttpStatusCode.Companion.Conflict, response.status)
        }

    @Test
    fun `Creating 1 booking the same day as other ends should be allowed`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            var bookingData =
                BookingData(
                    from = LocalDate.parse("2024-02-01"),
                    to = LocalDate.parse("2024-02-05"),
                    channelId = "test-channel-id",
                    channel = BookingChannel.Direct,
                    status = BookingStatus.Confirmed,
                )

            var response =
                client.post("/v1/properties/${TEST_ACCOUNT_1_PROPERTY_1.id}/bookings") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                    contentType(ContentType.Application.Json)
                    setBody(bookingData)
                }

            assertEquals(HttpStatusCode.Companion.OK, response.status)

            bookingData =
                BookingData(
                    from = LocalDate.parse("2024-02-05"),
                    to = LocalDate.parse("2024-02-09"),
                    channelId = "test-channel-id",
                    channel = BookingChannel.Direct,
                    status = BookingStatus.Confirmed,
                )

            response =
                client.post("/v1/properties/${TEST_ACCOUNT_1_PROPERTY_1.id}/bookings") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                    contentType(ContentType.Application.Json)
                    setBody(bookingData)
                }

            assertEquals(HttpStatusCode.Companion.OK, response.status)
        }

    @Test
    fun `Creating 1 booking the same end as other starts should be allowed`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            var bookingData =
                BookingData(
                    from = LocalDate.parse("2024-02-01"),
                    to = LocalDate.parse("2024-02-05"),
                    channelId = "test-channel-id",
                    channel = BookingChannel.Direct,
                    status = BookingStatus.Confirmed,
                )

            var response =
                client.post("/v1/properties/${TEST_ACCOUNT_1_PROPERTY_1.id}/bookings") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                    contentType(ContentType.Application.Json)
                    setBody(bookingData)
                }

            assertEquals(HttpStatusCode.Companion.OK, response.status)

            bookingData =
                BookingData(
                    from = LocalDate.parse("2024-01-05"),
                    to = LocalDate.parse("2024-02-01"),
                    channelId = "test-channel-id",
                    channel = BookingChannel.Direct,
                    status = BookingStatus.Confirmed,
                )

            response =
                client.post("/v1/properties/${TEST_ACCOUNT_1_PROPERTY_1.id}/bookings") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                    contentType(ContentType.Application.Json)
                    setBody(bookingData)
                }

            assertEquals(HttpStatusCode.Companion.OK, response.status)
        }

    // ============================================================
    // VALIDATION TESTS - Date Validation
    // ============================================================

    @Test
    fun `Creating booking with check-out before check-in should fail`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val bookingData =
                BookingData(
                    from = LocalDate.now().plusDays(5),
                    to = LocalDate.now().plusDays(2),
                    channelId = "test-channel-id",
                )

            val response =
                client.post("/v1/properties/${TEST_ACCOUNT_1_PROPERTY_1.id}/bookings") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                    contentType(ContentType.Application.Json)
                    setBody(bookingData)
                }

            assertEquals(HttpStatusCode.Companion.BadRequest, response.status)
            val responseText = response.bodyAsText()
            assertContains(responseText, "Check-out date must be after check-in date")
        }

    @Test
    fun `Creating booking with same check-in and check-out dates should fail`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val sameDate = LocalDate.now().plusDays(5)
            val bookingData =
                BookingData(
                    from = sameDate,
                    to = sameDate,
                    channelId = "test-channel-id",
                )

            val response =
                client.post("/v1/properties/${TEST_ACCOUNT_1_PROPERTY_1.id}/bookings") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                    contentType(ContentType.Application.Json)
                    setBody(bookingData)
                }

            assertEquals(HttpStatusCode.Companion.BadRequest, response.status)
            val responseText = response.bodyAsText()
            assertContains(responseText, "Check-out date must be after check-in date")
        }

    @Test
    fun `Creating booking with duration exceeding max should fail`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val bookingData =
                BookingData(
                    from = LocalDate.now().plusDays(1),
                    // 399 days > 365 max
                    to = LocalDate.now().plusDays(400),
                    channelId = "test-channel-id",
                )

            val response =
                client.post("/v1/properties/${TEST_ACCOUNT_1_PROPERTY_1.id}/bookings") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                    contentType(ContentType.Application.Json)
                    setBody(bookingData)
                }

            assertEquals(HttpStatusCode.Companion.BadRequest, response.status)
            val responseText = response.bodyAsText()
            assertContains(responseText, "Booking duration cannot exceed 365 days")
        }

    @Test
    fun `Creating booking with only check-in date should fail`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val bookingData =
                BookingData(
                    from = LocalDate.now().plusDays(5),
                    to = null,
                    channelId = "test-channel-id",
                    channel = BookingChannel.Airbnb,
                    status = BookingStatus.Confirmed,
                )

            val response =
                client.post("/v1/properties/${TEST_ACCOUNT_1_PROPERTY_1.id}/bookings") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                    contentType(ContentType.Application.Json)
                    setBody(bookingData)
                }

            assertEquals(HttpStatusCode.Companion.BadRequest, response.status)
            val responseText = response.bodyAsText()
            assertContains(responseText, "Check-out date (to) is required")
        }

    @Test
    fun `Creating booking with only check-out date should fail`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val bookingData =
                BookingData(
                    from = null,
                    to = LocalDate.now().plusDays(5),
                    channelId = "test-channel-id",
                )

            val response =
                client.post("/v1/properties/${TEST_ACCOUNT_1_PROPERTY_1.id}/bookings") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                    contentType(ContentType.Application.Json)
                    setBody(bookingData)
                }

            val responseText = response.bodyAsText()
            assertEquals(HttpStatusCode.Companion.BadRequest, response.status)
            assertContains(responseText, "Check-in date (from) is required")
        }

    // ============================================================
    // VALIDATION TESTS - Email Validation
    // ============================================================

    @Test
    fun `Creating booking with invalid email format should fail`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val bookingData =
                BookingData(
                    from = LocalDate.now().plusDays(1),
                    to = LocalDate.now().plusDays(5),
                    guestEmail = "invalid-email-format",
                    channelId = "test-channel-id",
                )

            val response =
                client.post("/v1/properties/${TEST_ACCOUNT_1_PROPERTY_1.id}/bookings") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                    contentType(ContentType.Application.Json)
                    setBody(bookingData)
                }

            assertEquals(HttpStatusCode.Companion.BadRequest, response.status)
            val responseText = response.bodyAsText()
            assertContains(responseText, "Guest email format is invalid")
        }

    @Test
    fun `Creating booking with valid email should succeed`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val bookingData =
                BookingData(
                    from = LocalDate.now().plusDays(1),
                    to = LocalDate.now().plusDays(5),
                    guestEmail = "valid.email+tag@example.com",
                    channelId = "test-channel-id",
                    channel = BookingChannel.Airbnb,
                    status = BookingStatus.Confirmed,
                )

            val response =
                client.post("/v1/properties/${TEST_ACCOUNT_1_PROPERTY_1.id}/bookings") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                    contentType(ContentType.Application.Json)
                    setBody(bookingData)
                }
            val responseText = response.bodyAsText()
            assertEquals(HttpStatusCode.Companion.OK, response.status)
            assertNotNull(responseText)
        }

    // ============================================================
    // VALIDATION TESTS - Guest Count Validation
    // ============================================================

    @Test
    fun `Creating booking with negative adults count should fail`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val bookingData =
                BookingData(
                    from = LocalDate.now().plusDays(1),
                    to = LocalDate.now().plusDays(5),
                    adultsCount = -1,
                    channelId = "test-channel-id",
                )

            val response =
                client.post("/v1/properties/${TEST_ACCOUNT_1_PROPERTY_1.id}/bookings") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                    contentType(ContentType.Application.Json)
                    setBody(bookingData)
                }

            assertEquals(HttpStatusCode.Companion.BadRequest, response.status)
            val responseText = response.bodyAsText()
            assertContains(responseText, "Adults count cannot be negative")
        }

    @Test
    fun `Creating booking with negative children count should fail`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val bookingData =
                BookingData(
                    from = LocalDate.now().plusDays(1),
                    to = LocalDate.now().plusDays(5),
                    childrenCount = -5,
                    channelId = "test-channel-id",
                )

            val response =
                client.post("/v1/properties/${TEST_ACCOUNT_1_PROPERTY_1.id}/bookings") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                    contentType(ContentType.Application.Json)
                    setBody(bookingData)
                }

            assertEquals(HttpStatusCode.Companion.BadRequest, response.status)
            val responseText = response.bodyAsText()
            assertContains(responseText, "Children count cannot be negative")
        }

    @Test
    fun `Creating booking with negative infants count should fail`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val bookingData =
                BookingData(
                    from = LocalDate.now().plusDays(1),
                    to = LocalDate.now().plusDays(5),
                    infantsCount = -2,
                    channelId = "test-channel-id",
                )

            val response =
                client.post("/v1/properties/${TEST_ACCOUNT_1_PROPERTY_1.id}/bookings") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                    contentType(ContentType.Application.Json)
                    setBody(bookingData)
                }

            assertEquals(HttpStatusCode.Companion.BadRequest, response.status)
            val responseText = response.bodyAsText()
            assertContains(responseText, "Infants count cannot be negative")
        }

    @Test
    fun `Creating booking with adults exceeding max should fail`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val bookingData =
                BookingData(
                    from = LocalDate.now().plusDays(1),
                    to = LocalDate.now().plusDays(5),
                    // Max is 50
                    adultsCount = 51,
                    channelId = "test-channel-id",
                )

            val response =
                client.post("/v1/properties/${TEST_ACCOUNT_1_PROPERTY_1.id}/bookings") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                    contentType(ContentType.Application.Json)
                    setBody(bookingData)
                }

            assertEquals(HttpStatusCode.Companion.BadRequest, response.status)
            val responseText = response.bodyAsText()
            assertContains(responseText, "Adults count cannot exceed 50")
        }

    @Test
    fun `Creating booking with total guests exceeding max should fail`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val bookingData =
                BookingData(
                    from = LocalDate.now().plusDays(1),
                    to = LocalDate.now().plusDays(5),
                    adultsCount = 30,
                    childrenCount = 15,
                    infantsCount = 10, // Total = 55 > 50 max
                    channelId = "test-channel-id",
                )

            val response =
                client.post("/v1/properties/${TEST_ACCOUNT_1_PROPERTY_1.id}/bookings") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                    contentType(ContentType.Application.Json)
                    setBody(bookingData)
                }

            assertEquals(HttpStatusCode.Companion.BadRequest, response.status)
            val responseText = response.bodyAsText()
            assertContains(responseText, "Total guest count cannot exceed 50")
        }

    @Test
    fun `Creating booking with exactly max guests should succeed`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val bookingData =
                BookingData(
                    from = LocalDate.now().plusDays(1),
                    to = LocalDate.now().plusDays(5),
                    adultsCount = 25,
                    childrenCount = 15,
                    infantsCount = 10, // Total = 50 (exactly max)
                    channelId = "test-channel-id",
                    channel = BookingChannel.Airbnb,
                    status = BookingStatus.Confirmed,
                )

            val response =
                client.post("/v1/properties/${TEST_ACCOUNT_1_PROPERTY_1.id}/bookings") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                    contentType(ContentType.Application.Json)
                    setBody(bookingData)
                }

            assertEquals(HttpStatusCode.Companion.OK, response.status)
        }

    // ============================================================
    // VALIDATION TESTS - String Length Validation
    // ============================================================

    @Test
    fun `Creating booking with guest first name exceeding max length should fail`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val bookingData =
                BookingData(
                    from = LocalDate.now().plusDays(1),
                    to = LocalDate.now().plusDays(5),
                    guestFirstName = "a".repeat(101), // Max is 100
                    channelId = "test-channel-id",
                    channel = BookingChannel.Airbnb,
                    status = BookingStatus.Confirmed,
                )

            val response =
                client.post("/v1/properties/${TEST_ACCOUNT_1_PROPERTY_1.id}/bookings") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                    contentType(ContentType.Application.Json)
                    setBody(bookingData)
                }

            assertEquals(HttpStatusCode.Companion.BadRequest, response.status)
            val responseText = response.bodyAsText()
            assertContains(responseText, "Guest first name must not exceed 100 characters")
        }

    @Test
    fun `Creating booking with guest last name exceeding max length should fail`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val bookingData =
                BookingData(
                    from = LocalDate.now().plusDays(1),
                    to = LocalDate.now().plusDays(5),
                    guestLastName = "b".repeat(101), // Max is 100
                    channelId = "test-channel-id",
                    channel = BookingChannel.Airbnb,
                    status = BookingStatus.Confirmed,
                )

            val response =
                client.post("/v1/properties/${TEST_ACCOUNT_1_PROPERTY_1.id}/bookings") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                    contentType(ContentType.Application.Json)
                    setBody(bookingData)
                }

            assertEquals(HttpStatusCode.Companion.BadRequest, response.status)
            val responseText = response.bodyAsText()
            assertContains(responseText, "Guest last name must not exceed 100 characters")
        }

    @Test
    fun `Creating booking with guest phone exceeding max length should fail`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val bookingData =
                BookingData(
                    from = LocalDate.now().plusDays(1),
                    to = LocalDate.now().plusDays(5),
                    guestPhone = "1".repeat(21), // Max is 20
                    channelId = "test-channel-id",
                    channel = BookingChannel.Airbnb,
                    status = BookingStatus.Confirmed,
                )

            val response =
                client.post("/v1/properties/${TEST_ACCOUNT_1_PROPERTY_1.id}/bookings") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                    contentType(ContentType.Application.Json)
                    setBody(bookingData)
                }

            assertEquals(HttpStatusCode.Companion.BadRequest, response.status)
            val responseText = response.bodyAsText()
            assertContains(responseText, "Guest phone must not exceed 20 characters")
        }

    @Test
    fun `Creating booking with notes exceeding max length should fail`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val bookingData =
                BookingData(
                    from = LocalDate.now().plusDays(1),
                    to = LocalDate.now().plusDays(5),
                    notes = "x".repeat(1001), // Max is 1000
                    channelId = "test-channel-id",
                    channel = BookingChannel.Airbnb,
                    status = BookingStatus.Confirmed,
                )

            val response =
                client.post("/v1/properties/${TEST_ACCOUNT_1_PROPERTY_1.id}/bookings") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                    contentType(ContentType.Application.Json)
                    setBody(bookingData)
                }

            assertEquals(HttpStatusCode.Companion.BadRequest, response.status)
            val responseText = response.bodyAsText()
            assertContains(responseText, "Notes must not exceed 1000 characters")
        }

    @Test
    fun `Creating booking with special requests exceeding max length should fail`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val bookingData =
                BookingData(
                    from = LocalDate.now().plusDays(1),
                    to = LocalDate.now().plusDays(5),
                    specialRequests = "y".repeat(1001), // Max is 1000
                    channelId = "test-channel-id",
                )

            val response =
                client.post("/v1/properties/${TEST_ACCOUNT_1_PROPERTY_1.id}/bookings") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                    contentType(ContentType.Application.Json)
                    setBody(bookingData)
                }

            assertEquals(HttpStatusCode.Companion.BadRequest, response.status)
            val responseText = response.bodyAsText()
            assertContains(responseText, "Special requests must not exceed 1000 characters")
        }

    @Test
    fun `Creating booking with channel ID exceeding max length should fail`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val bookingData =
                BookingData(
                    from = LocalDate.now().plusDays(1),
                    to = LocalDate.now().plusDays(5),
                    channelId = "z".repeat(256), // Max is 255,
                    channel = BookingChannel.Airbnb,
                    status = BookingStatus.Confirmed,
                )

            val response =
                client.post("/v1/properties/${TEST_ACCOUNT_1_PROPERTY_1.id}/bookings") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                    contentType(ContentType.Application.Json)
                    setBody(bookingData)
                }

            assertEquals(HttpStatusCode.Companion.BadRequest, response.status)
            val responseText = response.bodyAsText()
            assertContains(responseText, "Channel ID must not exceed 255 characters")
        }

    // ============================================================
    // VALIDATION TESTS - Multiple Errors
    // ============================================================

    @Test
    fun `Creating booking with multiple validation errors should return all errors`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val bookingData =
                BookingData(
                    from = LocalDate.now().plusDays(5),
                    to = LocalDate.now().plusDays(2), // Check-out before check-in
                    guestEmail = "invalid-email", // Invalid email
                    adultsCount = -5, // Negative count
                    notes = "x".repeat(1001), // Too long
                    channel = BookingChannel.Airbnb,
                    status = BookingStatus.Confirmed,
                )

            val response =
                client.post("/v1/properties/${TEST_ACCOUNT_1_PROPERTY_1.id}/bookings") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                    contentType(ContentType.Application.Json)
                    setBody(bookingData)
                }

            assertEquals(HttpStatusCode.Companion.BadRequest, response.status)
            val responseText = response.bodyAsText()

            // Should contain all validation errors
            assertContains(responseText, "Check-out date must be after check-in date")
            assertContains(responseText, "Guest email format is invalid")
            assertContains(responseText, "Adults count cannot be negative")
            assertContains(responseText, "Notes must not exceed 1000 characters")
        }

    // ============================================================
    // VALIDATION TESTS - Channel Validation
    // ============================================================

    @Test
    fun `Creating booking without channel should default to Direct and generate channelId`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val bookingData =
                BookingData(
                    from = LocalDate.now().plusDays(10),
                    to = LocalDate.now().plusDays(15),
                    status = BookingStatus.Confirmed,
                    guestFirstName = "John",
                )

            val response =
                client.post("/v1/properties/${TEST_ACCOUNT_1_PROPERTY_1.id}/bookings") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                    contentType(ContentType.Application.Json)
                    setBody(bookingData)
                }

            assertEquals(HttpStatusCode.Companion.OK, response.status)

            val responseText = response.bodyAsText()
            val responseJson = Json.parseToJsonElement(responseText).jsonObject

            // Verify channel is Direct
            assertEquals("Direct", responseJson["channel"]?.jsonPrimitive?.content)

            // Verify channelId was generated (10 alphanumeric characters)
            val channelId = responseJson["channel_id"]?.jsonPrimitive?.content
            assertNotNull(channelId)
            assertEquals(10, channelId.length)
            assert(channelId.all { it.isUpperCase() || it.isDigit() }) {
                "Channel ID should contain only uppercase letters and digits"
            }
        }

    @Test
    fun `Creating booking with Direct channel and no channelId should generate channelId`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val bookingData =
                BookingData(
                    from = LocalDate.now().plusDays(20),
                    to = LocalDate.now().plusDays(25),
                    channel = BookingChannel.Direct,
                    status = BookingStatus.Confirmed,
                    guestFirstName = "Jane",
                )

            val response =
                client.post("/v1/properties/${TEST_ACCOUNT_1_PROPERTY_1.id}/bookings") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                    contentType(ContentType.Application.Json)
                    setBody(bookingData)
                }

            assertEquals(HttpStatusCode.Companion.OK, response.status)

            val responseText = response.bodyAsText()
            val responseJson = Json.parseToJsonElement(responseText).jsonObject

            // Verify channelId was generated
            val channelId = responseJson["channel_id"]?.jsonPrimitive?.content
            assertNotNull(channelId)
            assertEquals(10, channelId.length)
            assert(channelId.all { it.isUpperCase() || it.isDigit() }) {
                "Channel ID should contain only uppercase letters and digits"
            }
        }

    @Test
    fun `Creating booking with non-Direct channel without channelId should fail`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val bookingData =
                BookingData(
                    from = LocalDate.now().plusDays(30),
                    to = LocalDate.now().plusDays(35),
                    channel = BookingChannel.Airbnb,
                    status = BookingStatus.Confirmed,
                    guestFirstName = "Bob",
                )

            val response =
                client.post("/v1/properties/${TEST_ACCOUNT_1_PROPERTY_1.id}/bookings") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                    contentType(ContentType.Application.Json)
                    setBody(bookingData)
                }

            assertEquals(HttpStatusCode.Companion.BadRequest, response.status)
            val responseText = response.bodyAsText()
            assertContains(responseText, "channelId is required")
        }

    @Test
    fun `Creating booking with non-Direct channel and channelId should succeed`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val bookingData =
                BookingData(
                    from = LocalDate.now().plusDays(40),
                    to = LocalDate.now().plusDays(45),
                    channel = BookingChannel.Booking,
                    channelId = "BOOKING123456",
                    status = BookingStatus.Confirmed,
                    guestFirstName = "Alice",
                )

            val response =
                client.post("/v1/properties/${TEST_ACCOUNT_1_PROPERTY_1.id}/bookings") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                    contentType(ContentType.Application.Json)
                    setBody(bookingData)
                }

            assertEquals(HttpStatusCode.Companion.OK, response.status)

            val responseText = response.bodyAsText()
            val responseJson = Json.parseToJsonElement(responseText).jsonObject

            assertEquals("Booking", responseJson["channel"]?.jsonPrimitive?.content)
            assertEquals("BOOKING123456", responseJson["channel_id"]?.jsonPrimitive?.content)
        }
}
