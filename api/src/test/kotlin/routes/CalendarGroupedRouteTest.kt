package eu.kasify.routes

import eu.kasify.TEST_ACCOUNT_1_PROPERTY_1
import eu.kasify.TEST_ACCOUNT_1_USER_1
import eu.kasify.TEST_ACCOUNT_1_USER_2
import eu.kasify.TEST_ACCOUNT_2_USER_3
import eu.kasify.extensions.configureTestModules
import eu.kasify.extensions.createClient
import eu.kasify.helpers.grantCrewAccess
import eu.kasify.helpers.insertBooking
import eu.kasify.helpers.insertTask
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
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for the property-grouped /v1/calendar endpoint introduced to feed the
 * property-row calendar UI. Validates the grouped response shape, date-range
 * validation, and that the existing per-property contractor/owner filtering
 * carries over into the grouped layout.
 */
class CalendarGroupedRouteTest : BaseRouteDBTests() {
    override fun setupData() {
        // A booking inside the test window so owners and full crew see at least one event.
        testDBConnection.insertBooking(
            propertyId = TEST_ACCOUNT_1_PROPERTY_1.id,
            from = LocalDateTime.of(2026, 5, 10, 17, 0),
            to = LocalDateTime.of(2026, 5, 14, 11, 0),
            guestFirstName = "Owner",
            guestLastName = "Visible",
            guestEmail = "owner@example.com",
        )
        // A booking that straddles the window's start: starts before `from`, ends inside.
        testDBConnection.insertBooking(
            propertyId = TEST_ACCOUNT_1_PROPERTY_1.id,
            from = LocalDateTime.of(2026, 4, 28, 17, 0),
            to = LocalDateTime.of(2026, 5, 3, 11, 0),
            guestFirstName = "Boundary",
            guestLastName = "Visible",
            guestEmail = "boundary@example.com",
        )
        // A booking entirely outside the window — must NOT appear.
        testDBConnection.insertBooking(
            propertyId = TEST_ACCOUNT_1_PROPERTY_1.id,
            from = LocalDateTime.of(2026, 7, 1, 17, 0),
            to = LocalDateTime.of(2026, 7, 5, 11, 0),
            guestFirstName = "Outside",
            guestLastName = "Hidden",
            guestEmail = "outside@example.com",
        )
    }

    private val from = "2026-05-01"
    private val to = "2026-05-31"

    @Test
    fun `Owner sees grouped properties with their events for the window`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val response =
                client.get("/v1/calendar?from=$from&to=$to") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                }

            assertEquals(HttpStatusCode.OK, response.status)
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject

            // Top-level shape
            assertTrue(body.containsKey("properties"))
            assertEquals(from, body["from"]?.jsonPrimitive?.content)
            assertEquals(to, body["to"]?.jsonPrimitive?.content)

            val properties = body["properties"]?.jsonArray ?: error("Missing properties[]")
            assertTrue(properties.isNotEmpty(), "Owner should see their property as a row")

            val row =
                properties.firstOrNull { it.jsonObject["id"]?.jsonPrimitive?.content == TEST_ACCOUNT_1_PROPERTY_1.id.toString() }
                    ?: error("Owner's property row missing")
            assertEquals(TEST_ACCOUNT_1_PROPERTY_1.name, row.jsonObject["name"]?.jsonPrimitive?.content)

            val events = row.jsonObject["events"]?.jsonArray ?: error("Missing events[] on property row")
            // Two events overlap the window: the in-window booking and the boundary-straddling one.
            assertEquals(2, events.size, "Expected the in-window and boundary-straddling bookings only")

            // Owner should see channelId on bookings (Visibility.OWNER).
            val anyBookingHasChannelId =
                events.any { evt ->
                    evt.jsonObject["booking"]
                        ?.jsonObject
                        ?.get("channelId")
                        ?.jsonPrimitive
                        ?.content != null
                }
            assertTrue(anyBookingHasChannelId, "Owner must see booking.channelId in grouped response")
        }

    @Test
    fun `Properties with zero events still appear as rows with empty events`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            // Account 2 user 3 has no properties yet — give them an account-level
            // property so the response includes an empty row.
            // (Account 2 has no seeded properties; account 1's property is owned by user 1.)
            // Use a window with no events for user 1's property to assert empty events[].
            val response =
                client.get("/v1/calendar?from=2026-01-01&to=2026-01-31") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                }

            assertEquals(HttpStatusCode.OK, response.status)
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            val properties = body["properties"]?.jsonArray ?: error("Missing properties[]")

            val row =
                properties.firstOrNull { it.jsonObject["id"]?.jsonPrimitive?.content == TEST_ACCOUNT_1_PROPERTY_1.id.toString() }
                    ?: error("Owner's property row missing")
            val events = row.jsonObject["events"]?.jsonArray ?: error("Missing events[]")
            assertEquals(0, events.size, "Empty window should return an empty events list, not omit the row")
        }

    @Test
    fun `Contractor sees only events tied to assigned tasks`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            testDBConnection.grantCrewAccess(
                userId = TEST_ACCOUNT_2_USER_3.id,
                propertyId = TEST_ACCOUNT_1_PROPERTY_1.id,
                accessLevel = "contractor",
            )

            // Insert a task assigned to the contractor — its calendar event should be visible.
            testDBConnection.insertTask(
                propertyId = TEST_ACCOUNT_1_PROPERTY_1.id,
                scheduledDate = LocalDateTime.of(2026, 5, 12, 10, 0),
                taskType = "cleaning",
                status = "pending",
                assignedTo = TEST_ACCOUNT_2_USER_3.id,
            )

            // Insert a task NOT assigned to the contractor — must be hidden.
            testDBConnection.insertTask(
                propertyId = TEST_ACCOUNT_1_PROPERTY_1.id,
                scheduledDate = LocalDateTime.of(2026, 5, 13, 10, 0),
                taskType = "maintenance",
                status = "pending",
                assignedTo = TEST_ACCOUNT_1_USER_1.id,
            )

            val response =
                client.get("/v1/calendar?from=$from&to=$to") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_2_USER_3.token}")
                }

            assertEquals(HttpStatusCode.OK, response.status)
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            val properties = body["properties"]?.jsonArray ?: error("Missing properties[]")

            val row =
                properties.firstOrNull { it.jsonObject["id"]?.jsonPrimitive?.content == TEST_ACCOUNT_1_PROPERTY_1.id.toString() }
                    ?: error("Contractor's accessible property row missing")
            val events = row.jsonObject["events"]?.jsonArray ?: error("Missing events[]")

            // Contractor must NOT see bookings (no assigned task on them) or the unassigned task.
            val seenTypes = events.map { it.jsonObject["type"]?.jsonPrimitive?.content }
            assertTrue(
                seenTypes.none { it == "booking" },
                "Contractor must not see booking events on their accessible property",
            )
            assertTrue(
                seenTypes.all { it == "task" },
                "Contractor should only see events tied to their assigned task. Saw: $seenTypes",
            )
            val seenTaskKinds =
                events.map {
                    it.jsonObject["task"]
                        ?.jsonObject
                        ?.get("task_type")
                        ?.jsonPrimitive
                        ?.content
                }
            assertTrue(
                seenTaskKinds.all { it == "cleaning" },
                "Contractor's visible task should be the cleaning one. Saw: $seenTaskKinds",
            )
            assertEquals(1, events.size, "Contractor should see exactly one event (their assigned cleaning task)")

            // Also verify channelId is not exposed in any way.
            val raw = response.bodyAsText()
            assertTrue(!raw.contains("airbnbId123"), "Contractor response must not contain booking channel id")
        }

    @Test
    fun `Staff sees own task in full and foreign task redacted to id only`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            testDBConnection.grantCrewAccess(
                userId = TEST_ACCOUNT_2_USER_3.id,
                propertyId = TEST_ACCOUNT_1_PROPERTY_1.id,
                accessLevel = "staff",
            )

            val ownTaskId =
                testDBConnection.insertTask(
                    propertyId = TEST_ACCOUNT_1_PROPERTY_1.id,
                    scheduledDate = LocalDateTime.of(2026, 5, 12, 10, 0),
                    taskType = "cleaning",
                    status = "pending",
                    assignedTo = TEST_ACCOUNT_2_USER_3.id,
                )
            val foreignTaskId =
                testDBConnection.insertTask(
                    propertyId = TEST_ACCOUNT_1_PROPERTY_1.id,
                    scheduledDate = LocalDateTime.of(2026, 5, 13, 10, 0),
                    taskType = "maintenance",
                    status = "pending",
                    assignedTo = TEST_ACCOUNT_1_USER_1.id,
                )

            val response =
                client.get("/v1/calendar?from=$from&to=$to") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_2_USER_3.token}")
                }

            assertEquals(HttpStatusCode.OK, response.status)
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            val properties = body["properties"]?.jsonArray ?: error("Missing properties[]")
            val row =
                properties.firstOrNull { it.jsonObject["id"]?.jsonPrimitive?.content == TEST_ACCOUNT_1_PROPERTY_1.id.toString() }
                    ?: error("Staff's accessible property row missing")
            val events = row.jsonObject["events"]?.jsonArray ?: error("Missing events[]")

            val tasks =
                events
                    .map { it.jsonObject["task"]?.jsonObject }
                    .filterNotNull()
                    .associateBy { it["id"]?.jsonPrimitive?.content }

            val own = tasks[ownTaskId.toString()] ?: error("Staff's own task missing from response")
            assertEquals("cleaning", own["task_type"]?.jsonPrimitive?.content)
            assertEquals("pending", own["status"]?.jsonPrimitive?.content)
            assertEquals(TEST_ACCOUNT_2_USER_3.id.toString(), own["assigned_to"]?.jsonPrimitive?.content)
            assertNotNull(own["assigned_to_name"], "Own task should expose assignee name")

            val foreign = tasks[foreignTaskId.toString()] ?: error("Foreign task missing from response")
            assertEquals(
                setOf("id"),
                foreign.keys,
                "Foreign task should be redacted to id only. Saw: ${foreign.keys}",
            )
        }

    @Test
    fun `Manager sees foreign task details in full`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            testDBConnection.grantCrewAccess(
                userId = TEST_ACCOUNT_2_USER_3.id,
                propertyId = TEST_ACCOUNT_1_PROPERTY_1.id,
                accessLevel = "manager",
            )

            val foreignTaskId =
                testDBConnection.insertTask(
                    propertyId = TEST_ACCOUNT_1_PROPERTY_1.id,
                    scheduledDate = LocalDateTime.of(2026, 5, 13, 10, 0),
                    taskType = "maintenance",
                    status = "pending",
                    assignedTo = TEST_ACCOUNT_1_USER_1.id,
                )

            val response =
                client.get("/v1/calendar?from=$from&to=$to") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_2_USER_3.token}")
                }

            assertEquals(HttpStatusCode.OK, response.status)
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            val properties = body["properties"]?.jsonArray ?: error("Missing properties[]")
            val row =
                properties.firstOrNull { it.jsonObject["id"]?.jsonPrimitive?.content == TEST_ACCOUNT_1_PROPERTY_1.id.toString() }
                    ?: error("Manager's accessible property row missing")
            val events = row.jsonObject["events"]?.jsonArray ?: error("Missing events[]")

            val foreign =
                events
                    .map { it.jsonObject["task"]?.jsonObject }
                    .firstOrNull { it?.get("id")?.jsonPrimitive?.content == foreignTaskId.toString() }
                    ?: error("Foreign task missing for manager")

            assertEquals("maintenance", foreign["task_type"]?.jsonPrimitive?.content)
            assertEquals("pending", foreign["status"]?.jsonPrimitive?.content)
            assertEquals(TEST_ACCOUNT_1_USER_1.id.toString(), foreign["assigned_to"]?.jsonPrimitive?.content)
        }

    @Test
    fun `Owner sees full details for tasks assigned to other account users`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            // Owner (USER_1) views a task assigned to another account user (USER_2).
            val taskId =
                testDBConnection.insertTask(
                    propertyId = TEST_ACCOUNT_1_PROPERTY_1.id,
                    scheduledDate = LocalDateTime.of(2026, 5, 13, 10, 0),
                    taskType = "maintenance",
                    status = "pending",
                    assignedTo = TEST_ACCOUNT_1_USER_2.id,
                )

            val response =
                client.get("/v1/calendar?from=$from&to=$to") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                }

            assertEquals(HttpStatusCode.OK, response.status)
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            val properties = body["properties"]?.jsonArray ?: error("Missing properties[]")
            val row =
                properties.firstOrNull { it.jsonObject["id"]?.jsonPrimitive?.content == TEST_ACCOUNT_1_PROPERTY_1.id.toString() }
                    ?: error("Owner's property row missing")
            val events = row.jsonObject["events"]?.jsonArray ?: error("Missing events[]")

            val task =
                events
                    .map { it.jsonObject["task"]?.jsonObject }
                    .firstOrNull { it?.get("id")?.jsonPrimitive?.content == taskId.toString() }
                    ?: error("Task missing for owner")

            assertEquals("maintenance", task["task_type"]?.jsonPrimitive?.content)
            assertEquals(TEST_ACCOUNT_1_USER_2.id.toString(), task["assigned_to"]?.jsonPrimitive?.content)
        }

    @Test
    fun `Staff sees foreign task redacted on property-scoped calendar route`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            testDBConnection.grantCrewAccess(
                userId = TEST_ACCOUNT_2_USER_3.id,
                propertyId = TEST_ACCOUNT_1_PROPERTY_1.id,
                accessLevel = "staff",
            )

            val ownTaskId =
                testDBConnection.insertTask(
                    propertyId = TEST_ACCOUNT_1_PROPERTY_1.id,
                    scheduledDate = LocalDateTime.of(2026, 5, 12, 10, 0),
                    taskType = "cleaning",
                    status = "pending",
                    assignedTo = TEST_ACCOUNT_2_USER_3.id,
                )
            val foreignTaskId =
                testDBConnection.insertTask(
                    propertyId = TEST_ACCOUNT_1_PROPERTY_1.id,
                    scheduledDate = LocalDateTime.of(2026, 5, 13, 10, 0),
                    taskType = "maintenance",
                    status = "pending",
                    assignedTo = TEST_ACCOUNT_1_USER_1.id,
                )

            val response =
                client.get("/v1/properties/${TEST_ACCOUNT_1_PROPERTY_1.id}/calendar?from=$from&to=$to") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_2_USER_3.token}")
                }

            assertEquals(HttpStatusCode.OK, response.status)
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            val events = body["events"]?.jsonArray ?: error("Missing events[]")

            val tasks =
                events
                    .map { it.jsonObject["task"]?.jsonObject }
                    .filterNotNull()
                    .associateBy { it["id"]?.jsonPrimitive?.content }

            val own = tasks[ownTaskId.toString()] ?: error("Staff's own task missing")
            assertEquals("cleaning", own["task_type"]?.jsonPrimitive?.content)

            val foreign = tasks[foreignTaskId.toString()] ?: error("Foreign task missing")
            assertEquals(
                setOf("id"),
                foreign.keys,
                "Foreign task should be redacted to id only on /properties/{id}/calendar too. Saw: ${foreign.keys}",
            )
        }

    @Test
    fun `Missing from defaults to today and returns 200`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            // `from` defaults to today, so `to` must be on or after today for the window to be
            // valid — compute it relative to now rather than a fixed date so the test never
            // goes stale as the calendar advances past a hardcoded window.
            val futureTo = LocalDate.now().plusMonths(1).toString()
            val response =
                client.get("/v1/calendar?to=$futureTo") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                }

            assertEquals(HttpStatusCode.OK, response.status)
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            assertNotNull(body["from"], "Response should expose the resolved `from`")
            assertEquals(futureTo, body["to"]?.jsonPrimitive?.content)
        }

    @Test
    fun `Missing to defaults to one month after from and returns 200`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val response =
                client.get("/v1/calendar?from=$from") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                }

            assertEquals(HttpStatusCode.OK, response.status)
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            assertEquals(from, body["from"]?.jsonPrimitive?.content)
            // `from` was 2026-05-01; default `to` should be exactly one month later.
            assertEquals("2026-06-01", body["to"]?.jsonPrimitive?.content)
        }

    @Test
    fun `Both params missing defaults to today through one month from today`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val response =
                client.get("/v1/calendar") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                }

            assertEquals(HttpStatusCode.OK, response.status)
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            assertNotNull(body["from"], "Response should expose the resolved `from`")
            assertNotNull(body["to"], "Response should expose the resolved `to`")
        }

    @Test
    fun `Inverted range returns 400 with calendar invalid_range`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val response =
                client.get("/v1/calendar?from=2026-06-01&to=2026-05-01") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                }

            assertEquals(HttpStatusCode.BadRequest, response.status)
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            assertEquals("calendar.invalid_range", body["code"]?.jsonPrimitive?.content)
        }

    @Test
    fun `Window over 366 days returns 400 with calendar invalid_range`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val response =
                client.get("/v1/calendar?from=2026-01-01&to=2027-01-02") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                }

            assertEquals(HttpStatusCode.BadRequest, response.status)
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            assertEquals("calendar.invalid_range", body["code"]?.jsonPrimitive?.content)
        }

    @Test
    fun `Unauthenticated request returns 401`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val response = client.get("/v1/calendar?from=$from&to=$to")
            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }

    @Test
    fun `Response exposes the resolved window`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val response =
                client.get("/v1/calendar?from=$from&to=$to") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                }

            assertEquals(HttpStatusCode.OK, response.status)
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            assertEquals(from, body["from"]?.jsonPrimitive?.content)
            assertEquals(to, body["to"]?.jsonPrimitive?.content)
            assertTrue(body.containsKey("properties"))
        }
}
