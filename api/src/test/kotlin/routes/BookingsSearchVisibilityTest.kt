package eu.kasify.routes
import eu.kasify.TEST_ACCOUNT_1_PROPERTY_1
import eu.kasify.TEST_ACCOUNT_1_USER_1
import eu.kasify.TEST_ACCOUNT_2_USER_3
import eu.kasify.TEST_ACCOUNT_ID_2
import eu.kasify.TestUser
import eu.kasify.extensions.configureTestModules
import eu.kasify.extensions.createClient
import eu.kasify.helpers.grantCrewAccess
import eu.kasify.helpers.insertBooking
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

/**
 * The free-text `query` filter on the booking list endpoints must not match on fields the
 * caller cannot see (guest last name, channel id, SES lote are MANAGER-level). Otherwise
 * match / no-match is an oracle a Staff member can use to rebuild those values.
 */
class BookingsSearchVisibilityTest : BaseRouteDBTests() {
    private val sharedBookingId = Uuid.parse("40000000-0000-0000-0000-0000000000f1")
    private val ownedBookingId = Uuid.parse("40000000-0000-0000-0000-0000000000f2")
    private val ownedPropertyId = Uuid.parse("30000000-0000-0000-0000-0000000000f2")

    // Substrings present only in MANAGER-level fields of both bookings.
    private val lastNameQuery = "zebrow"
    private val channelIdQuery = "hmxq77"
    private val sesLoteQuery = "lote99"
    private val hiddenFieldQueries = listOf(lastNameQuery, channelIdQuery, sesLoteQuery)

    // Substring of the (Staff-visible) guest first name of both bookings.
    private val firstNameQuery = "marisol"

    override fun setupData() {
        testDBConnection.insertBooking(
            bookingId = sharedBookingId,
            propertyId = TEST_ACCOUNT_1_PROPERTY_1.id,
            from = LocalDateTime.of(2025, 3, 1, 17, 0),
            to = LocalDateTime.of(2025, 3, 10, 11, 0),
            guestFirstName = "Marisol",
            guestLastName = "Zebrowski",
            channelId = "HMXQ7781",
        )
        insertSesLote(sharedBookingId, "LOTE99-0001")
    }

    private fun insertSesLote(
        bookingId: Uuid,
        lote: String,
    ) {
        testDBConnection.executeQuery(
            "INSERT INTO ses_communication " +
                "(booking_id, communication_type, operation, status, lote, created_at, updated_at) " +
                "VALUES ('$bookingId', 'pv', 'alta', 'accepted', '$lote', now(), now())",
        )
    }

    /** Makes TEST_ACCOUNT_2_USER_3 the owner of a second property with a similar booking. */
    private fun insertOwnedPropertyWithBooking() {
        testDBConnection.insertProperty(
            id = ownedPropertyId,
            accountId = TEST_ACCOUNT_ID_2,
            name = "Owned by user 3",
        )
        testDBConnection.insertBooking(
            bookingId = ownedBookingId,
            propertyId = ownedPropertyId,
            from = LocalDateTime.of(2025, 4, 1, 17, 0),
            to = LocalDateTime.of(2025, 4, 10, 11, 0),
            guestFirstName = "Marisol",
            guestLastName = "Zebrowska",
            channelId = "HMXQ7782",
        )
        insertSesLote(ownedBookingId, "LOTE99-0002")
    }

    private suspend fun HttpClient.bookingIds(
        path: String,
        user: TestUser,
        query: String,
    ): Set<Uuid> {
        val response =
            get("$path?query=$query") {
                header(HttpHeaders.Authorization, "Bearer ${user.token}")
            }
        assertEquals(HttpStatusCode.OK, response.status)
        return Json
            .parseToJsonElement(response.bodyAsText())
            .jsonObject["bookings"]!!
            .jsonArray
            .map { Uuid.parse(it.jsonObject["id"]!!.jsonPrimitive.content) }
            .toSet()
    }

    private val propertyBookingsPath = "/v1/properties/${TEST_ACCOUNT_1_PROPERTY_1.id}/bookings"
    private val allBookingsPath = "/v1/bookings"

    // ============================================================
    // STAFF
    // ============================================================

    @Test
    fun `Staff query on manager-only fields matches nothing in GET property bookings`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()
            testDBConnection.grantCrewAccess(TEST_ACCOUNT_2_USER_3.id, TEST_ACCOUNT_1_PROPERTY_1.id, accessLevel = "staff")

            hiddenFieldQueries.forEach { q ->
                assertEquals(
                    emptySet(),
                    client.bookingIds(propertyBookingsPath, TEST_ACCOUNT_2_USER_3, q),
                    "staff query '$q' must not match a manager-only field",
                )
            }
        }

    @Test
    fun `Staff query on manager-only fields matches nothing in GET bookings`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()
            testDBConnection.grantCrewAccess(TEST_ACCOUNT_2_USER_3.id, TEST_ACCOUNT_1_PROPERTY_1.id, accessLevel = "staff")

            hiddenFieldQueries.forEach { q ->
                assertEquals(
                    emptySet(),
                    client.bookingIds(allBookingsPath, TEST_ACCOUNT_2_USER_3, q),
                    "staff query '$q' must not match a manager-only field",
                )
            }
        }

    @Test
    fun `Staff query on guest first name still matches in both list endpoints`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()
            testDBConnection.grantCrewAccess(TEST_ACCOUNT_2_USER_3.id, TEST_ACCOUNT_1_PROPERTY_1.id, accessLevel = "staff")

            assertEquals(
                setOf(sharedBookingId),
                client.bookingIds(propertyBookingsPath, TEST_ACCOUNT_2_USER_3, firstNameQuery),
            )
            assertEquals(
                setOf(sharedBookingId),
                client.bookingIds(allBookingsPath, TEST_ACCOUNT_2_USER_3, firstNameQuery),
            )
        }

    // ============================================================
    // MANAGER
    // ============================================================

    @Test
    fun `Manager query matches all searchable fields in both list endpoints`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()
            testDBConnection.grantCrewAccess(TEST_ACCOUNT_2_USER_3.id, TEST_ACCOUNT_1_PROPERTY_1.id, accessLevel = "manager")

            (hiddenFieldQueries + firstNameQuery).forEach { q ->
                assertEquals(
                    setOf(sharedBookingId),
                    client.bookingIds(propertyBookingsPath, TEST_ACCOUNT_2_USER_3, q),
                    "manager query '$q' should match in GET property bookings",
                )
                assertEquals(
                    setOf(sharedBookingId),
                    client.bookingIds(allBookingsPath, TEST_ACCOUNT_2_USER_3, q),
                    "manager query '$q' should match in GET bookings",
                )
            }
        }

    // ============================================================
    // OWNER
    // ============================================================

    @Test
    fun `Owner query matches all searchable fields in both list endpoints`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            (hiddenFieldQueries + firstNameQuery).forEach { q ->
                assertEquals(
                    setOf(sharedBookingId),
                    client.bookingIds(propertyBookingsPath, TEST_ACCOUNT_1_USER_1, q),
                    "owner query '$q' should match in GET property bookings",
                )
                assertEquals(
                    setOf(sharedBookingId),
                    client.bookingIds(allBookingsPath, TEST_ACCOUNT_1_USER_1, q),
                    "owner query '$q' should match in GET bookings",
                )
            }
        }

    // ============================================================
    // MIXED: staff on one property, owner of another
    // ============================================================

    @Test
    fun `Staff on one property and owner of another only matches manager-only fields on the owned booking`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()
            testDBConnection.grantCrewAccess(TEST_ACCOUNT_2_USER_3.id, TEST_ACCOUNT_1_PROPERTY_1.id, accessLevel = "staff")
            insertOwnedPropertyWithBooking()

            hiddenFieldQueries.forEach { q ->
                assertEquals(
                    setOf(ownedBookingId),
                    client.bookingIds(allBookingsPath, TEST_ACCOUNT_2_USER_3, q),
                    "query '$q' should only match the owned booking in GET bookings",
                )
                assertEquals(
                    emptySet(),
                    client.bookingIds(propertyBookingsPath, TEST_ACCOUNT_2_USER_3, q),
                    "query '$q' must not match on the staff property",
                )
                assertEquals(
                    setOf(ownedBookingId),
                    client.bookingIds("/v1/properties/$ownedPropertyId/bookings", TEST_ACCOUNT_2_USER_3, q),
                    "query '$q' should match on the owned property",
                )
            }

            // Visible-field search still spans both properties.
            assertEquals(
                setOf(sharedBookingId, ownedBookingId),
                client.bookingIds(allBookingsPath, TEST_ACCOUNT_2_USER_3, firstNameQuery),
            )
        }
}
