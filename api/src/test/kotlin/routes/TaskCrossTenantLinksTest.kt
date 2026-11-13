package eu.kasify.routes
import eu.kasify.TEST_ACCOUNT_1_PROPERTY_1
import eu.kasify.TEST_ACCOUNT_1_USER_1
import eu.kasify.TEST_ACCOUNT_1_USER_2
import eu.kasify.TEST_ACCOUNT_2_USER_3
import eu.kasify.TEST_ACCOUNT_ID_2
import eu.kasify.TEST_SPAIN_COUNTRY_ID
import eu.kasify.db.TasksRepository
import eu.kasify.extensions.configureTestModules
import eu.kasify.extensions.createClient
import eu.kasify.helpers.grantCrewAccess
import eu.kasify.helpers.insertBooking
import eu.kasify.helpers.insertTask
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.uuid.Uuid

/**
 * Tenant isolation of the ids a task (or property) links to:
 * - `booking_id` on POST /tasks must be a booking of the task's own property, and a task row that
 *   links another property's booking is never surfaced as (or cancelled with) that booking's task.
 * - `assigned_to` on POST/PATCH /tasks and `default_cleaner_id` on PUT/PATCH /properties must be an
 *   owner or crew member of the property; values the property/task already holds are accepted
 *   unchanged.
 *
 * Account 1 (USER_1, USER_2) owns PROPERTY_1; account 2 (USER_3) owns [victimPropertyId].
 */
class TaskCrossTenantLinksTest : BaseRouteDBTests() {
    private val victimPropertyId = Uuid.parse("30000000-0000-0000-0000-0000000000b2")
    private val victimBookingId = Uuid.parse("50000000-0000-0000-0000-0000000000b2")
    private val ownBookingId = Uuid.parse("50000000-0000-0000-0000-0000000000a1")
    private val ownTaskId = Uuid.parse("60000000-0000-0000-0000-0000000000a1")
    private val madridProvinceId = Uuid.parse("c1000000-0000-0000-0000-000000000001")
    private val madridCityId = Uuid.parse("c2000000-0000-0000-0000-000000000001")

    private val victimName = "Victimname"
    private val victimLastName = "Victimlastname"

    override fun setupData() {
        testDBConnection.executeQuery(
            "UPDATE users SET name = '$victimName', last_name = '$victimLastName' " +
                "WHERE id = '${TEST_ACCOUNT_2_USER_3.id}'",
        )
        testDBConnection.insertProperty(
            id = victimPropertyId,
            accountId = TEST_ACCOUNT_ID_2,
            name = "Victim property",
        )
        testDBConnection.insertBooking(
            bookingId = victimBookingId,
            propertyId = victimPropertyId,
            from =
                LocalDateTime
                    .now()
                    .plusDays(10)
                    .withHour(17)
                    .withMinute(0),
            to =
                LocalDateTime
                    .now()
                    .plusDays(12)
                    .withHour(11)
                    .withMinute(0),
        )
        testDBConnection.insertBooking(
            bookingId = ownBookingId,
            propertyId = TEST_ACCOUNT_1_PROPERTY_1.id,
            from =
                LocalDateTime
                    .now()
                    .plusDays(10)
                    .withHour(17)
                    .withMinute(0),
            to =
                LocalDateTime
                    .now()
                    .plusDays(12)
                    .withHour(11)
                    .withMinute(0),
        )
        // A task on PROPERTY_1 assigned to its owner, used by the PATCH tests.
        testDBConnection.insertTask(
            taskId = ownTaskId,
            propertyId = TEST_ACCOUNT_1_PROPERTY_1.id,
            taskType = "maintenance",
            assignedTo = TEST_ACCOUNT_1_USER_1.id,
            scheduledDate =
                LocalDateTime
                    .now()
                    .plusDays(1)
                    .withHour(10)
                    .withMinute(0),
            notes = "original notes",
        )
        // Geo rows so PUT /properties can resolve a Spanish province/city.
        testDBConnection.executeQuery(
            "INSERT INTO provinces (id, country_id, name, code_iso_3166_2, ine_code_pro) VALUES " +
                "('$madridProvinceId', '$TEST_SPAIN_COUNTRY_ID', 'Madrid', 'ES-M', '28')",
        )
        testDBConnection.executeQuery(
            "INSERT INTO cities (id, country_id, province_id, name, ine_code, ine_code_mun) VALUES " +
                "('$madridCityId', '$TEST_SPAIN_COUNTRY_ID', '$madridProvinceId', 'Madrid', '28079', '079')",
        )
    }

    // ------------------------------------------------------------------ helpers

    private fun queryUuid(sql: String): Uuid? =
        testDBConnection.createStatement().use { stmt ->
            stmt.executeQuery(sql).use { rs ->
                if (rs.next()) rs.getString(1)?.let { Uuid.parse(it) } else null
            }
        }

    private fun queryString(sql: String): String? =
        testDBConnection.createStatement().use { stmt ->
            stmt.executeQuery(sql).use { rs -> if (rs.next()) rs.getString(1) else null }
        }

    private fun queryCount(sql: String): Int =
        testDBConnection.createStatement().use { stmt ->
            stmt.executeQuery(sql).use { rs ->
                rs.next()
                rs.getInt(1)
            }
        }

    private fun taskCount(): Int = queryCount("SELECT count(*) FROM tasks")

    private fun taskAssignee(taskId: Uuid): Uuid? = queryUuid("SELECT assigned_to FROM tasks WHERE id = '$taskId'")

    private fun defaultCleaner(propertyId: Uuid): Uuid? = queryUuid("SELECT default_cleaner_id FROM properties WHERE id = '$propertyId'")

    private fun setDefaultCleaner(
        propertyId: Uuid,
        userId: Uuid,
    ) = testDBConnection.executeQuery(
        "UPDATE properties SET default_cleaner_id = '$userId' WHERE id = '$propertyId'",
    )

    private suspend fun json(response: HttpResponse): JsonObject = Json.parseToJsonElement(response.bodyAsText()).jsonObject

    private suspend fun createTask(
        client: HttpClient,
        bookingId: Uuid? = null,
        assignedTo: Uuid? = null,
    ): HttpResponse {
        val extra =
            listOfNotNull(
                bookingId?.let { "\"booking_id\": \"$it\"" },
                assignedTo?.let { "\"assigned_to\": \"$it\"" },
            ).joinToString("") { ", $it" }
        return client.post("/v1/tasks") {
            header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
            contentType(ContentType.Application.Json)
            setBody(
                """{"property_id": "${TEST_ACCOUNT_1_PROPERTY_1.id}", "task_type": "cleaning", """ +
                    """"from": "2030-01-01T10:00:00.000Z", "to": "2030-01-01T12:00:00.000Z", """ +
                    """"notes": "attacker notes"$extra}""",
            )
        }
    }

    private suspend fun patchTask(
        client: HttpClient,
        taskId: Uuid,
        body: String,
    ): HttpResponse =
        client.patch("/v1/tasks/$taskId") {
            header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
            contentType(ContentType.Application.Json)
            setBody(body)
        }

    private suspend fun patchProperty(
        client: HttpClient,
        body: String,
    ): HttpResponse =
        client.patch("/v1/properties/${TEST_ACCOUNT_1_PROPERTY_1.id}") {
            header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
            contentType(ContentType.Application.Json)
            setBody(body)
        }

    private suspend fun putProperty(
        client: HttpClient,
        defaultCleanerId: Uuid,
    ): HttpResponse =
        client.put("/v1/properties/${TEST_ACCOUNT_1_PROPERTY_1.id}") {
            header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
            contentType(ContentType.Application.Json)
            setBody(
                """{"name": "Renamed", "address": "Calle Mayor 1", "zip_code": "28001", """ +
                    """"province_id": "$madridProvinceId", "city_id": "$madridCityId", """ +
                    """"default_cleaner_id": "$defaultCleanerId"}""",
            )
        }

    private fun assertNoVictimNameLeak(body: String) {
        assertFalse(body.contains(victimName), "Response must not echo the user's name: $body")
        assertFalse(body.contains("assigned_to_name"), "Response must not carry assigned_to_name: $body")
    }

    // ------------------------------------------------------------------ booking_id on POST /tasks

    @Test
    fun `POST tasks rejects a booking_id of another account's property`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()
            val before = taskCount()

            val response = createTask(client, bookingId = victimBookingId)

            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertEquals("task.booking_not_in_property", json(response)["code"]?.jsonPrimitive?.content)
            assertEquals(before, taskCount(), "No task must be stored")
        }

    @Test
    fun `POST tasks rejects an unknown booking_id with the same code`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()
            val before = taskCount()

            val response = createTask(client, bookingId = Uuid.random())

            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertEquals("task.booking_not_in_property", json(response)["code"]?.jsonPrimitive?.content)
            assertEquals(before, taskCount(), "No task must be stored")
        }

    @Test
    fun `POST tasks accepts a booking_id of the same property`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val response = createTask(client, bookingId = ownBookingId)

            assertEquals(HttpStatusCode.Created, response.status, response.bodyAsText())
            assertEquals(ownBookingId.toString(), json(response)["booking_id"]?.jsonPrimitive?.content)
        }

    @Test
    fun `POST tasks without booking_id still works`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val response = createTask(client)

            assertEquals(HttpStatusCode.Created, response.status, response.bodyAsText())
            // Falls back to the property's default cleaner (the owner).
            assertEquals(TEST_ACCOUNT_1_USER_1.id.toString(), json(response)["assigned_to"]?.jsonPrimitive?.content)
        }

    // ------------------------------------------------------------------ pre-existing cross-tenant task rows

    @Test
    fun `cross-tenant task linking the victim's booking is not the booking's cleaning_task`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()
            testDBConnection.insertTask(
                propertyId = TEST_ACCOUNT_1_PROPERTY_1.id,
                bookingId = victimBookingId,
                taskType = "cleaning",
                assignedTo = TEST_ACCOUNT_1_USER_1.id,
                scheduledDate =
                    LocalDateTime
                        .now()
                        .plusDays(12)
                        .withHour(12)
                        .withMinute(0),
                notes = "attacker notes",
            )

            val response =
                client.get("/v1/bookings/$victimBookingId") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_2_USER_3.token}")
                }

            assertEquals(HttpStatusCode.OK, response.status)
            val body = json(response)
            assertNull(body["cleaning_task"], "Another property's task must not appear as cleaning_task: $body")
        }

    @Test
    fun `cross-tenant task does not displace the victim's own cleaning_task`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()
            testDBConnection.insertTask(
                propertyId = TEST_ACCOUNT_1_PROPERTY_1.id,
                bookingId = victimBookingId,
                taskType = "cleaning",
                assignedTo = TEST_ACCOUNT_1_USER_1.id,
                scheduledDate =
                    LocalDateTime
                        .now()
                        .plusDays(12)
                        .withHour(12)
                        .withMinute(0),
                notes = "attacker notes",
            )
            val victimTaskId =
                testDBConnection.insertTask(
                    propertyId = victimPropertyId,
                    bookingId = victimBookingId,
                    taskType = "cleaning",
                    assignedTo = TEST_ACCOUNT_2_USER_3.id,
                    scheduledDate =
                        LocalDateTime
                            .now()
                            .plusDays(12)
                            .withHour(12)
                            .withMinute(0),
                    notes = "victim notes",
                )

            val response =
                client.get("/v1/bookings/$victimBookingId") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_2_USER_3.token}")
                }

            assertEquals(HttpStatusCode.OK, response.status)
            val cleaningTask = json(response)["cleaning_task"]?.jsonObject
            assertNotNull(cleaningTask, "The victim's own cleaning task must be returned")
            assertEquals(victimTaskId.toString(), cleaningTask["id"]?.jsonPrimitive?.content)
        }

    @Test
    fun `cancelTasksForBooking leaves tasks of other properties untouched`() {
        val attackerTaskId =
            testDBConnection.insertTask(
                propertyId = TEST_ACCOUNT_1_PROPERTY_1.id,
                bookingId = victimBookingId,
                taskType = "cleaning",
                assignedTo = TEST_ACCOUNT_1_USER_1.id,
                scheduledDate =
                    LocalDateTime
                        .now()
                        .plusDays(12)
                        .withHour(12)
                        .withMinute(0),
            )
        val victimTaskId =
            testDBConnection.insertTask(
                propertyId = victimPropertyId,
                bookingId = victimBookingId,
                taskType = "cleaning",
                assignedTo = TEST_ACCOUNT_2_USER_3.id,
                scheduledDate =
                    LocalDateTime
                        .now()
                        .plusDays(12)
                        .withHour(12)
                        .withMinute(0),
            )

        val cancelled = runBlocking { TasksRepository().cancelTasksForBooking(victimBookingId) }

        assertEquals(1, cancelled)
        assertNull(queryString("SELECT cancelled_at FROM tasks WHERE id = '$attackerTaskId'"))
        assertNotNull(queryString("SELECT cancelled_at FROM tasks WHERE id = '$victimTaskId'"))
    }

    // ------------------------------------------------------------------ assigned_to on POST /tasks

    @Test
    fun `POST tasks rejects an assignee who is not an owner or crew of the property`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()
            val before = taskCount()

            val response = createTask(client, assignedTo = TEST_ACCOUNT_2_USER_3.id)

            assertEquals(HttpStatusCode.BadRequest, response.status)
            val body = response.bodyAsText()
            assertEquals("task.assignee_not_in_property", json(response)["code"]?.jsonPrimitive?.content)
            assertNoVictimNameLeak(body)
            assertEquals(before, taskCount(), "No task must be stored")
            assertEquals(
                0,
                queryCount("SELECT count(*) FROM tasks WHERE assigned_to = '${TEST_ACCOUNT_2_USER_3.id}'"),
            )
        }

    @Test
    fun `POST tasks rejects an unknown assignee id`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val response = createTask(client, assignedTo = Uuid.random())

            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertEquals("task.assignee_not_in_property", json(response)["code"]?.jsonPrimitive?.content)
        }

    @Test
    fun `POST tasks accepts a crew member as assignee`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()
            testDBConnection.grantCrewAccess(
                userId = TEST_ACCOUNT_2_USER_3.id,
                propertyId = TEST_ACCOUNT_1_PROPERTY_1.id,
                service = "cleaning",
                accessLevel = "staff",
            )

            val response = createTask(client, assignedTo = TEST_ACCOUNT_2_USER_3.id)

            assertEquals(HttpStatusCode.Created, response.status, response.bodyAsText())
            assertEquals(TEST_ACCOUNT_2_USER_3.id.toString(), json(response)["assigned_to"]?.jsonPrimitive?.content)
        }

    @Test
    fun `POST tasks accepts another owner of the account as assignee`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val response = createTask(client, assignedTo = TEST_ACCOUNT_1_USER_2.id)

            assertEquals(HttpStatusCode.Created, response.status, response.bodyAsText())
            assertEquals(TEST_ACCOUNT_1_USER_2.id.toString(), json(response)["assigned_to"]?.jsonPrimitive?.content)
        }

    // ------------------------------------------------------------------ assigned_to on PATCH /tasks/{id}

    @Test
    fun `PATCH tasks rejects reassignment to a user outside the property and writes nothing`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val response =
                patchTask(
                    client,
                    ownTaskId,
                    """{"assigned_to": "${TEST_ACCOUNT_2_USER_3.id}", "notes": "changed"}""",
                )

            assertEquals(HttpStatusCode.BadRequest, response.status)
            val body = response.bodyAsText()
            assertEquals("task.assignee_not_in_property", json(response)["code"]?.jsonPrimitive?.content)
            assertNoVictimNameLeak(body)
            assertEquals(TEST_ACCOUNT_1_USER_1.id, taskAssignee(ownTaskId), "Assignee must be unchanged")
            assertEquals("original notes", queryString("SELECT notes FROM tasks WHERE id = '$ownTaskId'"))
        }

    @Test
    fun `PATCH tasks accepts reassignment to a crew member`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()
            testDBConnection.grantCrewAccess(
                userId = TEST_ACCOUNT_2_USER_3.id,
                propertyId = TEST_ACCOUNT_1_PROPERTY_1.id,
                accessLevel = "contractor",
            )

            val response = patchTask(client, ownTaskId, """{"assigned_to": "${TEST_ACCOUNT_2_USER_3.id}"}""")

            assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
            assertEquals(TEST_ACCOUNT_2_USER_3.id, taskAssignee(ownTaskId))
        }

    @Test
    fun `PATCH tasks accepts reassignment to another owner of the account`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val response = patchTask(client, ownTaskId, """{"assigned_to": "${TEST_ACCOUNT_1_USER_2.id}"}""")

            assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
            assertEquals(TEST_ACCOUNT_1_USER_2.id, taskAssignee(ownTaskId))
        }

    @Test
    fun `PATCH tasks accepts the property's stale default cleaner`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()
            // USER_3 is the property's default cleaner but holds no crew access (removed crew).
            setDefaultCleaner(TEST_ACCOUNT_1_PROPERTY_1.id, TEST_ACCOUNT_2_USER_3.id)

            val response = patchTask(client, ownTaskId, """{"assigned_to": "${TEST_ACCOUNT_2_USER_3.id}"}""")

            assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
            assertEquals(TEST_ACCOUNT_2_USER_3.id, taskAssignee(ownTaskId))
        }

    @Test
    fun `PATCH tasks accepts re-sending the task's current stale assignee`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()
            // Task assigned to USER_3 while they were crew; their access has since been removed.
            val staleTaskId =
                testDBConnection.insertTask(
                    propertyId = TEST_ACCOUNT_1_PROPERTY_1.id,
                    taskType = "cleaning",
                    assignedTo = TEST_ACCOUNT_2_USER_3.id,
                    scheduledDate =
                        LocalDateTime
                            .now()
                            .plusDays(2)
                            .withHour(10)
                            .withMinute(0),
                )

            val response =
                patchTask(
                    client,
                    staleTaskId,
                    """{"assigned_to": "${TEST_ACCOUNT_2_USER_3.id}", "notes": "updated"}""",
                )

            assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
            assertEquals("updated", json(response)["notes"]?.jsonPrimitive?.content)
        }

    // ------------------------------------------------------------------ default_cleaner_id on PUT/PATCH /properties

    @Test
    fun `PATCH property rejects a default cleaner outside the property`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val response = patchProperty(client, """{"default_cleaner_id": "${TEST_ACCOUNT_2_USER_3.id}"}""")

            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertEquals(
                "property.default_cleaner_not_in_property",
                json(response)["code"]?.jsonPrimitive?.content,
            )
            assertEquals(TEST_ACCOUNT_1_USER_1.id, defaultCleaner(TEST_ACCOUNT_1_PROPERTY_1.id))
        }

    @Test
    fun `PUT property rejects a default cleaner outside the property`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val response = putProperty(client, TEST_ACCOUNT_2_USER_3.id)

            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertEquals(
                "property.default_cleaner_not_in_property",
                json(response)["code"]?.jsonPrimitive?.content,
            )
            assertEquals(TEST_ACCOUNT_1_USER_1.id, defaultCleaner(TEST_ACCOUNT_1_PROPERTY_1.id))
            assertEquals(
                TEST_ACCOUNT_1_PROPERTY_1.name,
                queryString("SELECT name FROM properties WHERE id = '${TEST_ACCOUNT_1_PROPERTY_1.id}'"),
            )
        }

    @Test
    fun `PATCH property accepts a crew member or another owner as default cleaner`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()
            testDBConnection.grantCrewAccess(
                userId = TEST_ACCOUNT_2_USER_3.id,
                propertyId = TEST_ACCOUNT_1_PROPERTY_1.id,
                service = "cleaning",
                accessLevel = "contractor",
            )

            val crewResponse = patchProperty(client, """{"default_cleaner_id": "${TEST_ACCOUNT_2_USER_3.id}"}""")
            assertEquals(HttpStatusCode.OK, crewResponse.status, crewResponse.bodyAsText())
            assertEquals(TEST_ACCOUNT_2_USER_3.id, defaultCleaner(TEST_ACCOUNT_1_PROPERTY_1.id))

            val ownerResponse = patchProperty(client, """{"default_cleaner_id": "${TEST_ACCOUNT_1_USER_2.id}"}""")
            assertEquals(HttpStatusCode.OK, ownerResponse.status, ownerResponse.bodyAsText())
            assertEquals(TEST_ACCOUNT_1_USER_2.id, defaultCleaner(TEST_ACCOUNT_1_PROPERTY_1.id))
        }

    @Test
    fun `PATCH property echoing the current stale default cleaner still works`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()
            setDefaultCleaner(TEST_ACCOUNT_1_PROPERTY_1.id, TEST_ACCOUNT_2_USER_3.id)

            val response =
                patchProperty(
                    client,
                    """{"name": "Renamed", "default_cleaner_id": "${TEST_ACCOUNT_2_USER_3.id}"}""",
                )

            assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
            assertEquals(
                "Renamed",
                queryString("SELECT name FROM properties WHERE id = '${TEST_ACCOUNT_1_PROPERTY_1.id}'"),
            )
        }

    @Test
    fun `PUT property echoing the current stale default cleaner still works`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()
            setDefaultCleaner(TEST_ACCOUNT_1_PROPERTY_1.id, TEST_ACCOUNT_2_USER_3.id)

            val response = putProperty(client, TEST_ACCOUNT_2_USER_3.id)

            assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
            assertEquals(
                "Renamed",
                queryString("SELECT name FROM properties WHERE id = '${TEST_ACCOUNT_1_PROPERTY_1.id}'"),
            )
        }
}
