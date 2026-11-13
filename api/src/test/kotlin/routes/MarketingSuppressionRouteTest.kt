package eu.kasify.routes
import eu.kasify.TEST_ACCOUNT_1_PROPERTY_1
import eu.kasify.TEST_ACCOUNT_1_USER_1
import eu.kasify.TEST_ACCOUNT_2_USER_3
import eu.kasify.TEST_ACCOUNT_ID_2
import eu.kasify.TEST_USA_COUNTRY_ID
import eu.kasify.db.model.DocumentType
import eu.kasify.db.model.Gender
import eu.kasify.extensions.configureTestModules
import eu.kasify.extensions.createClient
import eu.kasify.helpers.insertBooking
import eu.kasify.model.api.requests.GuestRegistrationRequest
import io.ktor.client.HttpClient
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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.uuid.Uuid

/**
 * Marketing suppressions are time-aware and never lifted by a self-asserted consent:
 * an unsubscribe withdraws every consent recorded up to it (in every account), while a
 * consent recorded later counts for its own guest row only.
 */
class MarketingSuppressionRouteTest : BaseRouteDBTests() {
    private val victimEmail = "victim@test.com"
    private val futureFrom = LocalDateTime.now().plusDays(10)
    private val futureTo = LocalDateTime.now().plusDays(15)

    // Account 1: the victim's real stays.
    private val victimStayCode = "victim-stay"
    private val victimSecondStayCode = "victim-second-stay"

    // Account 2: an unrelated (attacker-controlled) account with its own property and booking.
    private val otherPropertyId = Uuid.parse("30000000-0000-0000-0000-0000000000f2")
    private val otherBookingId = Uuid.parse("40000000-0000-0000-0000-0000000000f2")
    private val otherStayCode = "other-account-stay"

    override fun setupData() {
        testDBConnection.createStatement().execute(
            "INSERT INTO countries (id, name, name_es, alpha_2, alpha_3) VALUES " +
                "('$TEST_USA_COUNTRY_ID', 'United States', 'Estados Unidos', 'US', 'USA')",
        )
        testDBConnection.insertBooking(
            propertyId = TEST_ACCOUNT_1_PROPERTY_1.id,
            from = futureFrom,
            to = futureTo,
            channelId = "victim-stay-ref",
            checkinCode = victimStayCode,
            adultsCount = 2,
        )
        testDBConnection.insertBooking(
            propertyId = TEST_ACCOUNT_1_PROPERTY_1.id,
            from = futureFrom.plusDays(20),
            to = futureTo.plusDays(20),
            channelId = "victim-second-stay-ref",
            checkinCode = victimSecondStayCode,
            adultsCount = 2,
        )
        testDBConnection.insertProperty(
            id = otherPropertyId,
            accountId = TEST_ACCOUNT_ID_2,
            name = "Other account property",
        )
        testDBConnection.insertBooking(
            bookingId = otherBookingId,
            propertyId = otherPropertyId,
            from = futureFrom,
            to = futureTo,
            channelId = "other-account-stay-ref",
            checkinCode = otherStayCode,
            adultsCount = 2,
        )
    }

    private fun consentedGuestRequest(
        firstName: String,
        email: String = victimEmail,
        documentNumber: String = "12345678Z",
        documentSupportNumber: String = "ABC123456",
    ) = GuestRegistrationRequest(
        firstName = firstName,
        lastName = "Garcia",
        secondLastName = "Lopez",
        nationalityCountryCode = "ESP",
        dateOfBirth = LocalDate.of(1990, 1, 1),
        sex = Gender.Male,
        documentType = DocumentType.DNI,
        documentNumber = documentNumber,
        documentSupportNumber = documentSupportNumber,
        residenceAddress = "Calle Test 1",
        residenceCountryCode = "USA",
        residenceCity = "New York",
        residencePostalCode = "10001",
        phone = "612345678",
        phonePrefix = "+34",
        email = email,
        marketingConsent = true,
    )

    private suspend fun registerViaCheckin(
        client: HttpClient,
        code: String,
        request: GuestRegistrationRequest,
    ) {
        val response =
            client.post("/v1/checkin/$code/guests") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        assertEquals(HttpStatusCode.Created, response.status, response.bodyAsText())
    }

    private suspend fun listGuests(
        client: HttpClient,
        token: String,
    ): List<JsonObject> {
        val response =
            client.get("/v1/guests") {
                header(HttpHeaders.Authorization, "Bearer $token")
            }
        assertEquals(HttpStatusCode.OK, response.status)
        return Json
            .parseToJsonElement(response.bodyAsText())
            .jsonObject["guests"]!!
            .jsonArray
            .map { it.jsonObject }
    }

    private fun List<JsonObject>.byFirstName(firstName: String): JsonObject = first { it["first_name"]?.jsonPrimitive?.content == firstName }

    // false is omitted from the JSON (encodeDefaults is off).
    private fun JsonObject.marketingConsent(): Boolean = this["marketing_consent"]?.jsonPrimitive?.boolean ?: false

    private fun unsubscribeTokenOf(firstName: String): String =
        testDBConnection.createStatement().use { stmt ->
            stmt
                .executeQuery(
                    "SELECT marketing_unsubscribe_token FROM guests WHERE first_name = '$firstName'",
                ).use { rs ->
                    check(rs.next()) { "guest $firstName not found" }
                    rs.getString(1)
                }
        }

    private fun suppressionCount(email: String): Int =
        testDBConnection.createStatement().use { stmt ->
            stmt.executeQuery("SELECT count(*) FROM marketing_suppressions WHERE email = '$email'").use { rs ->
                rs.next()
                rs.getInt(1)
            }
        }

    private suspend fun unsubscribe(
        client: HttpClient,
        token: String,
    ) {
        val response = client.post("/v1/marketing/unsubscribe/$token")
        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
    }

    @Test
    fun `public check-in consent with a suppressed address does not lift the suppression`() =
        testApplication {
            application {
                configureTestModules()
                configureMarketingRouting()
            }
            val client = createClient()

            // The victim stayed with account 1, opted in, and later unsubscribed.
            registerViaCheckin(client, victimStayCode, consentedGuestRequest(firstName = "Victim"))
            unsubscribe(client, unsubscribeTokenOf("Victim"))
            assertEquals(1, suppressionCount(victimEmail))
            assertEquals(false, listGuests(client, TEST_ACCOUNT_1_USER_1.token).byFirstName("Victim").marketingConsent())

            // Anyone holding some other booking's check-in code registers a guest with the
            // victim's address and ticks the consent box.
            registerViaCheckin(
                client,
                otherStayCode,
                consentedGuestRequest(firstName = "Impostor", email = "Victim@Test.com"),
            )

            // The victim's opt-out survives, and account 1 still sees them as unsubscribed.
            assertEquals(1, suppressionCount(victimEmail))
            val victim = listGuests(client, TEST_ACCOUNT_1_USER_1.token).byFirstName("Victim")
            assertEquals(false, victim.marketingConsent())
            assertNotNull(victim["marketing_consent_at"]?.jsonPrimitive?.content)
        }

    @Test
    fun `a freshly consented row reads consented regardless of other accounts' suppressions`() =
        testApplication {
            application {
                configureTestModules()
                configureMarketingRouting()
            }
            val client = createClient()

            // Account 1's guest with this address unsubscribed.
            registerViaCheckin(client, victimStayCode, consentedGuestRequest(firstName = "Victim"))
            unsubscribe(client, unsubscribeTokenOf("Victim"))

            // Account 2 records new consents for the same address, owner-side and via check-in.
            // Neither may reveal that the address is suppressed elsewhere.
            val ownerSide =
                client.post("/v1/bookings/$otherBookingId/guests") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_2_USER_3.token}")
                    contentType(ContentType.Application.Json)
                    setBody(consentedGuestRequest(firstName = "OwnerSide"))
                }
            assertEquals(HttpStatusCode.Created, ownerSide.status, ownerSide.bodyAsText())
            val ownerSideBody = Json.parseToJsonElement(ownerSide.bodyAsText()).jsonObject
            assertEquals(true, ownerSideBody.marketingConsent())

            registerViaCheckin(
                client,
                otherStayCode,
                consentedGuestRequest(
                    firstName = "PublicSide",
                    documentNumber = "87654321X",
                    documentSupportNumber = "DEF456789",
                ),
            )

            val otherAccountGuests = listGuests(client, TEST_ACCOUNT_2_USER_3.token)
            assertEquals(true, otherAccountGuests.byFirstName("OwnerSide").marketingConsent())
            assertEquals(true, otherAccountGuests.byFirstName("PublicSide").marketingConsent())

            // ...and the earlier opt-out still holds for account 1's row.
            assertEquals(false, listGuests(client, TEST_ACCOUNT_1_USER_1.token).byFirstName("Victim").marketingConsent())
        }

    @Test
    fun `re-consent after unsubscribing counts until the guest unsubscribes again`() =
        testApplication {
            application {
                configureTestModules()
                configureMarketingRouting()
            }
            val client = createClient()

            registerViaCheckin(client, victimStayCode, consentedGuestRequest(firstName = "FirstStay"))
            unsubscribe(client, unsubscribeTokenOf("FirstStay"))

            // A later stay with a fresh consent supersedes the old opt-out for that row.
            registerViaCheckin(client, victimSecondStayCode, consentedGuestRequest(firstName = "SecondStay"))
            val afterReconsent = listGuests(client, TEST_ACCOUNT_1_USER_1.token)
            assertEquals(false, afterReconsent.byFirstName("FirstStay").marketingConsent())
            assertEquals(true, afterReconsent.byFirstName("SecondStay").marketingConsent())

            // Unsubscribing through the new row's link must withdraw that consent too.
            unsubscribe(client, unsubscribeTokenOf("SecondStay"))
            val afterSecondUnsubscribe = listGuests(client, TEST_ACCOUNT_1_USER_1.token)
            assertEquals(false, afterSecondUnsubscribe.byFirstName("FirstStay").marketingConsent())
            assertEquals(false, afterSecondUnsubscribe.byFirstName("SecondStay").marketingConsent())
            assertEquals(1, suppressionCount(victimEmail))
        }
}
