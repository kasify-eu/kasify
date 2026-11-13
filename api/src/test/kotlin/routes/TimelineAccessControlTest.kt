package eu.kasify.routes
import eu.kasify.TEST_ACCOUNT_1_PROPERTY_1
import eu.kasify.TEST_ACCOUNT_1_USER_1
import eu.kasify.TEST_ACCOUNT_2_USER_3
import eu.kasify.extensions.configureTestModules
import eu.kasify.extensions.createClient
import eu.kasify.helpers.grantCrewAccess
import eu.kasify.helpers.insertBooking
import eu.kasify.helpers.insertTask
import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for role-based access control on the timeline/activities endpoint.
 * The /activities endpoint returns a flat paginated list with access control:
 * - Owners see all events (bookings + tasks) for their properties
 * - Full crew sees all events for granted properties
 * - Limited crew sees bookings only for granted properties
 * - TaskOnly crew sees only assigned tasks
 */
class TimelineAccessControlTest : BaseRouteDBTests() {
    override fun setupData() {
        // Insert a booking that checks in tomorrow (within upcoming events)
        testDBConnection.insertBooking(
            propertyId = TEST_ACCOUNT_1_PROPERTY_1.id,
            from =
                LocalDateTime
                    .now()
                    .plusDays(1)
                    .withHour(17)
                    .withMinute(0),
            to =
                LocalDateTime
                    .now()
                    .plusDays(5)
                    .withHour(11)
                    .withMinute(0),
            guestFirstName = "Timeline",
            guestLastName = "Guest",
            guestEmail = "timeline@email.com",
        )
    }

    @Test
    fun `Owner sees activities for their bookings`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val response =
                client.get("/v1/activities?page=0&size=20") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                }

            assertEquals(HttpStatusCode.OK, response.status)
            val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            val activities = json["activities"]?.jsonArray ?: error("Missing 'activities' field")

            assertTrue(activities.isNotEmpty(), "Owner should see activities")

            // Check that a booking activity exists
            val hasBooking =
                activities.any { activity ->
                    activity.jsonObject["type"]?.jsonPrimitive?.content == "booking"
                }
            assertTrue(hasBooking, "Owner should see booking activity")

            // Verify activities have property_id field
            activities.forEach { activity ->
                val propertyId = activity.jsonObject["property_id"]?.jsonPrimitive?.content
                assertTrue(propertyId != null, "Activity should have property_id")
            }
        }

    @Test
    fun `Full crew sees booking activities for granted property`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            testDBConnection.grantCrewAccess(
                userId = TEST_ACCOUNT_2_USER_3.id,
                propertyId = TEST_ACCOUNT_1_PROPERTY_1.id,
                accessLevel = "manager",
            )

            val response =
                client.get("/v1/activities?page=0&size=20") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_2_USER_3.token}")
                }

            assertEquals(HttpStatusCode.OK, response.status)
            val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            val activities = json["activities"]?.jsonArray ?: error("Missing 'activities' field")

            assertTrue(activities.isNotEmpty(), "Full crew should see booking activities")
        }

    @Test
    fun `Limited crew sees booking activities but not tasks for granted property`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            testDBConnection.grantCrewAccess(
                userId = TEST_ACCOUNT_2_USER_3.id,
                propertyId = TEST_ACCOUNT_1_PROPERTY_1.id,
                accessLevel = "staff",
            )

            // Insert a task not assigned to the crew user (assigned to owner)
            testDBConnection.insertTask(
                propertyId = TEST_ACCOUNT_1_PROPERTY_1.id,
                scheduledDate =
                    LocalDateTime
                        .now()
                        .plusDays(2)
                        .withHour(10)
                        .withMinute(0),
                taskType = "cleaning",
                status = "pending",
                assignedTo = TEST_ACCOUNT_1_USER_1.id,
            )

            val response =
                client.get("/v1/activities?page=0&size=20") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_2_USER_3.token}")
                }

            assertEquals(HttpStatusCode.OK, response.status)
            val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            val activities = json["activities"]?.jsonArray ?: error("Missing 'activities' field")

            // Limited crew should see bookings
            val hasBooking =
                activities.any { activity ->
                    activity.jsonObject["type"]?.jsonPrimitive?.content == "booking"
                }
            assertTrue(hasBooking, "Limited crew should see booking activities")

            // Limited crew should NOT see unassigned tasks
            val hasUnassignedTask =
                activities.any { activity ->
                    activity.jsonObject["type"]?.jsonPrimitive?.content == "cleaning" &&
                        activity.jsonObject["task"] != null
                }
            assertTrue(!hasUnassignedTask, "Limited crew should not see unassigned tasks")
        }

    @Test
    fun `TaskOnly crew sees only assigned tasks`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            testDBConnection.grantCrewAccess(
                userId = TEST_ACCOUNT_2_USER_3.id,
                propertyId = TEST_ACCOUNT_1_PROPERTY_1.id,
                accessLevel = "contractor",
            )

            // Insert a task assigned to the crew user
            testDBConnection.insertTask(
                propertyId = TEST_ACCOUNT_1_PROPERTY_1.id,
                scheduledDate =
                    LocalDateTime
                        .now()
                        .plusDays(2)
                        .withHour(10)
                        .withMinute(0),
                taskType = "cleaning",
                status = "pending",
                assignedTo = TEST_ACCOUNT_2_USER_3.id,
            )

            val response =
                client.get("/v1/activities?page=0&size=20") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_2_USER_3.token}")
                }

            assertEquals(HttpStatusCode.OK, response.status)
            val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            val activities = json["activities"]?.jsonArray ?: error("Missing 'activities' field")

            // TaskOnly should see their assigned task
            val hasCleaning =
                activities.any { activity ->
                    activity.jsonObject["type"]?.jsonPrimitive?.content == "cleaning"
                }
            assertTrue(hasCleaning, "TaskOnly crew should see their assigned cleaning task")

            // TaskOnly should NOT see bookings
            val hasBooking =
                activities.any { activity ->
                    activity.jsonObject["type"]?.jsonPrimitive?.content == "booking"
                }
            assertTrue(!hasBooking, "TaskOnly crew should not see booking activities")
        }

    @Test
    fun `Owner with no bookings sees empty activities`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            // Use account 2 user who has no bookings
            val response =
                client.get("/v1/activities?page=0&size=20") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_2_USER_3.token}")
                }

            assertEquals(HttpStatusCode.OK, response.status)
            val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            val activities = json["activities"]?.jsonArray ?: error("Missing 'activities' field")

            assertTrue(activities.isEmpty(), "User with no bookings should see empty activities")
        }

    @Test
    fun `Timeline activities contain booking data for owners`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val response =
                client.get("/v1/activities?page=0&size=20") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                }

            assertEquals(HttpStatusCode.OK, response.status)
            val responseText = response.bodyAsText()

            // Activities should contain booking activities
            assertTrue(responseText.contains("\"booking\""))

            // Activities should have the guest first name
            assertTrue(responseText.contains("Timeline"))
        }

    @Test
    fun `Unauthenticated request to activities returns 401`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val response = client.get("/v1/activities")

            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }

    @Test
    fun `Activities response includes pagination fields`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val response =
                client.get("/v1/activities?page=0&size=10") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                }

            assertEquals(HttpStatusCode.OK, response.status)
            val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject

            assertTrue(json.containsKey("activities"), "Response should have 'activities' field")
            assertTrue(json.containsKey("page"), "Response should have 'page' field")
            assertTrue(json.containsKey("size"), "Response should have 'size' field")
            assertTrue(json.containsKey("total"), "Response should have 'total' field")

            assertEquals(0, json["page"]?.jsonPrimitive?.content?.toInt())
            assertEquals(10, json["size"]?.jsonPrimitive?.content?.toInt())
        }

    // ============================================================
    // Revoked crew must not keep seeing tasks still assigned to them
    // ============================================================

    private fun insertTaskAssignedToCrew(): String =
        testDBConnection
            .insertTask(
                propertyId = TEST_ACCOUNT_1_PROPERTY_1.id,
                scheduledDate =
                    LocalDateTime
                        .now()
                        .plusDays(2)
                        .withHour(10)
                        .withMinute(0),
                taskType = "cleaning",
                status = "pending",
                assignedTo = TEST_ACCOUNT_2_USER_3.id,
                notes = "Revoked crew task notes",
            ).toString()

    private suspend fun HttpClient.activitiesFor(
        token: String,
        query: String = "",
    ): JsonObject {
        val response =
            get("/v1/activities?page=0&size=20$query") {
                header(HttpHeaders.Authorization, "Bearer $token")
            }
        assertEquals(HttpStatusCode.OK, response.status)
        return Json.parseToJsonElement(response.bodyAsText()).jsonObject
    }

    private suspend fun HttpClient.timelineActivitiesFor(token: String): List<JsonObject> {
        val response =
            get("/v1/timeline") {
                header(HttpHeaders.Authorization, "Bearer $token")
            }
        assertEquals(HttpStatusCode.OK, response.status)
        val timeline =
            Json.parseToJsonElement(response.bodyAsText()).jsonObject["timeline"]?.jsonArray
                ?: error("Missing 'timeline' field")
        return timeline.flatMap { day ->
            val activities = day.jsonObject["activities"]?.jsonArray.orEmpty()
            activities.map { it.jsonObject }
        }
    }

    private fun List<JsonObject>.taskIds(): Set<String> =
        mapNotNull { activity ->
            val task = activity["task"] as? JsonObject
            task?.get("id")?.jsonPrimitive?.content
        }.toSet()

    private suspend fun HttpClient.revokeCrew() {
        val response =
            delete("/v1/properties/${TEST_ACCOUNT_1_PROPERTY_1.id}/crew/${TEST_ACCOUNT_2_USER_3.id}") {
                header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
            }
        assertEquals(HttpStatusCode.NoContent, response.status)
    }

    @Test
    fun `Revoked contractor no longer sees still-assigned tasks in activities`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            testDBConnection.grantCrewAccess(
                userId = TEST_ACCOUNT_2_USER_3.id,
                propertyId = TEST_ACCOUNT_1_PROPERTY_1.id,
                accessLevel = "contractor",
                service = "cleaning",
            )
            val taskId = insertTaskAssignedToCrew()

            // Sanity: while the grant exists the contractor sees their task.
            val before =
                client.activitiesFor(TEST_ACCOUNT_2_USER_3.token, "&property_id=${TEST_ACCOUNT_1_PROPERTY_1.id}")
            assertTrue(
                taskId in before["activities"]!!.jsonArray.map { it.jsonObject }.taskIds(),
                "Current contractor should see their assigned task",
            )

            client.revokeCrew()

            // The task stays assigned to them, but access is gone.
            val filtered =
                client.activitiesFor(TEST_ACCOUNT_2_USER_3.token, "&property_id=${TEST_ACCOUNT_1_PROPERTY_1.id}")
            assertTrue(
                filtered["activities"]!!.jsonArray.isEmpty(),
                "Revoked crew must not see any activity for the property: $filtered",
            )
            assertEquals(0, filtered["total"]?.jsonPrimitive?.content?.toInt())

            val unfiltered = client.activitiesFor(TEST_ACCOUNT_2_USER_3.token)
            assertTrue(
                unfiltered["activities"]!!.jsonArray.isEmpty(),
                "Revoked crew must not see any activity: $unfiltered",
            )
            assertEquals(0, unfiltered["total"]?.jsonPrimitive?.content?.toInt())
        }

    @Test
    fun `Revoked contractor no longer sees still-assigned tasks in timeline`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            testDBConnection.grantCrewAccess(
                userId = TEST_ACCOUNT_2_USER_3.id,
                propertyId = TEST_ACCOUNT_1_PROPERTY_1.id,
                accessLevel = "contractor",
                service = "cleaning",
            )
            val taskId = insertTaskAssignedToCrew()

            // Sanity: while the grant exists the contractor sees their task.
            assertTrue(
                taskId in client.timelineActivitiesFor(TEST_ACCOUNT_2_USER_3.token).taskIds(),
                "Current contractor should see their assigned task on the timeline",
            )

            client.revokeCrew()

            val after = client.timelineActivitiesFor(TEST_ACCOUNT_2_USER_3.token)
            assertTrue(after.isEmpty(), "Revoked crew must not see any timeline activity: $after")
        }

    @Test
    fun `Owner still sees a task assigned to a revoked crew member`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            testDBConnection.grantCrewAccess(
                userId = TEST_ACCOUNT_2_USER_3.id,
                propertyId = TEST_ACCOUNT_1_PROPERTY_1.id,
                accessLevel = "contractor",
                service = "cleaning",
            )
            val taskId = insertTaskAssignedToCrew()
            client.revokeCrew()

            val activities =
                client
                    .activitiesFor(TEST_ACCOUNT_1_USER_1.token, "&property_id=${TEST_ACCOUNT_1_PROPERTY_1.id}")["activities"]!!
                    .jsonArray
                    .map { it.jsonObject }
            assertTrue(taskId in activities.taskIds(), "Owner should still see the task in activities")
            assertTrue(
                activities.any { it["type"]?.jsonPrimitive?.content == "booking" },
                "Owner should still see booking activities",
            )

            val timeline = client.timelineActivitiesFor(TEST_ACCOUNT_1_USER_1.token)
            assertTrue(taskId in timeline.taskIds(), "Owner should still see the task on the timeline")
        }

    @Test
    fun `Staff crew still sees their own assigned task in activities`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            testDBConnection.grantCrewAccess(
                userId = TEST_ACCOUNT_2_USER_3.id,
                propertyId = TEST_ACCOUNT_1_PROPERTY_1.id,
                accessLevel = "staff",
                service = "cleaning",
            )
            val taskId = insertTaskAssignedToCrew()

            val activities =
                client
                    .activitiesFor(TEST_ACCOUNT_2_USER_3.token)["activities"]!!
                    .jsonArray
                    .map { it.jsonObject }
            assertTrue(taskId in activities.taskIds(), "Staff should see their assigned task")
            assertTrue(
                activities.any { it["type"]?.jsonPrimitive?.content == "booking" },
                "Staff should still see booking activities",
            )
        }
}
