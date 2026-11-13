package eu.kasify.routes
import eu.kasify.TEST_ACCOUNT_1_PROPERTY_1
import eu.kasify.TEST_ACCOUNT_1_USER_1
import eu.kasify.TEST_ACCOUNT_2_USER_3
import eu.kasify.TEST_USA_COUNTRY_ID
import eu.kasify.db.model.DocumentType
import eu.kasify.db.model.Gender
import eu.kasify.extensions.configureTestModules
import eu.kasify.extensions.createClient
import eu.kasify.helpers.insertBooking
import eu.kasify.model.api.requests.GuestRegistrationRequest
import io.ktor.client.request.delete
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
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.uuid.Uuid

class BookingGuestsRouteTest : BaseRouteDBTests() {
    private val testBookingId = Uuid.parse("40000000-0000-0000-0000-000000000020")
    private val bookingCode = "owner-side-booking"
    private val futureFrom = LocalDateTime.now().plusDays(10)
    private val futureTo = LocalDateTime.now().plusDays(15)

    override fun setupData() {
        testDBConnection.createStatement().execute(
            "INSERT INTO countries (id, name, name_es, alpha_2, alpha_3) VALUES " +
                "('$TEST_USA_COUNTRY_ID', 'United States', 'Estados Unidos', 'US', 'USA')",
        )
        testDBConnection.insertBooking(
            bookingId = testBookingId,
            propertyId = TEST_ACCOUNT_1_PROPERTY_1.id,
            from = futureFrom,
            to = futureTo,
            channelId = bookingCode,
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
        residenceCountryCode = "USA",
        residenceCity = "New York",
        residencePostalCode = "10001",
        phone = "612345678",
        phonePrefix = "+34",
        email = "juan@test.com",
    )

    @Test
    fun `POST creates a guest as owner`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val response =
                client.post("/v1/bookings/$testBookingId/guests") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                    contentType(ContentType.Application.Json)
                    setBody(adultGuestRequest())
                }

            assertEquals(HttpStatusCode.Created, response.status)
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            assertEquals("Juan", body["first_name"]?.jsonPrimitive?.content)
            assertNotNull(body["id"]?.jsonPrimitive?.content)
        }

    @Test
    fun `POST rejects callers without property access`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val response =
                client.post("/v1/bookings/$testBookingId/guests") {
                    // User belongs to a different account with no property_access grant.
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_2_USER_3.token}")
                    contentType(ContentType.Application.Json)
                    setBody(adultGuestRequest())
                }

            assertEquals(HttpStatusCode.Forbidden, response.status)
        }

    @Test
    fun `POST returns booking_full when capacity reached`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            // Fill both adult slots.
            client.post("/v1/bookings/$testBookingId/guests") {
                header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                contentType(ContentType.Application.Json)
                setBody(adultGuestRequest(firstName = "Juan", documentNumber = "12345678Z"))
            }
            client.post("/v1/bookings/$testBookingId/guests") {
                header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                contentType(ContentType.Application.Json)
                setBody(
                    adultGuestRequest(
                        firstName = "Maria",
                        lastName = "Sanchez",
                        documentNumber = "87654321X",
                        documentSupportNumber = "DEF456789",
                    ),
                )
            }

            val response =
                client.post("/v1/bookings/$testBookingId/guests") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
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
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            assertEquals("checkin.booking_full", body["code"]?.jsonPrimitive?.content)
        }

    @Test
    fun `PATCH updates guest fields`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val createResp =
                client.post("/v1/bookings/$testBookingId/guests") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                    contentType(ContentType.Application.Json)
                    setBody(adultGuestRequest(firstName = "Juan"))
                }
            assertEquals(HttpStatusCode.Created, createResp.status)
            val guestId =
                Json
                    .parseToJsonElement(createResp.bodyAsText())
                    .jsonObject["id"]
                    ?.jsonPrimitive
                    ?.content
            assertNotNull(guestId)

            val updated =
                client.patch("/v1/guests/$guestId") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                    contentType(ContentType.Application.Json)
                    setBody(adultGuestRequest(firstName = "Juanito"))
                }

            assertEquals(HttpStatusCode.OK, updated.status)
            val body = Json.parseToJsonElement(updated.bodyAsText()).jsonObject
            assertEquals("Juanito", body["first_name"]?.jsonPrimitive?.content)
        }

    @Test
    fun `PATCH returns guest_not_found when guest does not exist`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val missingId = Uuid.parse("99999999-9999-9999-9999-999999999999")
            val response =
                client.patch("/v1/guests/$missingId") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                    contentType(ContentType.Application.Json)
                    setBody(adultGuestRequest())
                }

            assertEquals(HttpStatusCode.NotFound, response.status)
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            assertEquals("guest.not_found", body["code"]?.jsonPrimitive?.content)
        }

    @Test
    fun `PATCH rejects duplicate document among siblings`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val first =
                client.post("/v1/bookings/$testBookingId/guests") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                    contentType(ContentType.Application.Json)
                    setBody(adultGuestRequest(firstName = "Juan", documentNumber = "12345678Z"))
                }
            assertEquals(HttpStatusCode.Created, first.status)

            val second =
                client.post("/v1/bookings/$testBookingId/guests") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                    contentType(ContentType.Application.Json)
                    setBody(
                        adultGuestRequest(
                            firstName = "Maria",
                            lastName = "Sanchez",
                            documentNumber = "87654321X",
                            documentSupportNumber = "DEF456789",
                        ),
                    )
                }
            assertEquals(HttpStatusCode.Created, second.status)
            val secondId =
                Json
                    .parseToJsonElement(second.bodyAsText())
                    .jsonObject["id"]
                    ?.jsonPrimitive
                    ?.content
            assertNotNull(secondId)

            // Try to update the second guest with the first's document number.
            val response =
                client.patch("/v1/guests/$secondId") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                    contentType(ContentType.Application.Json)
                    setBody(
                        adultGuestRequest(
                            firstName = "Maria",
                            lastName = "Sanchez",
                            documentNumber = "12345678Z",
                            documentSupportNumber = "DEF456789",
                        ),
                    )
                }

            assertEquals(HttpStatusCode.BadRequest, response.status)
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            assertEquals("checkin.duplicate_document", body["code"]?.jsonPrimitive?.content)
        }

    @Test
    fun `PATCH preserves existing relationship_code when omitted in booking with minors`() =
        testApplication {
            // Booking with a minor — adults in this booking need parentesco.
            val withMinorsId = Uuid.parse("40000000-0000-0000-0000-000000000021")
            testDBConnection.insertBooking(
                bookingId = withMinorsId,
                propertyId = TEST_ACCOUNT_1_PROPERTY_1.id,
                from = futureFrom,
                to = futureTo,
                channelId = "owner-minors",
                adultsCount = 1,
                childrenCount = 1,
                infantsCount = 0,
            )

            application { configureTestModules() }
            val client = createClient()

            // Create an adult guest with relationship_code set.
            val createResp =
                client.post("/v1/bookings/$withMinorsId/guests") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                    contentType(ContentType.Application.Json)
                    setBody(adultGuestRequest().copy(relationshipCode = "PM"))
                }
            assertEquals(HttpStatusCode.Created, createResp.status)
            val guestId =
                Json
                    .parseToJsonElement(createResp.bodyAsText())
                    .jsonObject["id"]
                    ?.jsonPrimitive
                    ?.content
            assertNotNull(guestId)

            // PATCH with a request that omits relationship_code (mirrors the
            // dashboard drawer's edit payload). Should succeed because the
            // existing guest already has one.
            val updated =
                client.patch("/v1/guests/$guestId") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                    contentType(ContentType.Application.Json)
                    setBody(adultGuestRequest(firstName = "Juanito"))
                }

            assertEquals(HttpStatusCode.OK, updated.status)
            val body = Json.parseToJsonElement(updated.bodyAsText()).jsonObject
            assertEquals("Juanito", body["first_name"]?.jsonPrimitive?.content)
            assertEquals("PM", body["relationship_code"]?.jsonPrimitive?.content)
        }

    @Test
    fun `DELETE removes a guest as owner`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val guestId = createGuest(client)

            val response =
                client.delete("/v1/guests/$guestId") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                }
            assertEquals(HttpStatusCode.NoContent, response.status)

            // A second delete now 404s — the row is gone.
            val again =
                client.delete("/v1/guests/$guestId") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                }
            assertEquals(HttpStatusCode.NotFound, again.status)
            val body = Json.parseToJsonElement(again.bodyAsText()).jsonObject
            assertEquals("guest.not_found", body["code"]?.jsonPrimitive?.content)
        }

    @Test
    fun `DELETE rejects callers without property access`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val guestId = createGuest(client)

            val response =
                client.delete("/v1/guests/$guestId") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_2_USER_3.token}")
                }
            assertEquals(HttpStatusCode.Forbidden, response.status)
        }

    @Test
    fun `DELETE returns ses_locked once booking submitted to SES`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val guestId = createGuest(client)
            markBookingSesSubmitted(testBookingId)

            val response =
                client.delete("/v1/guests/$guestId") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                }
            assertEquals(HttpStatusCode.Conflict, response.status)
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            assertEquals("guest.ses_locked", body["code"]?.jsonPrimitive?.content)
        }

    @Test
    fun `PATCH returns ses_locked once booking submitted to SES`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val guestId = createGuest(client)
            markBookingSesSubmitted(testBookingId)

            val response =
                client.patch("/v1/guests/$guestId") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                    contentType(ContentType.Application.Json)
                    setBody(adultGuestRequest(firstName = "Juanito"))
                }
            assertEquals(HttpStatusCode.Conflict, response.status)
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            assertEquals("guest.ses_locked", body["code"]?.jsonPrimitive?.content)
        }

    @Test
    fun `marketing consent surfaces on guest responses and suppression overrides it`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            // Guest 1 opts in to marketing; guest 2 does not.
            val optedIn =
                client.post("/v1/bookings/$testBookingId/guests") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                    contentType(ContentType.Application.Json)
                    setBody(adultGuestRequest().copy(marketingConsent = true))
                }
            assertEquals(HttpStatusCode.Created, optedIn.status)
            val optedInBody = Json.parseToJsonElement(optedIn.bodyAsText()).jsonObject
            assertEquals(true, optedInBody["marketing_consent"]?.jsonPrimitive?.boolean)
            assertNotNull(optedInBody["marketing_consent_at"]?.jsonPrimitive?.content)

            val notOptedIn =
                client.post("/v1/bookings/$testBookingId/guests") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                    contentType(ContentType.Application.Json)
                    setBody(
                        adultGuestRequest(
                            firstName = "Maria",
                            lastName = "Sanchez",
                            documentNumber = "87654321X",
                            documentSupportNumber = "DEF456789",
                        ).copy(email = "maria@test.com"),
                    )
                }
            assertEquals(HttpStatusCode.Created, notOptedIn.status)
            val notOptedInBody = Json.parseToJsonElement(notOptedIn.bodyAsText()).jsonObject
            // false/null are omitted from the JSON (encodeDefaults/explicitNulls are off).
            assertEquals(false, notOptedInBody["marketing_consent"]?.jsonPrimitive?.boolean ?: false)
            assertNull(notOptedInBody["marketing_consent_at"])

            // Guest 1 unsubscribes: consent stays stamped but the effective flag drops.
            testDBConnection
                .createStatement()
                .execute("INSERT INTO marketing_suppressions (email) VALUES ('juan@test.com')")

            val list =
                client.get("/v1/guests") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                }
            assertEquals(HttpStatusCode.OK, list.status)
            val guests =
                Json
                    .parseToJsonElement(list.bodyAsText())
                    .jsonObject["guests"]!!
                    .jsonArray
                    .map { it.jsonObject }
            val juan = guests.first { it["first_name"]?.jsonPrimitive?.content == "Juan" }
            assertEquals(false, juan["marketing_consent"]?.jsonPrimitive?.boolean ?: false)
            assertNotNull(juan["marketing_consent_at"]?.jsonPrimitive?.content)
        }

    @Test
    fun `PATCH clears marketing consent when the email changes`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            // Create a guest who opted in with the original address.
            val created =
                client.post("/v1/bookings/$testBookingId/guests") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                    contentType(ContentType.Application.Json)
                    setBody(adultGuestRequest().copy(marketingConsent = true))
                }
            assertEquals(HttpStatusCode.Created, created.status)
            val createdBody = Json.parseToJsonElement(created.bodyAsText()).jsonObject
            assertEquals(true, createdBody["marketing_consent"]?.jsonPrimitive?.boolean)
            val guestId = createdBody["id"]!!.jsonPrimitive.content

            // Owner corrects the address: consent recorded for the old email must not carry over.
            val emailChanged =
                client.patch("/v1/guests/$guestId") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                    contentType(ContentType.Application.Json)
                    setBody(adultGuestRequest().copy(email = "corrected@test.com"))
                }
            assertEquals(HttpStatusCode.OK, emailChanged.status)
            val changedBody = Json.parseToJsonElement(emailChanged.bodyAsText()).jsonObject
            assertEquals(false, changedBody["marketing_consent"]?.jsonPrimitive?.boolean ?: false)
            assertNull(changedBody["marketing_consent_at"])

            // Editing an unrelated field keeps the (now cleared) consent state stable.
            val nameChanged =
                client.patch("/v1/guests/$guestId") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                    contentType(ContentType.Application.Json)
                    setBody(adultGuestRequest(firstName = "Juanito").copy(email = "corrected@test.com"))
                }
            assertEquals(HttpStatusCode.OK, nameChanged.status)
            val nameBody = Json.parseToJsonElement(nameChanged.bodyAsText()).jsonObject
            assertEquals(false, nameBody["marketing_consent"]?.jsonPrimitive?.boolean ?: false)
        }

    private suspend fun createGuest(client: io.ktor.client.HttpClient): String {
        val createResp =
            client.post("/v1/bookings/$testBookingId/guests") {
                header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                contentType(ContentType.Application.Json)
                setBody(adultGuestRequest())
            }
        assertEquals(HttpStatusCode.Created, createResp.status)
        val guestId =
            Json
                .parseToJsonElement(createResp.bodyAsText())
                .jsonObject["id"]
                ?.jsonPrimitive
                ?.content
        assertNotNull(guestId)
        return guestId
    }

    private fun markBookingSesSubmitted(bookingId: Uuid) {
        // SES "submitted" state now lives in ses_communication: an accepted PV alta.
        testDBConnection
            .createStatement()
            .execute(
                "INSERT INTO ses_communication " +
                    "(booking_id, communication_type, operation, status, created_at, updated_at) " +
                    "VALUES ('$bookingId', 'pv', 'alta', 'accepted', now(), now())",
            )
    }
}
