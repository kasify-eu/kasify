package eu.kasify.routes
import eu.kasify.TEST_ACCOUNT_1_PROPERTY_1
import eu.kasify.TEST_ACCOUNT_1_USER_1
import eu.kasify.TEST_ACCOUNT_2_USER_3
import eu.kasify.extensions.configureTestModules
import eu.kasify.extensions.createClient
import eu.kasify.helpers.grantCrewAccess
import eu.kasify.helpers.insertBooking
import eu.kasify.helpers.insertTask
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
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid

/**
 * Tests for role-based access control on task endpoints.
 * Verifies that:
 * - TaskOnly crew can see their assigned tasks and gets booking_context on cleaning tasks
 * - Full/Limited crew can see tasks without booking_context
 * - Only owners can create/update/delete tasks
 * - Crew can start/complete tasks assigned to them
 */
class TasksAccessControlTest : BaseRouteDBTests() {
    private val testBookingId = Uuid.parse("50000000-0000-0000-0000-000000000001")
    private val testTaskId = Uuid.parse("60000000-0000-0000-0000-000000000001")
    private val testTaskId2 = Uuid.parse("60000000-0000-0000-0000-000000000002")
    private val unassignedTaskId = Uuid.parse("60000000-0000-0000-0000-000000000002")

    override fun setupData() {
        // Insert a booking that checks out today (for cleaning task context)
        testDBConnection.insertBooking(
            bookingId = testBookingId,
            propertyId = TEST_ACCOUNT_1_PROPERTY_1.id,
            from =
                LocalDateTime
                    .now()
                    .minusDays(3)
                    .withHour(17)
                    .withMinute(0),
            to = LocalDateTime.now().withHour(11).withMinute(0),
            guestFirstName = "Departing",
            guestLastName = "Guest",
            adultsCount = 2,
            childrenCount = 1,
        )

        testDBConnection.insertBooking(
            bookingId = testTaskId2,
            propertyId = TEST_ACCOUNT_1_PROPERTY_1.id,
            from =
                LocalDateTime
                    .now()
                    .plusDays(3)
                    .withHour(17)
                    .withMinute(0),
            to =
                LocalDateTime
                    .now()
                    .plusDays(7)
                    .withHour(11)
                    .withMinute(0),
            guestFirstName = "Departing",
            guestLastName = "Guest",
            adultsCount = 2,
            childrenCount = 1,
        )

        // Insert a cleaning task assigned to crew user (account 2 user 3)
        testDBConnection.insertTask(
            taskId = testTaskId,
            propertyId = TEST_ACCOUNT_1_PROPERTY_1.id,
            bookingId = testBookingId,
            taskType = "cleaning",
            status = "pending",
            assignedTo = TEST_ACCOUNT_2_USER_3.id,
            scheduledDate = LocalDateTime.now().withHour(12).withMinute(0),
            notes = "Clean after checkout",
        )

        // Insert a task not assigned to the crew user (assigned to owner instead)
        testDBConnection.insertTask(
            taskId = unassignedTaskId,
            propertyId = TEST_ACCOUNT_1_PROPERTY_1.id,
            taskType = "maintenance",
            status = "pending",
            assignedTo = TEST_ACCOUNT_1_USER_1.id,
            scheduledDate =
                LocalDateTime
                    .now()
                    .plusDays(1)
                    .withHour(10)
                    .withMinute(0),
            notes = "Fix bathroom faucet",
        )
    }

    // ============================================================
    // GET TASK DETAIL - Access Control Tests
    // ============================================================

    @Test
    fun `Owner sees task detail without booking_context`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val response =
                client.get("/v1/tasks/$testTaskId") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                }

            assertEquals(HttpStatusCode.OK, response.status)
            val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject

            // Owner gets task properties at root level, no booking_context
            assertNotNull(json["id"], "Response should have id field")
            assertNull(json["booking_context"], "Owner should not get booking_context")
        }

    @Test
    fun `TaskOnly crew sees cleaning task with next_booking`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            testDBConnection.grantCrewAccess(
                userId = TEST_ACCOUNT_2_USER_3.id,
                propertyId = TEST_ACCOUNT_1_PROPERTY_1.id,
                accessLevel = "contractor",
            )

            val response =
                client.get("/v1/tasks/$testTaskId") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_2_USER_3.token}")
                }

            assertEquals(HttpStatusCode.OK, response.status)
            val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject

            // Task properties at root level, cleaning task includes next_booking
            assertNotNull(json["id"], "Response should have id field")
            assertFalse(json.containsKey("booking_context"), "Response should not have booking_context field")
            assertTrue(
                json.containsKey("next_booking"),
                "Cleaning task response should include next_booking",
            )

            // Regression pin: MANAGER-visibility fields must be stripped from the embedded booking
            val nextBooking = json["next_booking"]?.jsonObject ?: error("next_booking should be a JSON object")
            assertNull(nextBooking["checkin_code"], "checkin_code must not be visible to contractor in next_booking")
            assertNull(nextBooking["channel_id"], "channel_id must not be visible to contractor in next_booking")
        }

    @Test
    fun `Full access crew sees task without booking_context`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            testDBConnection.grantCrewAccess(
                userId = TEST_ACCOUNT_2_USER_3.id,
                propertyId = TEST_ACCOUNT_1_PROPERTY_1.id,
                accessLevel = "manager",
            )

            val response =
                client.get("/v1/tasks/$testTaskId") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_2_USER_3.token}")
                }

            assertEquals(HttpStatusCode.OK, response.status)
            val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject

            assertNotNull(json["id"], "Response should have id field")
            assertNull(json["booking_context"], "Full crew should not get booking_context")
        }

    @Test
    fun `Limited access crew sees task without booking_context`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            testDBConnection.grantCrewAccess(
                userId = TEST_ACCOUNT_2_USER_3.id,
                propertyId = TEST_ACCOUNT_1_PROPERTY_1.id,
                accessLevel = "staff",
            )

            val response =
                client.get("/v1/tasks/$testTaskId") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_2_USER_3.token}")
                }

            assertEquals(HttpStatusCode.OK, response.status)
            val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject

            assertNotNull(json["id"], "Response should have id field")
            assertNull(json["booking_context"], "Limited crew should not get booking_context")
        }

    @Test
    fun `Crew without property access gets 403 on task detail`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            // No crew access granted
            val response =
                client.get("/v1/tasks/$testTaskId") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_2_USER_3.token}")
                }

            assertEquals(HttpStatusCode.Forbidden, response.status)
        }

    @Test
    fun `Owner sees all tasks including unassigned via tasks endpoint`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val response =
                client.get("/v1/properties/${TEST_ACCOUNT_1_PROPERTY_1.id}/tasks") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                }

            assertEquals(HttpStatusCode.OK, response.status)
            val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            val tasks = json["tasks"]?.jsonArray ?: error("Missing 'tasks' field")

            // Owner should see both tasks (assigned cleaning + unassigned maintenance)
            assertEquals(2, tasks.size, "Owner should see all property tasks")
        }

    @Test
    fun `Crew can list property tasks they have access to`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            testDBConnection.grantCrewAccess(
                userId = TEST_ACCOUNT_2_USER_3.id,
                propertyId = TEST_ACCOUNT_1_PROPERTY_1.id,
                accessLevel = "contractor",
            )

            val response =
                client.get("/v1/properties/${TEST_ACCOUNT_1_PROPERTY_1.id}/tasks") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_2_USER_3.token}")
                }

            // Crew with property access can list tasks (requireAccess, not requireOwner)
            assertEquals(HttpStatusCode.OK, response.status)
            val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            val tasks = json["tasks"]?.jsonArray ?: error("Missing 'tasks' field")
            assertTrue(tasks.size > 0, "Crew should see property tasks")
        }

    @Test
    fun `Staff listing property tasks gets assigned task field-filtered and foreign task stripped`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            testDBConnection.grantCrewAccess(
                userId = TEST_ACCOUNT_2_USER_3.id,
                propertyId = TEST_ACCOUNT_1_PROPERTY_1.id,
                accessLevel = "staff",
            )

            val response =
                client.get("/v1/properties/${TEST_ACCOUNT_1_PROPERTY_1.id}/tasks") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_2_USER_3.token}")
                }

            assertEquals(HttpStatusCode.OK, response.status)
            val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            val tasks = (json["tasks"]?.jsonArray ?: error("Missing 'tasks' field")).map { it.jsonObject }
            assertEquals(2, tasks.size, "Staff should see both property tasks")

            val assigned = tasks.first { it["id"]?.jsonPrimitive?.content == testTaskId.toString() }
            val foreign = tasks.first { it["id"]?.jsonPrimitive?.content == unassignedTaskId.toString() }

            // The owner-assigned (foreign) task is reduced to schedule + property only.
            assertEquals(
                setOf("id", "from", "to", "property"),
                foreign.keys,
                "Foreign task must be stripped in list. Saw: ${foreign.keys}",
            )

            // The staff member's own task is full, but owner-only fields are removed.
            assertNull(assigned["booking_id"], "Staff must not see owner-only booking_id")
            assertNull(assigned["created_at"], "Staff must not see owner-only created_at")
            assertNull(assigned["cancelled_at"], "Staff must not see owner-only cancelled_at")
            // public_token stays visible so crew can resend the task to whoever they work with.
            assertNotNull(assigned["public_token"], "public_token must remain visible to crew")
            assertEquals("Clean after checkout", assigned["notes"]?.jsonPrimitive?.content)
        }

    @Test
    fun `Contractor listing via tasks endpoint strips foreign task and field-filters assigned`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            testDBConnection.grantCrewAccess(
                userId = TEST_ACCOUNT_2_USER_3.id,
                propertyId = TEST_ACCOUNT_1_PROPERTY_1.id,
                accessLevel = "contractor",
            )

            val response =
                client.get("/v1/tasks?property_id=${TEST_ACCOUNT_1_PROPERTY_1.id}") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_2_USER_3.token}")
                }

            assertEquals(HttpStatusCode.OK, response.status)
            val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            val tasks = (json["tasks"]?.jsonArray ?: error("Missing 'tasks' field")).map { it.jsonObject }

            val assigned = tasks.first { it["id"]?.jsonPrimitive?.content == testTaskId.toString() }
            val foreign = tasks.first { it["id"]?.jsonPrimitive?.content == unassignedTaskId.toString() }

            assertEquals(
                setOf("id", "from", "to", "property"),
                foreign.keys,
                "Foreign task must be stripped on GET /tasks. Saw: ${foreign.keys}",
            )
            assertNull(assigned["booking_id"], "Contractor must not see owner-only booking_id")
            assertNull(assigned["created_at"], "Contractor must not see owner-only created_at")
            assertNotNull(assigned["public_token"], "public_token must remain visible to contractors")
        }

    @Test
    fun `Owner listing property tasks still sees owner-only fields`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val response =
                client.get("/v1/properties/${TEST_ACCOUNT_1_PROPERTY_1.id}/tasks") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                }

            assertEquals(HttpStatusCode.OK, response.status)
            val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            val tasks = (json["tasks"]?.jsonArray ?: error("Missing 'tasks' field")).map { it.jsonObject }

            val cleaning = tasks.first { it["id"]?.jsonPrimitive?.content == testTaskId.toString() }
            assertNotNull(cleaning["booking_id"], "Owner must still see booking_id")
            assertNotNull(cleaning["created_at"], "Owner must still see created_at")
            assertNotNull(cleaning["public_token"], "Owner must see public_token")
        }

    // ============================================================
    // TASK LIFECYCLE - Start/Complete
    // ============================================================

    @Test
    fun `Assigned crew can start their task`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            testDBConnection.grantCrewAccess(
                userId = TEST_ACCOUNT_2_USER_3.id,
                propertyId = TEST_ACCOUNT_1_PROPERTY_1.id,
                accessLevel = "contractor",
            )

            val response =
                client.post("/v1/tasks/$testTaskId/start") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_2_USER_3.token}")
                }

            assertEquals(HttpStatusCode.OK, response.status)
            val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            assertEquals("in_progress", json["status"]?.jsonPrimitive?.content)
        }

    @Test
    fun `Crew cannot start task not assigned to them`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            testDBConnection.grantCrewAccess(
                userId = TEST_ACCOUNT_2_USER_3.id,
                propertyId = TEST_ACCOUNT_1_PROPERTY_1.id,
                accessLevel = "contractor",
            )

            // Try to start the unassigned maintenance task
            val response =
                client.post("/v1/tasks/$unassignedTaskId/start") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_2_USER_3.token}")
                }

            // Should be rejected - not assigned to this crew member
            assertEquals(HttpStatusCode.BadRequest, response.status)
        }

    @Test
    fun `Owner can start any task`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val response =
                client.post("/v1/tasks/$unassignedTaskId/start") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                }

            assertEquals(HttpStatusCode.OK, response.status)
        }

    // ============================================================
    // TaskOnly crew on maintenance task (no booking_context)
    // ============================================================

    @Test
    fun `Contractor non-assignee gets stripped task on GET tasks id`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            testDBConnection.grantCrewAccess(
                userId = TEST_ACCOUNT_2_USER_3.id,
                propertyId = TEST_ACCOUNT_1_PROPERTY_1.id,
                accessLevel = "contractor",
            )

            // Maintenance task is assigned to the owner — foreign to this contractor.
            val response =
                client.get("/v1/tasks/$unassignedTaskId") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_2_USER_3.token}")
                }

            assertEquals(HttpStatusCode.OK, response.status)
            val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject

            assertEquals(
                setOf("id", "from", "to", "property"),
                json.keys,
                "Foreign task response must be stripped to id+from+to+property. Saw: ${json.keys}",
            )
            assertEquals(unassignedTaskId.toString(), json["id"]?.jsonPrimitive?.content)
            val property = json["property"]?.jsonObject ?: error("Stripped response should include property")
            assertEquals(TEST_ACCOUNT_1_PROPERTY_1.id.toString(), property["id"]?.jsonPrimitive?.content)
            assertEquals(TEST_ACCOUNT_1_PROPERTY_1.name, property["name"]?.jsonPrimitive?.content)
        }

    @Test
    fun `Staff non-assignee gets stripped task on GET tasks id`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            testDBConnection.grantCrewAccess(
                userId = TEST_ACCOUNT_2_USER_3.id,
                propertyId = TEST_ACCOUNT_1_PROPERTY_1.id,
                accessLevel = "staff",
            )

            val response =
                client.get("/v1/tasks/$unassignedTaskId") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_2_USER_3.token}")
                }

            assertEquals(HttpStatusCode.OK, response.status)
            val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject

            assertEquals(
                setOf("id", "from", "to", "property"),
                json.keys,
                "Foreign task response must be stripped to id+from+to+property. Saw: ${json.keys}",
            )
            // Confirm sensitive fields are absent.
            assertNull(json["notes"], "Stripped response must not expose notes")
            assertNull(json["assigned_to"], "Stripped response must not expose assigned_to")
            assertNull(json["assigned_to_name"], "Stripped response must not expose assigned_to_name")
            assertNull(json["task_type"], "Stripped response must not expose task_type")
            assertNull(json["status"], "Stripped response must not expose status")
        }

    @Test
    fun `Staff assignee gets full task on GET tasks id`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            // testTaskId is already assigned to USER_3 in setupData.
            testDBConnection.grantCrewAccess(
                userId = TEST_ACCOUNT_2_USER_3.id,
                propertyId = TEST_ACCOUNT_1_PROPERTY_1.id,
                accessLevel = "staff",
            )

            val response =
                client.get("/v1/tasks/$testTaskId") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_2_USER_3.token}")
                }

            assertEquals(HttpStatusCode.OK, response.status)
            val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject

            assertNotNull(json["task_type"], "Staff assignee should see task_type")
            assertNotNull(json["status"], "Staff assignee should see status")
            assertEquals(TEST_ACCOUNT_2_USER_3.id.toString(), json["assigned_to"]?.jsonPrimitive?.content)
            assertEquals("Clean after checkout", json["notes"]?.jsonPrimitive?.content)
        }

    @Test
    fun `Manager non-assignee gets full task on GET tasks id`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            testDBConnection.grantCrewAccess(
                userId = TEST_ACCOUNT_2_USER_3.id,
                propertyId = TEST_ACCOUNT_1_PROPERTY_1.id,
                accessLevel = "manager",
            )

            val response =
                client.get("/v1/tasks/$unassignedTaskId") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_2_USER_3.token}")
                }

            assertEquals(HttpStatusCode.OK, response.status)
            val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject

            assertEquals("maintenance", json["task_type"]?.jsonPrimitive?.content)
            assertEquals(
                TEST_ACCOUNT_1_USER_1.id.toString(),
                json["assigned_to"]?.jsonPrimitive?.content,
                "Manager should see foreign task's assigned_to",
            )
        }

    // ============================================================
    // PATCH /tasks/{id} - Assignee Guard
    // ============================================================

    @Test
    fun `PATCH tasks id returns 403 task not_assignee for non-assignee crew`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            testDBConnection.grantCrewAccess(
                userId = TEST_ACCOUNT_2_USER_3.id,
                propertyId = TEST_ACCOUNT_1_PROPERTY_1.id,
                accessLevel = "staff",
            )

            // Try to update job_report on a task assigned to the owner.
            val response =
                client.patch("/v1/tasks/$unassignedTaskId") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_2_USER_3.token}")
                    contentType(ContentType.Application.Json)
                    setBody("""{"job_report":"sneaky update"}""")
                }

            assertEquals(HttpStatusCode.Forbidden, response.status)
            val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            assertEquals("task.not_assignee", json["code"]?.jsonPrimitive?.content)
        }

    @Test
    fun `PATCH tasks id succeeds when crew is the assignee`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            // testTaskId is assigned to USER_3.
            testDBConnection.grantCrewAccess(
                userId = TEST_ACCOUNT_2_USER_3.id,
                propertyId = TEST_ACCOUNT_1_PROPERTY_1.id,
                accessLevel = "staff",
            )

            val response =
                client.patch("/v1/tasks/$testTaskId") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_2_USER_3.token}")
                    contentType(ContentType.Application.Json)
                    setBody("""{"job_report":"All clean."}""")
                }

            assertEquals(HttpStatusCode.OK, response.status)
            val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            assertEquals("All clean.", json["job_report"]?.jsonPrimitive?.content)
        }

    @Test
    fun `PATCH tasks id allows manager non-assignee to update job_report`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            testDBConnection.grantCrewAccess(
                userId = TEST_ACCOUNT_2_USER_3.id,
                propertyId = TEST_ACCOUNT_1_PROPERTY_1.id,
                accessLevel = "manager",
            )

            val response =
                client.patch("/v1/tasks/$unassignedTaskId") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_2_USER_3.token}")
                    contentType(ContentType.Application.Json)
                    setBody("""{"job_report":"Manager note."}""")
                }

            assertEquals(HttpStatusCode.OK, response.status)
            val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            assertEquals("Manager note.", json["job_report"]?.jsonPrimitive?.content)
        }

    // ============================================================
    // Public-token task route - Field-stripping regression
    // ============================================================

    @Test
    fun `Public token task route strips checkin_code and channel_id from embedded next_booking`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            // Retrieve the auto-generated public_token for the cleaning task from the DB
            val publicToken =
                testDBConnection
                    .prepareStatement("SELECT public_token FROM tasks WHERE id = ?")
                    .use { stmt ->
                        stmt.setObject(1, testTaskId.toJavaUuid())
                        stmt.executeQuery().use { rs ->
                            rs.next()
                            rs.getString("public_token")
                        }
                    }

            val response = client.get("/v1/public/tasks/$publicToken")

            assertEquals(HttpStatusCode.OK, response.status)
            val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject

            assertTrue(json.containsKey("next_booking"), "Public task route should include next_booking for cleaning task")

            // Regression pin: public route uses Contractor visibility — MANAGER-only fields must be absent
            val nextBooking = json["next_booking"]?.jsonObject ?: error("next_booking should be a JSON object")
            assertNull(nextBooking["checkin_code"], "checkin_code must not be exposed via public task route next_booking")
            assertNull(nextBooking["channel_id"], "channel_id must not be exposed via public task route next_booking")
        }
}
