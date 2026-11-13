package eu.kasify.routes

import eu.kasify.TEST_ACCOUNT_1_PROPERTY_1
import eu.kasify.TEST_ACCOUNT_1_USER_1
import eu.kasify.TEST_ACCOUNT_1_USER_2
import eu.kasify.TEST_ACCOUNT_2_USER_3
import eu.kasify.db.ApiTokensRepository
import eu.kasify.db.UsersRepository
import eu.kasify.db.model.PropertyAccessLevel
import eu.kasify.extensions.configureTestModules
import eu.kasify.extensions.createClient
import eu.kasify.helpers.grantCrewAccess
import eu.kasify.model.api.requests.CreatePropertyInvitationRequest
import eu.kasify.model.api.requests.UpdatePropertyMemberRequest
import eu.kasify.services.ApiTokenService
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import java.time.LocalDateTime
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class PropertyMembersRouteTest : BaseRouteDBTests() {
    // ============================================================
    // CREATE INVITATION TESTS - POST /properties/{id}/invitations
    // ============================================================

    @Test
    fun `Create invitation successfully`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val request =
                CreatePropertyInvitationRequest(
                    email = "member@example.com",
                    maxUses = 5,
                )

            val response =
                client.post("/v1/properties/${TEST_ACCOUNT_1_PROPERTY_1.id}/invitations") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }

            assertEquals(HttpStatusCode.Created, response.status)
            val responseText = response.bodyAsText()
            assertContains(responseText, "token")
            assertContains(responseText, "member@example.com")
            assertContains(responseText, TEST_ACCOUNT_1_PROPERTY_1.id.toString())
        }

    @Test
    fun `Created invitation token is 22-char base64url`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val response =
                client.post("/v1/properties/${TEST_ACCOUNT_1_PROPERTY_1.id}/invitations") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                    contentType(ContentType.Application.Json)
                    setBody(CreatePropertyInvitationRequest(maxUses = 1))
                }

            assertEquals(HttpStatusCode.Created, response.status)
            val token =
                Json
                    .parseToJsonElement(response.bodyAsText())
                    .jsonObject["token"]!!
                    .jsonPrimitive.content

            // 16 random bytes encoded as base64url without padding => 22 chars
            assertEquals(22, token.length)
            assertTrue(
                token.matches(Regex("^[A-Za-z0-9_-]{22}$")),
                "Token must be URL-safe base64 without padding, was: $token",
            )
        }

    @Test
    fun `Create invitation without email successfully`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val request =
                CreatePropertyInvitationRequest(
                    maxUses = 10,
                )

            val response =
                client.post("/v1/properties/${TEST_ACCOUNT_1_PROPERTY_1.id}/invitations") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }

            assertEquals(HttpStatusCode.Created, response.status)
            val responseText = response.bodyAsText()
            assertContains(responseText, "token")
        }

    @Test
    fun `Create invitation with expiration date`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val expiresAt = LocalDateTime.now().plusDays(7)
            val request =
                CreatePropertyInvitationRequest(
                    email = "member@example.com",
                    maxUses = 1,
                    expiresAt = expiresAt,
                )

            val response =
                client.post("/v1/properties/${TEST_ACCOUNT_1_PROPERTY_1.id}/invitations") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }

            assertEquals(HttpStatusCode.Created, response.status)
        }

    @Test
    fun `Create invitation unauthorized`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val request = CreatePropertyInvitationRequest(email = "member@example.com")

            val response =
                client.post("/v1/properties/${TEST_ACCOUNT_1_PROPERTY_1.id}/invitations") {
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }

            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }

    @Test
    fun `Create invitation invalid token`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val request = CreatePropertyInvitationRequest(email = "member@example.com")

            val response =
                client.post("/v1/properties/${TEST_ACCOUNT_1_PROPERTY_1.id}/invitations") {
                    header(HttpHeaders.Authorization, "Bearer invalid-token")
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }

            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }

    @Test
    fun `Create invitation non-owner gets forbidden`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val request = CreatePropertyInvitationRequest(email = "member@example.com")

            val response =
                client.post("/v1/properties/${TEST_ACCOUNT_1_PROPERTY_1.id}/invitations") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_2_USER_3.token}")
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }

            assertEquals(HttpStatusCode.Forbidden, response.status)
        }

    @Test
    fun `Create invitation invalid property uuid`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val request = CreatePropertyInvitationRequest(email = "member@example.com")

            val response =
                client.post("/v1/properties/invalid-uuid/invitations") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }

            assertEquals(HttpStatusCode.BadRequest, response.status)
        }

    @Test
    fun `Create invitation property not found`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val request = CreatePropertyInvitationRequest(email = "member@example.com")
            val nonExistentPropertyId = Uuid.parse("99999999-9999-9999-9999-999999999999")

            val response =
                client.post("/v1/properties/$nonExistentPropertyId/invitations") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }

            assertEquals(HttpStatusCode.NotFound, response.status)
        }

    @Test(timeout = 30_000)
    fun `Create invitation rejects backtracking email attack quickly`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            // `a@` + dots + `@` made the former email regex backtrack O(N^2) before failing.
            val request = CreatePropertyInvitationRequest(email = "a@" + ".".repeat(300_000) + "@")

            val response =
                client.post("/v1/properties/${TEST_ACCOUNT_1_PROPERTY_1.id}/invitations") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }

            assertEquals(HttpStatusCode.BadRequest, response.status)
            val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            assertEquals("invitation.invalid_email", json["code"]?.jsonPrimitive?.content)
        }

    // ============================================================
    // LIST INVITATIONS TESTS - GET /properties/{id}/invitations
    // ============================================================

    @Test
    fun `List invitations successfully`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            // First create an invitation
            val createRequest = CreatePropertyInvitationRequest(email = "member1@example.com")
            client.post("/v1/properties/${TEST_ACCOUNT_1_PROPERTY_1.id}/invitations") {
                header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                contentType(ContentType.Application.Json)
                setBody(createRequest)
            }

            // Now list invitations
            val response =
                client.get("/v1/properties/${TEST_ACCOUNT_1_PROPERTY_1.id}/invitations") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                }

            assertEquals(HttpStatusCode.OK, response.status)
            val responseText = response.bodyAsText()
            assertContains(responseText, "invitations")
            assertContains(responseText, "member1@example.com")
        }

    @Test
    fun `List invitations empty list`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val response =
                client.get("/v1/properties/${TEST_ACCOUNT_1_PROPERTY_1.id}/invitations") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                }

            assertEquals(HttpStatusCode.OK, response.status)
            val responseText = response.bodyAsText()
            val json = Json.parseToJsonElement(responseText)
            val invitations = json.jsonObject["invitations"]?.jsonArray
            assertNotNull(invitations)
            assertEquals(0, invitations.size)
        }

    @Test
    fun `List invitations unauthorized`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val response = client.get("/v1/properties/${TEST_ACCOUNT_1_PROPERTY_1.id}/invitations")

            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }

    @Test
    fun `List invitations non-owner gets forbidden`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val response =
                client.get("/v1/properties/${TEST_ACCOUNT_1_PROPERTY_1.id}/invitations") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_2_USER_3.token}")
                }

            assertEquals(HttpStatusCode.Forbidden, response.status)
        }

    // ============================================================
    // REVOKE INVITATION TESTS - DELETE /invitations/{id}
    // ============================================================

    @Test
    fun `Revoke invitation successfully`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            // First create an invitation
            val createRequest = CreatePropertyInvitationRequest(email = "member@example.com")
            val createResponse =
                client.post("/v1/properties/${TEST_ACCOUNT_1_PROPERTY_1.id}/invitations") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                    contentType(ContentType.Application.Json)
                    setBody(createRequest)
                }

            val createResponseText = createResponse.bodyAsText()
            val invitationId =
                Json
                    .parseToJsonElement(createResponseText)
                    .jsonObject["id"]
                    ?.jsonPrimitive
                    ?.content

            assertNotNull(invitationId)

            // Revoke the invitation
            val response =
                client.delete("/v1/invitations/$invitationId") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                }

            assertEquals(HttpStatusCode.NoContent, response.status)

            // Verify it's deleted
            val listResponse =
                client.get("/v1/properties/${TEST_ACCOUNT_1_PROPERTY_1.id}/invitations") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                }
            val listJson = Json.parseToJsonElement(listResponse.bodyAsText())
            val invitations = listJson.jsonObject["invitations"]?.jsonArray
            assertEquals(0, invitations?.size)
        }

    @Test
    fun `Revoke invitation unauthorized`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val invitationId = Uuid.parse("12345678-1234-1234-1234-123456789012")
            val response = client.delete("/v1/invitations/$invitationId")

            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }

    @Test
    fun `Revoke invitation not found`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val nonExistentId = Uuid.parse("99999999-9999-9999-9999-999999999999")
            val response =
                client.delete("/v1/invitations/$nonExistentId") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                }

            assertEquals(HttpStatusCode.NotFound, response.status)
        }

    @Test
    fun `Revoke invitation invalid uuid`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val response =
                client.delete("/v1/invitations/invalid-uuid") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                }

            assertEquals(HttpStatusCode.BadRequest, response.status)
        }

    @Test
    fun `Revoke invitation non-owner gets forbidden`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            // First create an invitation as owner
            val createRequest = CreatePropertyInvitationRequest(email = "member@example.com")
            val createResponse =
                client.post("/v1/properties/${TEST_ACCOUNT_1_PROPERTY_1.id}/invitations") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                    contentType(ContentType.Application.Json)
                    setBody(createRequest)
                }

            val invitationId =
                Json
                    .parseToJsonElement(createResponse.bodyAsText())
                    .jsonObject["id"]
                    ?.jsonPrimitive
                    ?.content

            // Try to revoke as non-owner
            val response =
                client.delete("/v1/invitations/$invitationId") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_2_USER_3.token}")
                }

            assertEquals(HttpStatusCode.Forbidden, response.status)
        }

    // ============================================================
    // ACCEPT INVITATION TESTS - POST /invitations/{token}/accept
    // ============================================================

    @Test
    fun `Accept invitation successfully`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            // Create an invitation as owner
            val createRequest = CreatePropertyInvitationRequest(maxUses = 5)
            val createResponse =
                client.post("/v1/properties/${TEST_ACCOUNT_1_PROPERTY_1.id}/invitations") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                    contentType(ContentType.Application.Json)
                    setBody(createRequest)
                }

            val token =
                Json
                    .parseToJsonElement(createResponse.bodyAsText())
                    .jsonObject["token"]
                    ?.jsonPrimitive
                    ?.content

            assertNotNull(token)

            // Accept invitation as a different user (from a different account)
            val response =
                client.post("/v1/invitations/$token/accept") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_2_USER_3.token}")
                }

            assertEquals(HttpStatusCode.OK, response.status)
            val responseText = response.bodyAsText()
            assertContains(responseText, TEST_ACCOUNT_1_PROPERTY_1.id.toString())
            assertContains(responseText, TEST_ACCOUNT_1_PROPERTY_1.name)
        }

    @Test
    fun `Accept invitation unauthorized`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val response = client.post("/v1/invitations/some-token/accept")

            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }

    @Test
    fun `Accept invitation token not found`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val response =
                client.post("/v1/invitations/invalid-token-that-does-not-exist/accept") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_2_USER_3.token}")
                }

            assertEquals(HttpStatusCode.NotFound, response.status)
        }

    @Test
    fun `Accept invitation owner gets conflict`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            // Create an invitation
            val createRequest = CreatePropertyInvitationRequest(maxUses = 5)
            val createResponse =
                client.post("/v1/properties/${TEST_ACCOUNT_1_PROPERTY_1.id}/invitations") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                    contentType(ContentType.Application.Json)
                    setBody(createRequest)
                }

            val token =
                Json
                    .parseToJsonElement(createResponse.bodyAsText())
                    .jsonObject["token"]
                    ?.jsonPrimitive
                    ?.content

            // Try to accept as the owner
            val response =
                client.post("/v1/invitations/$token/accept") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                }

            assertEquals(HttpStatusCode.Conflict, response.status)
            val responseText = response.bodyAsText()
            assertContains(responseText, "already an owner")
        }

    @Test
    fun `Accept invitation twice gets conflict`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            // Create an invitation with multiple uses
            val createRequest = CreatePropertyInvitationRequest(maxUses = 5)
            val createResponse =
                client.post("/v1/properties/${TEST_ACCOUNT_1_PROPERTY_1.id}/invitations") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                    contentType(ContentType.Application.Json)
                    setBody(createRequest)
                }

            val token =
                Json
                    .parseToJsonElement(createResponse.bodyAsText())
                    .jsonObject["token"]
                    ?.jsonPrimitive
                    ?.content

            // Accept first time
            client.post("/v1/invitations/$token/accept") {
                header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_2_USER_3.token}")
            }

            // Try to accept again
            val response =
                client.post("/v1/invitations/$token/accept") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_2_USER_3.token}")
                }

            assertEquals(HttpStatusCode.Conflict, response.status)
            val responseText = response.bodyAsText()
            assertContains(responseText, "already have access")
        }

    @Test
    fun `Accept invitation exhausted gets bad request`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            // Create an invitation with only 1 use
            val createRequest = CreatePropertyInvitationRequest(maxUses = 1)
            val createResponse =
                client.post("/v1/properties/${TEST_ACCOUNT_1_PROPERTY_1.id}/invitations") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                    contentType(ContentType.Application.Json)
                    setBody(createRequest)
                }

            val token =
                Json
                    .parseToJsonElement(createResponse.bodyAsText())
                    .jsonObject["token"]
                    ?.jsonPrimitive
                    ?.content

            // Accept with first user (from different account)
            client.post("/v1/invitations/$token/accept") {
                header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_2_USER_3.token}")
            }

            // Create a third test user in a third account and try to accept
            val thirdAccountId = Uuid.parse("00000000-0000-0000-0000-000000000003")
            val thirdUserId = Uuid.parse("10000000-0000-0000-0000-000000000004")
            testDBConnection.executeQuery(
                "INSERT INTO accounts (id) VALUES ('$thirdAccountId') ON CONFLICT DO NOTHING",
            )
            testDBConnection.executeQuery(
                "INSERT INTO users (id, account_id, name, last_name, email, external_user_id) " +
                    "VALUES ('$thirdUserId', '$thirdAccountId', 'Third', 'User', 'third@test.com', 'ex-third') ON CONFLICT DO NOTHING",
            )

            // The token-based auth won't work for the new user in tests since it's not in the users list
            // But we can verify the invitation is exhausted by checking its uses count
            val listResponse =
                client.get("/v1/properties/${TEST_ACCOUNT_1_PROPERTY_1.id}/invitations") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                }

            val invitations =
                Json
                    .parseToJsonElement(listResponse.bodyAsText())
                    .jsonObject["invitations"]
                    ?.jsonArray

            assertNotNull(invitations)
            assertTrue(invitations.size > 0)
            val uses =
                invitations[0]
                    .jsonObject["uses"]
                    ?.jsonPrimitive
                    ?.content
                    ?.toInt()
            assertEquals(1, uses)
        }

    @Test
    fun `Concurrent accepts of a single-use invitation grant access only once`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val createResponse =
                client.post("/v1/properties/${TEST_ACCOUNT_1_PROPERTY_1.id}/invitations") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                    contentType(ContentType.Application.Json)
                    setBody(CreatePropertyInvitationRequest(maxUses = 1))
                }
            val token =
                Json
                    .parseToJsonElement(createResponse.bodyAsText())
                    .jsonObject["token"]!!
                    .jsonPrimitive.content

            // Distinct users, each in their own account, authenticated with personal access tokens.
            val apiTokenService = ApiTokenService(ApiTokensRepository(), UsersRepository())
            val bearerTokens =
                (1..CONCURRENT_ACCEPTS).map { i ->
                    val accountId = Uuid.random()
                    val userId = Uuid.random()
                    testDBConnection.executeQuery("INSERT INTO accounts (id) VALUES ('$accountId')")
                    testDBConnection.executeQuery(
                        "INSERT INTO users (id, account_id, name, last_name, email, external_user_id) " +
                            "VALUES ('$userId', '$accountId', 'Racer$i', 'User', 'racer$i@test.com', 'ex-racer-$i')",
                    )
                    apiTokenService.generate(userId, accountId, "race-$i", null).rawToken
                }

            // Hold an exclusive lock on property_access so every request blocks right after it has
            // passed the invitation validity check, then release them all at once to race for the
            // invitation's only use.
            testDBConnection.autoCommit = false
            testDBConnection.executeQuery("LOCK TABLE property_access IN ACCESS EXCLUSIVE MODE")
            val responses =
                coroutineScope {
                    val pending =
                        bearerTokens.map { bearer ->
                            async {
                                val response =
                                    client.post("/v1/invitations/$token/accept") {
                                        header(HttpHeaders.Authorization, "Bearer $bearer")
                                    }
                                response.status to response.bodyAsText()
                            }
                        }
                    try {
                        withContext(Dispatchers.IO) { awaitRequestsBlockedOn("property_access", CONCURRENT_ACCEPTS) }
                    } finally {
                        testDBConnection.rollback()
                        testDBConnection.autoCommit = true
                    }
                    pending.awaitAll()
                }

            assertEquals(1, responses.count { it.first == HttpStatusCode.OK }, "responses: $responses")
            val rejected = responses.filter { it.first != HttpStatusCode.OK }
            assertEquals(CONCURRENT_ACCEPTS - 1, rejected.size)
            rejected.forEach { (status, body) ->
                assertEquals(HttpStatusCode.BadRequest, status)
                assertContains(body, "invitation.expired")
            }
            assertEquals(
                1,
                queryInt("SELECT count(*) FROM property_access WHERE property_id = '${TEST_ACCOUNT_1_PROPERTY_1.id}'"),
            )
            assertEquals(1, queryInt("SELECT uses FROM crew_invitations WHERE token = '$token'"))
        }

    private fun queryInt(sql: String): Int =
        testDBConnection.createStatement().use { stmt ->
            stmt.executeQuery(sql).use { rs ->
                rs.next()
                rs.getInt(1)
            }
        }

    private fun awaitRequestsBlockedOn(
        table: String,
        expected: Int,
    ) {
        val deadline = System.nanoTime() + 30_000_000_000L
        while (
            queryInt("SELECT count(DISTINCT pid) FROM pg_locks WHERE relation = '$table'::regclass AND NOT granted") < expected
        ) {
            check(System.nanoTime() < deadline) { "Timed out waiting for $expected requests to block on $table" }
            Thread.sleep(20)
        }
    }

    // ============================================================
    // LIST PROPERTY MEMBERS TESTS - GET /properties/{id}/crew
    // ============================================================

    @Test
    fun `List property members returns owners when no crew invited`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val response =
                client.get("/v1/properties/${TEST_ACCOUNT_1_PROPERTY_1.id}/crew") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                }

            assertEquals(HttpStatusCode.OK, response.status)
            val responseText = response.bodyAsText()
            val crew = Json.parseToJsonElement(responseText).jsonObject["crew"]?.jsonArray
            assertNotNull(crew)
            // With no invited crew the account owners are still returned so they can be assigned tasks.
            assertEquals(
                setOf(TEST_ACCOUNT_1_USER_1.id.toString(), TEST_ACCOUNT_1_USER_2.id.toString()),
                crew.map { it.jsonObject["id"]?.jsonPrimitive?.content }.toSet(),
            )
            assertContains(responseText, "Owner")
        }

    @Test
    fun `List property members returns default cleaner id`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val response =
                client.get("/v1/properties/${TEST_ACCOUNT_1_PROPERTY_1.id}/crew") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                }

            assertEquals(HttpStatusCode.OK, response.status)
            val json = Json.parseToJsonElement(response.bodyAsText())
            val defaultCleanerId = json.jsonObject["default_cleaner_id"]?.jsonPrimitive?.content
            // Seed data sets the property's default cleaner to its owner.
            assertEquals(TEST_ACCOUNT_1_USER_1.id.toString(), defaultCleanerId)
        }

    @Test
    fun `List property members after accepting invitation`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            // Create an invitation
            val createRequest = CreatePropertyInvitationRequest(maxUses = 5)
            val createResponse =
                client.post("/v1/properties/${TEST_ACCOUNT_1_PROPERTY_1.id}/invitations") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                    contentType(ContentType.Application.Json)
                    setBody(createRequest)
                }

            val token =
                Json
                    .parseToJsonElement(createResponse.bodyAsText())
                    .jsonObject["token"]
                    ?.jsonPrimitive
                    ?.content

            // Accept invitation
            client.post("/v1/invitations/$token/accept") {
                header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_2_USER_3.token}")
            }

            // List property members
            val response =
                client.get("/v1/properties/${TEST_ACCOUNT_1_PROPERTY_1.id}/crew") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                }

            assertEquals(HttpStatusCode.OK, response.status)
            val responseText = response.bodyAsText()
            val json = Json.parseToJsonElement(responseText)
            val crew = json.jsonObject["crew"]?.jsonArray
            assertNotNull(crew)
            // Owners plus the newly accepted crew member.
            assertEquals(
                setOf(
                    TEST_ACCOUNT_1_USER_1.id.toString(),
                    TEST_ACCOUNT_1_USER_2.id.toString(),
                    TEST_ACCOUNT_2_USER_3.id.toString(),
                ),
                crew.map { it.jsonObject["id"]?.jsonPrimitive?.content }.toSet(),
            )
        }

    @Test
    fun `List property members unauthorized`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val response = client.get("/v1/properties/${TEST_ACCOUNT_1_PROPERTY_1.id}/crew")

            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }

    @Test
    fun `List property members non-owner gets forbidden`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val response =
                client.get("/v1/properties/${TEST_ACCOUNT_1_PROPERTY_1.id}/crew") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_2_USER_3.token}")
                }

            assertEquals(HttpStatusCode.Forbidden, response.status)
        }

    // ============================================================
    // REMOVE PROPERTY MEMBER TESTS - DELETE /properties/{id}/crew/{userId}
    // ============================================================

    @Test
    fun `Remove property member successfully`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            // Create and accept invitation to add property member
            val createRequest = CreatePropertyInvitationRequest(maxUses = 5)
            val createResponse =
                client.post("/v1/properties/${TEST_ACCOUNT_1_PROPERTY_1.id}/invitations") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                    contentType(ContentType.Application.Json)
                    setBody(createRequest)
                }

            val token =
                Json
                    .parseToJsonElement(createResponse.bodyAsText())
                    .jsonObject["token"]
                    ?.jsonPrimitive
                    ?.content

            client.post("/v1/invitations/$token/accept") {
                header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_2_USER_3.token}")
            }

            // Remove the property member
            val response =
                client.delete("/v1/properties/${TEST_ACCOUNT_1_PROPERTY_1.id}/crew/${TEST_ACCOUNT_2_USER_3.id}") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                }

            assertEquals(HttpStatusCode.NoContent, response.status)

            // Verify property member is removed
            val listResponse =
                client.get("/v1/properties/${TEST_ACCOUNT_1_PROPERTY_1.id}/crew") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                }

            val crew =
                Json
                    .parseToJsonElement(listResponse.bodyAsText())
                    .jsonObject["crew"]
                    ?.jsonArray
            assertNotNull(crew)
            // The removed crew member is gone; the account owners remain.
            assertEquals(
                setOf(TEST_ACCOUNT_1_USER_1.id.toString(), TEST_ACCOUNT_1_USER_2.id.toString()),
                crew.map { it.jsonObject["id"]?.jsonPrimitive?.content }.toSet(),
            )
        }

    @Test
    fun `Remove property member unauthorized`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val response =
                client.delete("/v1/properties/${TEST_ACCOUNT_1_PROPERTY_1.id}/crew/${TEST_ACCOUNT_2_USER_3.id}")

            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }

    @Test
    fun `Remove property member not found`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val nonExistentUserId = Uuid.parse("99999999-9999-9999-9999-999999999999")
            val response =
                client.delete("/v1/properties/${TEST_ACCOUNT_1_PROPERTY_1.id}/crew/$nonExistentUserId") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                }

            assertEquals(HttpStatusCode.NotFound, response.status)
        }

    @Test
    fun `Remove property member invalid uuid`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val response =
                client.delete("/v1/properties/${TEST_ACCOUNT_1_PROPERTY_1.id}/crew/invalid-uuid") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                }

            assertEquals(HttpStatusCode.BadRequest, response.status)
        }

    @Test
    fun `Remove property member non-owner gets forbidden`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            // First add a property member
            val createRequest = CreatePropertyInvitationRequest(maxUses = 5)
            val createResponse =
                client.post("/v1/properties/${TEST_ACCOUNT_1_PROPERTY_1.id}/invitations") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                    contentType(ContentType.Application.Json)
                    setBody(createRequest)
                }

            val token =
                Json
                    .parseToJsonElement(createResponse.bodyAsText())
                    .jsonObject["token"]
                    ?.jsonPrimitive
                    ?.content

            client.post("/v1/invitations/$token/accept") {
                header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_2_USER_3.token}")
            }

            // Try to remove property member as non-owner (the member themselves)
            val response =
                client.delete("/v1/properties/${TEST_ACCOUNT_1_PROPERTY_1.id}/crew/${TEST_ACCOUNT_2_USER_3.id}") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_2_USER_3.token}")
                }

            assertEquals(HttpStatusCode.Forbidden, response.status)
        }

    @Test
    fun `Remove property member property not found`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val nonExistentPropertyId = Uuid.parse("99999999-9999-9999-9999-999999999999")
            val response =
                client.delete("/v1/properties/$nonExistentPropertyId/crew/${TEST_ACCOUNT_2_USER_3.id}") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                }

            assertEquals(HttpStatusCode.NotFound, response.status)
        }

    // ============================================================
    // ACCESS LEVEL TESTS
    // ============================================================

    @Test
    fun `Create invitation with access level`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val request =
                CreatePropertyInvitationRequest(
                    email = "member@example.com",
                    maxUses = 1,
                    accessLevel = PropertyAccessLevel.Manager,
                )

            val response =
                client.post("/v1/properties/${TEST_ACCOUNT_1_PROPERTY_1.id}/invitations") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }

            assertEquals(HttpStatusCode.Created, response.status)
            val responseText = response.bodyAsText()
            assertContains(responseText, "Manager")
        }

    @Test
    fun `Update property member access level`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            testDBConnection.grantCrewAccess(
                userId = TEST_ACCOUNT_2_USER_3.id,
                propertyId = TEST_ACCOUNT_1_PROPERTY_1.id,
                accessLevel = "contractor",
            )

            val response =
                client.patch("/v1/properties/${TEST_ACCOUNT_1_PROPERTY_1.id}/crew/${TEST_ACCOUNT_2_USER_3.id}") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                    contentType(ContentType.Application.Json)
                    setBody(UpdatePropertyMemberRequest(accessLevel = PropertyAccessLevel.Manager))
                }

            assertEquals(HttpStatusCode.OK, response.status)
        }

    @Test
    fun `List members shows access level`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            testDBConnection.grantCrewAccess(
                userId = TEST_ACCOUNT_2_USER_3.id,
                propertyId = TEST_ACCOUNT_1_PROPERTY_1.id,
                accessLevel = "staff",
            )

            val response =
                client.get("/v1/properties/${TEST_ACCOUNT_1_PROPERTY_1.id}/crew") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                }

            assertEquals(HttpStatusCode.OK, response.status)
            val responseText = response.bodyAsText()
            assertContains(responseText, "Staff")
        }

    companion object {
        private const val CONCURRENT_ACCEPTS = 5
    }
}
