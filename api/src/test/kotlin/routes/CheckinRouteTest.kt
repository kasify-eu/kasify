package eu.kasify.routes
import eu.kasify.TEST_ACCOUNT_1_PROPERTY_1
import eu.kasify.db.model.DocumentType
import eu.kasify.db.model.Gender
import eu.kasify.extensions.configureTestModules
import eu.kasify.extensions.createClient
import eu.kasify.helpers.insertBooking
import eu.kasify.model.api.requests.GuestRegistrationRequest
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class CheckinRouteTest : BaseRouteDBTests() {
    private val testBookingId = Uuid.parse("40000000-0000-0000-0000-000000000010")
    private val bookingCode = "checkin-test-booking"
    private val otaChannelId = "OTA-CONF-9921"
    private val usaCountryId = eu.kasify.TEST_USA_COUNTRY_ID

    // Use future dates so the booking is not closed
    private val futureFrom = LocalDateTime.now().plusDays(10)
    private val futureTo = LocalDateTime.now().plusDays(15)

    override fun setupData() {
        // Spain is seeded by the base; add USA for non-Spanish residence cases.
        testDBConnection.createStatement().execute(
            "INSERT INTO countries (id, name, name_es, alpha_2, alpha_3) VALUES " +
                "('$usaCountryId', 'United States', 'Estados Unidos', 'US', 'USA')",
        )

        testDBConnection.insertBooking(
            bookingId = testBookingId,
            propertyId = TEST_ACCOUNT_1_PROPERTY_1.id,
            from = futureFrom,
            to = futureTo,
            channelId = otaChannelId,
            checkinCode = bookingCode,
            adultsCount = 2,
            childrenCount = 0,
            infantsCount = 0,
        )
    }

    private fun adultGuestRequest(
        firstName: String = "Juan",
        lastName: String = "Garcia",
        secondLastName: String? = "Lopez",
        documentNumber: String = "12345678Z",
        documentType: DocumentType = DocumentType.DNI,
        documentSupportNumber: String = "ABC123456",
    ) = GuestRegistrationRequest(
        firstName = firstName,
        lastName = lastName,
        secondLastName = secondLastName,
        nationalityCountryCode = "ESP",
        dateOfBirth = LocalDate.of(1990, 1, 1),
        sex = Gender.Male,
        documentType = documentType,
        documentNumber = documentNumber,
        documentSupportNumber = documentSupportNumber,
        residenceAddress = "Calle Test 1",
        // Use non-Spanish residence to avoid needing to seed provinces/cities for the common-path tests
        residenceCountryCode = "USA",
        residenceCity = "New York",
        residencePostalCode = "10001",
        phone = "612345678",
        phonePrefix = "+34",
        email = "juan@test.com",
    )

    @Test
    fun `GET checkin returns booking snapshot with flags`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val response = client.get("/v1/checkin/$bookingCode")

            assertEquals(HttpStatusCode.OK, response.status)
            val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            assertEquals(bookingCode, json["booking_code"]?.jsonPrimitive?.content)
            assertEquals(2, json["guests_number"]?.jsonPrimitive?.content?.toInt())
            assertEquals(0, json["guests"]?.jsonArray?.size)
            assertFalse(json["checkin_completed"]!!.jsonPrimitive.boolean)
            assertFalse(json["checkin_closed"]!!.jsonPrimitive.boolean)
            assertFalse(json["has_minors"]!!.jsonPrimitive.boolean)
        }

    @Test
    fun `POST registers guest successfully`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val response =
                client.post("/v1/checkin/$bookingCode/guests") {
                    contentType(ContentType.Application.Json)
                    setBody(adultGuestRequest())
                }

            assertEquals(HttpStatusCode.Created, response.status)
            val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            val guests = json["guests"]?.jsonArray
            assertNotNull(guests)
            assertEquals(1, guests.size)
            assertEquals("Juan", guests[0].jsonObject["first_name"]?.jsonPrimitive?.content)
            assertFalse(json["checkin_completed"]!!.jsonPrimitive.boolean)
            assertEquals(setOf("id", "first_name", "last_name"), guests[0].jsonObject.keys)
        }

    @Test
    fun `checkin snapshot exposes only id and name for registered guests`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            client.post("/v1/checkin/$bookingCode/guests") {
                contentType(ContentType.Application.Json)
                setBody(adultGuestRequest())
            }

            val response = client.get("/v1/checkin/$bookingCode")

            assertEquals(HttpStatusCode.OK, response.status)
            val guests = Json.parseToJsonElement(response.bodyAsText()).jsonObject["guests"]!!.jsonArray
            assertEquals(1, guests.size)
            val guest = guests[0].jsonObject
            assertEquals("Juan", guest["first_name"]?.jsonPrimitive?.content)
            assertEquals("Garcia", guest["last_name"]?.jsonPrimitive?.content)
            // The public snapshot must never serialize PII (document numbers, DOB,
            // address, contact data). Pinning the exact key set guards against
            // accidental re-introduction when fields are added to the guest model.
            assertEquals(setOf("id", "first_name", "last_name"), guest.keys)
        }

    @Test
    fun `POST rejects duplicate document number within same booking`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            // Register first guest
            val first =
                client.post("/v1/checkin/$bookingCode/guests") {
                    contentType(ContentType.Application.Json)
                    setBody(adultGuestRequest(firstName = "Juan", documentNumber = "12345678Z"))
                }
            assertEquals(HttpStatusCode.Created, first.status)

            // Try to register second guest with same document number
            val second =
                client.post("/v1/checkin/$bookingCode/guests") {
                    contentType(ContentType.Application.Json)
                    setBody(
                        adultGuestRequest(
                            firstName = "Maria",
                            lastName = "Lopez",
                            documentNumber = "12345678Z",
                        ),
                    )
                }
            assertEquals(HttpStatusCode.BadRequest, second.status)
            val json = Json.parseToJsonElement(second.bodyAsText()).jsonObject
            assertEquals("checkin.duplicate_document", json["code"]?.jsonPrimitive?.content)
        }

    @Test
    fun `POST marks checkin completed when all guests registered`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            // Register first guest (2 expected total)
            val firstResponse =
                client.post("/v1/checkin/$bookingCode/guests") {
                    contentType(ContentType.Application.Json)
                    setBody(adultGuestRequest(firstName = "Juan", documentNumber = "12345678Z"))
                }
            val firstJson = Json.parseToJsonElement(firstResponse.bodyAsText()).jsonObject
            assertFalse(firstJson["checkin_completed"]!!.jsonPrimitive.boolean)

            // No check-in-completed notification is enqueued before the booking is fully registered.
            assertEquals(0, checkinCompletedNotificationCount())

            // Register second guest
            val secondResponse =
                client.post("/v1/checkin/$bookingCode/guests") {
                    contentType(ContentType.Application.Json)
                    setBody(
                        adultGuestRequest(
                            firstName = "Maria",
                            lastName = "Lopez",
                            documentNumber = "87654321X",
                            documentSupportNumber = "DEF456789",
                        ),
                    )
                }
            val secondJson = Json.parseToJsonElement(secondResponse.bodyAsText()).jsonObject
            assertTrue(secondJson["checkin_completed"]!!.jsonPrimitive.boolean)

            // Verify checkin_completed_at is set in DB
            val rs =
                testDBConnection.createStatement().executeQuery(
                    "SELECT checkin_completed_at FROM bookings WHERE id = '$testBookingId'",
                )
            rs.next()
            assertNotNull(rs.getTimestamp("checkin_completed_at"))

            // Completing the check-in enqueues a notification for each property owner (account 1 has
            // two seeded owner users). Email is the only channel enabled in tests.
            assertEquals(2, checkinCompletedNotificationCount())
        }

    private fun checkinCompletedNotificationCount(): Int {
        val rs =
            testDBConnection.createStatement().executeQuery(
                "SELECT COUNT(*) AS c FROM notifications " +
                    "WHERE event_type = 'checkin_completed' AND channel = 'email'",
            )
        rs.next()
        return rs.getInt("c")
    }

    @Test
    fun `POST rejects when all guests already registered`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            // Register both expected guests
            client.post("/v1/checkin/$bookingCode/guests") {
                contentType(ContentType.Application.Json)
                setBody(adultGuestRequest(firstName = "Juan", documentNumber = "12345678Z"))
            }
            client.post("/v1/checkin/$bookingCode/guests") {
                contentType(ContentType.Application.Json)
                setBody(
                    adultGuestRequest(
                        firstName = "Maria",
                        lastName = "Lopez",
                        documentNumber = "87654321X",
                        documentSupportNumber = "DEF456789",
                    ),
                )
            }

            // Try to register a third guest
            val response =
                client.post("/v1/checkin/$bookingCode/guests") {
                    contentType(ContentType.Application.Json)
                    setBody(
                        adultGuestRequest(
                            firstName = "Pedro",
                            lastName = "Martinez",
                            documentNumber = "11111111H",
                            documentSupportNumber = "GHI789012",
                        ),
                    )
                }
            assertEquals(HttpStatusCode.BadRequest, response.status)
            val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            assertEquals("checkin.booking_full", json["code"]?.jsonPrimitive?.content)
        }

    @Test
    fun `POST rejects registration when guest count is zero`() =
        testApplication {
            val unknownBookingId = Uuid.parse("40000000-0000-0000-0000-000000000011")
            val unknownCode = "checkin-unknown-count"
            testDBConnection.insertBooking(
                bookingId = unknownBookingId,
                propertyId = TEST_ACCOUNT_1_PROPERTY_1.id,
                from = futureFrom,
                to = futureTo,
                checkinCode = unknownCode,
                adultsCount = 0,
                childrenCount = 0,
                infantsCount = 0,
            )

            application { configureTestModules() }
            val client = createClient()

            val response =
                client.post("/v1/checkin/$unknownCode/guests") {
                    contentType(ContentType.Application.Json)
                    setBody(adultGuestRequest())
                }
            assertEquals(HttpStatusCode.BadRequest, response.status)
            val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            assertEquals("checkin.closed", json["code"]?.jsonPrimitive?.content)
        }

    @Test
    fun `POST rejects DNI or NIE guest when support number is not 9 characters`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val dniResponse =
                client.post("/v1/checkin/$bookingCode/guests") {
                    contentType(ContentType.Application.Json)
                    setBody(adultGuestRequest(documentSupportNumber = "ABC123"))
                }
            assertEquals(HttpStatusCode.BadRequest, dniResponse.status)

            val nieResponse =
                client.post("/v1/checkin/$bookingCode/guests") {
                    contentType(ContentType.Application.Json)
                    setBody(
                        adultGuestRequest(
                            documentType = DocumentType.NIE,
                            documentNumber = "X1234567L",
                            documentSupportNumber = "E123",
                        ),
                    )
                }
            assertEquals(HttpStatusCode.BadRequest, nieResponse.status)
        }

    @Test
    fun `GET returns checkin_closed true when guest count is zero`() =
        testApplication {
            val zeroGuestsId = Uuid.parse("40000000-0000-0000-0000-000000000015")
            val zeroGuestsCode = "checkin-zero-guests"
            testDBConnection.insertBooking(
                bookingId = zeroGuestsId,
                propertyId = TEST_ACCOUNT_1_PROPERTY_1.id,
                from = futureFrom,
                to = futureTo,
                checkinCode = zeroGuestsCode,
                adultsCount = 0,
                childrenCount = 0,
                infantsCount = 0,
            )

            application { configureTestModules() }
            val client = createClient()

            val response = client.get("/v1/checkin/$zeroGuestsCode")
            assertEquals(HttpStatusCode.OK, response.status)
            val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            assertTrue(json["checkin_closed"]!!.jsonPrimitive.boolean)
        }

    @Test
    fun `POST rejects registration for past booking`() =
        testApplication {
            val pastBookingId = Uuid.parse("40000000-0000-0000-0000-000000000012")
            val pastCode = "checkin-past-booking"
            testDBConnection.insertBooking(
                bookingId = pastBookingId,
                propertyId = TEST_ACCOUNT_1_PROPERTY_1.id,
                from = LocalDateTime.now().minusDays(10),
                to = LocalDateTime.now().minusDays(5),
                checkinCode = pastCode,
                adultsCount = 2,
            )

            application { configureTestModules() }
            val client = createClient()

            val response =
                client.post("/v1/checkin/$pastCode/guests") {
                    contentType(ContentType.Application.Json)
                    setBody(adultGuestRequest())
                }
            assertEquals(HttpStatusCode.BadRequest, response.status)
            val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            assertEquals("checkin.closed", json["code"]?.jsonPrimitive?.content)
        }

    @Test
    fun `GET returns checkin_closed true for past booking`() =
        testApplication {
            val pastBookingId = Uuid.parse("40000000-0000-0000-0000-000000000013")
            val pastCode = "checkin-past-get"
            testDBConnection.insertBooking(
                bookingId = pastBookingId,
                propertyId = TEST_ACCOUNT_1_PROPERTY_1.id,
                from = LocalDateTime.now().minusDays(10),
                to = LocalDateTime.now().minusDays(5),
                checkinCode = pastCode,
                adultsCount = 2,
            )

            application { configureTestModules() }
            val client = createClient()

            val response = client.get("/v1/checkin/$pastCode")
            assertEquals(HttpStatusCode.OK, response.status)
            val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            assertTrue(json["checkin_closed"]!!.jsonPrimitive.boolean)
            assertFalse(json["checkin_completed"]!!.jsonPrimitive.boolean)
            assertEquals(0, json["guests"]?.jsonArray?.size, "Closed bookings must not expose guest data")
        }

    @Test
    fun `POST rejects registration for cancelled booking`() =
        testApplication {
            val cancelledBookingId = Uuid.parse("40000000-0000-0000-0000-000000000014")
            val cancelledCode = "checkin-cancelled-booking"
            testDBConnection.insertBooking(
                bookingId = cancelledBookingId,
                propertyId = TEST_ACCOUNT_1_PROPERTY_1.id,
                from = futureFrom,
                to = futureTo,
                checkinCode = cancelledCode,
                adultsCount = 2,
            )
            // Mark the booking as cancelled
            testDBConnection.createStatement().execute(
                "UPDATE bookings SET status = 'cancelled', cancelled_at = now() WHERE id = '$cancelledBookingId'",
            )

            application { configureTestModules() }
            val client = createClient()

            val response =
                client.post("/v1/checkin/$cancelledCode/guests") {
                    contentType(ContentType.Application.Json)
                    setBody(adultGuestRequest())
                }
            assertEquals(HttpStatusCode.BadRequest, response.status)
            val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            assertEquals("checkin.closed", json["code"]?.jsonPrimitive?.content)
        }

    @Test
    fun `GET exposes has_minors true when booking has children`() =
        testApplication {
            val withMinorsId = Uuid.parse("40000000-0000-0000-0000-000000000016")
            val withMinorsCode = "checkin-has-minors"
            testDBConnection.insertBooking(
                bookingId = withMinorsId,
                propertyId = TEST_ACCOUNT_1_PROPERTY_1.id,
                from = futureFrom,
                to = futureTo,
                checkinCode = withMinorsCode,
                adultsCount = 2,
                childrenCount = 1,
                infantsCount = 0,
            )

            application { configureTestModules() }
            val client = createClient()

            val response = client.get("/v1/checkin/$withMinorsCode")
            assertEquals(HttpStatusCode.OK, response.status)
            val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            assertTrue(json["has_minors"]!!.jsonPrimitive.boolean)
        }

    @Test
    fun `POST accepts adult guest without relationship_code when booking has minors`() =
        testApplication {
            val withMinorsId = Uuid.parse("40000000-0000-0000-0000-000000000017")
            val withMinorsCode = "checkin-minors-adult-no-rel"
            testDBConnection.insertBooking(
                bookingId = withMinorsId,
                propertyId = TEST_ACCOUNT_1_PROPERTY_1.id,
                from = futureFrom,
                to = futureTo,
                checkinCode = withMinorsCode,
                adultsCount = 1,
                childrenCount = 1,
                infantsCount = 0,
            )

            application { configureTestModules() }
            val client = createClient()

            val response =
                client.post("/v1/checkin/$withMinorsCode/guests") {
                    contentType(ContentType.Application.Json)
                    setBody(adultGuestRequest())
                }
            assertEquals(HttpStatusCode.Created, response.status)
        }

    @Test
    fun `POST accepts adult guest with relationship_code when booking has minors`() =
        testApplication {
            val withMinorsId = Uuid.parse("40000000-0000-0000-0000-000000000018")
            val withMinorsCode = "checkin-minors-with-rel"
            testDBConnection.insertBooking(
                bookingId = withMinorsId,
                propertyId = TEST_ACCOUNT_1_PROPERTY_1.id,
                from = futureFrom,
                to = futureTo,
                checkinCode = withMinorsCode,
                adultsCount = 1,
                childrenCount = 1,
                infantsCount = 0,
            )

            application { configureTestModules() }
            val client = createClient()

            val response =
                client.post("/v1/checkin/$withMinorsCode/guests") {
                    contentType(ContentType.Application.Json)
                    setBody(adultGuestRequest().copy(relationshipCode = "PM"))
                }
            assertEquals(HttpStatusCode.Created, response.status)
        }

    @Test
    fun `GET exposes has_minors true when a registered guest is a minor even though booking reports no children`() =
        testApplication {
            val adultsOnlyId = Uuid.parse("40000000-0000-0000-0000-000000000020")
            val adultsOnlyCode = "checkin-registered-minor"
            testDBConnection.insertBooking(
                bookingId = adultsOnlyId,
                propertyId = TEST_ACCOUNT_1_PROPERTY_1.id,
                from = futureFrom,
                to = futureTo,
                checkinCode = adultsOnlyCode,
                adultsCount = 2,
                childrenCount = 0,
                infantsCount = 0,
            )
            val minorDob = LocalDate.now().minusYears(8)
            testDBConnection.createStatement().execute(
                "INSERT INTO guests (booking_id, property_id, first_name, last_name, nationality_country_id, date_of_birth) " +
                    "VALUES ('$adultsOnlyId', '${TEST_ACCOUNT_1_PROPERTY_1.id}', 'Lucia', 'Garcia', " +
                    "'${eu.kasify.TEST_SPAIN_COUNTRY_ID}', '$minorDob')",
            )

            application { configureTestModules() }
            val client = createClient()

            val response = client.get("/v1/checkin/$adultsOnlyCode")
            assertEquals(HttpStatusCode.OK, response.status)
            val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            assertTrue(json["has_minors"]!!.jsonPrimitive.boolean)
        }

    @Test
    fun `GET keeps has_minors false when all registered guests are adults`() =
        testApplication {
            val adultsOnlyId = Uuid.parse("40000000-0000-0000-0000-000000000021")
            val adultsOnlyCode = "checkin-registered-adult-only"
            testDBConnection.insertBooking(
                bookingId = adultsOnlyId,
                propertyId = TEST_ACCOUNT_1_PROPERTY_1.id,
                from = futureFrom,
                to = futureTo,
                checkinCode = adultsOnlyCode,
                adultsCount = 2,
                childrenCount = 0,
                infantsCount = 0,
            )
            val adultDob = LocalDate.now().minusYears(30)
            testDBConnection.createStatement().execute(
                "INSERT INTO guests (booking_id, property_id, first_name, last_name, nationality_country_id, date_of_birth) " +
                    "VALUES ('$adultsOnlyId', '${TEST_ACCOUNT_1_PROPERTY_1.id}', 'Adulto', 'Garcia', " +
                    "'${eu.kasify.TEST_SPAIN_COUNTRY_ID}', '$adultDob')",
            )

            application { configureTestModules() }
            val client = createClient()

            val response = client.get("/v1/checkin/$adultsOnlyCode")
            assertEquals(HttpStatusCode.OK, response.status)
            val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            assertFalse(json["has_minors"]!!.jsonPrimitive.boolean)
        }

    @Test
    fun `POST flips has_minors to true when a minor guest is registered against an adults-only booking`() =
        testApplication {
            val adultsOnlyId = Uuid.parse("40000000-0000-0000-0000-000000000022")
            val adultsOnlyCode = "checkin-minor-post-flips"
            testDBConnection.insertBooking(
                bookingId = adultsOnlyId,
                propertyId = TEST_ACCOUNT_1_PROPERTY_1.id,
                from = futureFrom,
                to = futureTo,
                checkinCode = adultsOnlyCode,
                adultsCount = 2,
                childrenCount = 0,
                infantsCount = 0,
            )

            application { configureTestModules() }
            val client = createClient()

            val minorRequest =
                GuestRegistrationRequest(
                    firstName = "Pequeno",
                    lastName = "Garcia",
                    secondLastName = "Lopez",
                    nationalityCountryCode = "ESP",
                    dateOfBirth = LocalDate.now().minusYears(5),
                    sex = Gender.Male,
                    residenceAddress = "Calle Test 1",
                    residenceCountryCode = "USA",
                    residenceCity = "New York",
                    residencePostalCode = "10001",
                    email = "minor@test.com",
                    relationshipCode = "HJ",
                )

            val response =
                client.post("/v1/checkin/$adultsOnlyCode/guests") {
                    contentType(ContentType.Application.Json)
                    setBody(minorRequest)
                }
            assertEquals(HttpStatusCode.Created, response.status)
            val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            assertTrue(json["has_minors"]!!.jsonPrimitive.boolean)
        }

    @Test
    fun `POST rejects minor guest without relationship_code`() =
        testApplication {
            val withMinorsId = Uuid.parse("40000000-0000-0000-0000-000000000019")
            val withMinorsCode = "checkin-minors-minor-rejects"
            testDBConnection.insertBooking(
                bookingId = withMinorsId,
                propertyId = TEST_ACCOUNT_1_PROPERTY_1.id,
                from = futureFrom,
                to = futureTo,
                checkinCode = withMinorsCode,
                adultsCount = 0,
                childrenCount = 1,
                infantsCount = 0,
            )

            application { configureTestModules() }
            val client = createClient()

            val minorRequest =
                GuestRegistrationRequest(
                    firstName = "Pequeno",
                    lastName = "Garcia",
                    secondLastName = "Lopez",
                    nationalityCountryCode = "ESP",
                    dateOfBirth = LocalDate.now().minusYears(5),
                    sex = Gender.Male,
                    residenceAddress = "Calle Test 1",
                    residenceCountryCode = "USA",
                    residenceCity = "New York",
                    residencePostalCode = "10001",
                    email = "minor@test.com",
                )

            val response =
                client.post("/v1/checkin/$withMinorsCode/guests") {
                    contentType(ContentType.Application.Json)
                    setBody(minorRequest)
                }
            assertEquals(HttpStatusCode.BadRequest, response.status)
            val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            assertEquals("checkin.relationship_required", json["code"]?.jsonPrimitive?.content)
        }

    @Test
    fun `POST accepts minor guest with relationship_code`() =
        testApplication {
            val withMinorsId = Uuid.parse("40000000-0000-0000-0000-000000000023")
            val withMinorsCode = "checkin-minor-with-rel"
            testDBConnection.insertBooking(
                bookingId = withMinorsId,
                propertyId = TEST_ACCOUNT_1_PROPERTY_1.id,
                from = futureFrom,
                to = futureTo,
                checkinCode = withMinorsCode,
                adultsCount = 0,
                childrenCount = 1,
                infantsCount = 0,
            )

            application { configureTestModules() }
            val client = createClient()

            val minorRequest =
                GuestRegistrationRequest(
                    firstName = "Pequeno",
                    lastName = "Garcia",
                    secondLastName = "Lopez",
                    nationalityCountryCode = "ESP",
                    dateOfBirth = LocalDate.now().minusYears(5),
                    sex = Gender.Male,
                    residenceAddress = "Calle Test 1",
                    residenceCountryCode = "USA",
                    residenceCity = "New York",
                    residencePostalCode = "10001",
                    email = "minor@test.com",
                    relationshipCode = "HJ",
                )

            val response =
                client.post("/v1/checkin/$withMinorsCode/guests") {
                    contentType(ContentType.Application.Json)
                    setBody(minorRequest)
                }
            assertEquals(HttpStatusCode.Created, response.status)
        }

    @Test
    fun `POST rejects minor guest without phone or email`() =
        testApplication {
            val withMinorsId = Uuid.parse("40000000-0000-0000-0000-000000000024")
            val withMinorsCode = "checkin-minor-no-contact"
            testDBConnection.insertBooking(
                bookingId = withMinorsId,
                propertyId = TEST_ACCOUNT_1_PROPERTY_1.id,
                from = futureFrom,
                to = futureTo,
                checkinCode = withMinorsCode,
                adultsCount = 0,
                childrenCount = 1,
                infantsCount = 0,
            )

            application { configureTestModules() }
            val client = createClient()

            // A minor with a valid parentesco but no phone and no email: SES requires contact
            // details on every traveller, so registration must be rejected.
            val minorRequest =
                GuestRegistrationRequest(
                    firstName = "Pequeno",
                    lastName = "Garcia",
                    secondLastName = "Lopez",
                    nationalityCountryCode = "ESP",
                    dateOfBirth = LocalDate.now().minusYears(5),
                    sex = Gender.Male,
                    residenceAddress = "Calle Test 1",
                    residenceCountryCode = "USA",
                    residenceCity = "New York",
                    residencePostalCode = "10001",
                    relationshipCode = "HJ",
                )

            val response =
                client.post("/v1/checkin/$withMinorsCode/guests") {
                    contentType(ContentType.Application.Json)
                    setBody(minorRequest)
                }
            assertEquals(HttpStatusCode.BadRequest, response.status)
        }

    @Test
    fun `POST accepts minor guest with only a phone`() =
        testApplication {
            val withMinorsId = Uuid.parse("40000000-0000-0000-0000-000000000025")
            val withMinorsCode = "checkin-minor-phone-only"
            testDBConnection.insertBooking(
                bookingId = withMinorsId,
                propertyId = TEST_ACCOUNT_1_PROPERTY_1.id,
                from = futureFrom,
                to = futureTo,
                checkinCode = withMinorsCode,
                adultsCount = 0,
                childrenCount = 1,
                infantsCount = 0,
            )

            application { configureTestModules() }
            val client = createClient()

            // A single contact channel (here the responsible adult's phone) is enough for a minor.
            val minorRequest =
                GuestRegistrationRequest(
                    firstName = "Pequeno",
                    lastName = "Garcia",
                    secondLastName = "Lopez",
                    nationalityCountryCode = "ESP",
                    dateOfBirth = LocalDate.now().minusYears(5),
                    sex = Gender.Male,
                    residenceAddress = "Calle Test 1",
                    residenceCountryCode = "USA",
                    residenceCity = "New York",
                    residencePostalCode = "10001",
                    phone = "612345678",
                    phonePrefix = "+34",
                    relationshipCode = "HJ",
                )

            val response =
                client.post("/v1/checkin/$withMinorsCode/guests") {
                    contentType(ContentType.Application.Json)
                    setBody(minorRequest)
                }
            assertEquals(HttpStatusCode.Created, response.status)
        }

    @Test
    fun `checkin lookup by OTA channel id returns 404`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val response = client.get("/v1/checkin/$otaChannelId")

            assertEquals(HttpStatusCode.NotFound, response.status)
        }
}
