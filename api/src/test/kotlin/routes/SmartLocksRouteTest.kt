package eu.kasify.routes
import eu.kasify.TEST_ACCOUNT_1_PROPERTY_1
import eu.kasify.TEST_ACCOUNT_1_USER_1
import eu.kasify.TEST_ACCOUNT_2_USER_3
import eu.kasify.TEST_ACCOUNT_ID_1
import eu.kasify.TEST_ACCOUNT_ID_2
import eu.kasify.extensions.TEST_ENCRYPTION_KEY
import eu.kasify.extensions.configureTestModules
import eu.kasify.helpers.grantCrewAccess
import eu.kasify.security.EncryptionService
import io.ktor.client.request.delete
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
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class SmartLocksRouteTest : BaseRouteDBTests() {
    private val testSmartlockId = Uuid.Companion.parse("40000000-0000-0000-0000-000000000001")
    private val nonExistentSmartlockId = Uuid.Companion.parse("40000000-0000-0000-0000-000000000999")
    private val testNukiSmartlockId = Uuid.Companion.parse("60000000-0000-0000-0000-000000000001")
    private val testNukiDeviceId = "123456789"
    private val testNukiIntegrationId = Uuid.Companion.parse("70000000-0000-0000-0000-000000000001")
    private val testNukiIntegrationId2 = Uuid.Companion.parse("70000000-0000-0000-0000-000000000002")

    // Second property for testing cross-property duplicates
    private val testAccount2Property = Uuid.Companion.parse("30000000-0000-0000-0000-000000000002")

    override fun setupData() {
        val encryptionService = EncryptionService(key = TEST_ENCRYPTION_KEY)
        val encryptedNukiToken = encryptionService.encrypt("test_token")
        val encryptedNukiToken2 = encryptionService.encrypt("test_token_2")

        // Insert second test property for account 2
        testDBConnection.insertProperty(testAccount2Property, TEST_ACCOUNT_ID_2, "Second Test Property")

        // Insert a test smartlock for the property
        testDBConnection.executeQuery(
            """
            INSERT INTO property_smartlocks (id, property_id, vendor, name, created_at, updated_at)
            VALUES ('$testSmartlockId', '${TEST_ACCOUNT_1_PROPERTY_1.id}', 'nuki', 'Test Door', NOW(), NOW())
            """.trimIndent(),
        )

        // Insert account integration for Nuki
        val sqlAccountIntegration =
            "INSERT INTO public.account_integrations " +
                "(id, account_id, vendor, created_at, updated_at) " +
                "VALUES(gen_random_uuid(), '${TEST_ACCOUNT_ID_1}', 'nuki', now(), now());"
        testDBConnection.executeQuery(sqlAccountIntegration)

        // Insert Nuki Config
        val sqlNukiConfig =
            "INSERT INTO public.account_nuki_integrations " +
                "(id, account_integration_id, nuki_token, created_at, updated_at) " +
                "VALUES('$testNukiIntegrationId', (SELECT id FROM account_integrations WHERE account_id = '${TEST_ACCOUNT_ID_1}' AND vendor = 'nuki' LIMIT 1), '$encryptedNukiToken', now(), now());"
        testDBConnection.executeQuery(sqlNukiConfig)

        // Insert Nuki smartlock details
        val sqlNukiSmartlock =
            "INSERT INTO public.property_nuki_smartlocks " +
                "(id, property_smartlock_id, nuki_integration_id, nuki_smartlock_id, created_at, updated_at) " +
                "VALUES('$testNukiSmartlockId', '$testSmartlockId', '$testNukiIntegrationId', '123456789', now(), now())"
        testDBConnection.executeQuery(sqlNukiSmartlock)

        // Insert account integration for Nuki (account 2)
        testDBConnection.executeQuery(
            "INSERT INTO public.account_integrations (id, account_id, vendor, created_at, updated_at) " +
                "VALUES(gen_random_uuid(), '${TEST_ACCOUNT_ID_2}', 'nuki', now(), now());",
        )
        testDBConnection.executeQuery(
            "INSERT INTO public.account_nuki_integrations (id, account_integration_id, nuki_token, created_at, updated_at) " +
                "VALUES('$testNukiIntegrationId2', (SELECT id FROM account_integrations WHERE account_id = '${TEST_ACCOUNT_ID_2}' AND vendor = 'nuki' LIMIT 1), '$encryptedNukiToken2', now(), now());",
        )
    }

    // GET /properties/{id}/smartlocks tests
    @Test
    fun testGetSmartLocksSuccess() =
        testApplication {
            application { configureTestModules() }

            val response =
                client.get("/v1/properties/${TEST_ACCOUNT_1_PROPERTY_1.id}/smartlocks") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                }

            assertEquals(HttpStatusCode.Companion.OK, response.status)
            val responseBody = response.bodyAsText()
            assertTrue(responseBody.contains("Test Door"))
            assertTrue(responseBody.contains(testNukiSmartlockId.toString()))
        }

    @Test
    fun testGetSmartLocksUnauthorized() =
        testApplication {
            application { configureTestModules() }

            val response = client.get("/v1/properties/${TEST_ACCOUNT_1_PROPERTY_1.id}/smartlocks")

            assertEquals(HttpStatusCode.Companion.Unauthorized, response.status)
        }

    @Test
    fun testGetSmartLocksInvalidToken() =
        testApplication {
            application { configureTestModules() }

            val response =
                client.get("/v1/properties/${TEST_ACCOUNT_1_PROPERTY_1.id}/smartlocks") {
                    header(HttpHeaders.Authorization, "Bearer invalid-token")
                }

            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }

    @Test
    fun testGetSmartLocksWrongAccount() =
        testApplication {
            application { configureTestModules() }

            val response =
                client.get("/v1/properties/${TEST_ACCOUNT_1_PROPERTY_1.id}/smartlocks") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_2_USER_3.token}")
                }

            assertEquals(HttpStatusCode.Companion.Forbidden, response.status)
        }

    @Test
    fun testGetSmartLocksPropertyNotFound() =
        testApplication {
            application { configureTestModules() }

            val nonExistentPropertyId = Uuid.Companion.parse("30000000-0000-0000-0000-000000000999")

            val response =
                client.get("/v1/properties/$nonExistentPropertyId/smartlocks") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                }

            assertEquals(HttpStatusCode.Companion.NotFound, response.status)
        }

    @Test
    fun testGetSmartLocksInvalidPropertyId() =
        testApplication {
            application { configureTestModules() }

            val response =
                client.get("/v1/properties/invalid-uuid/smartlocks") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                }

            assertEquals(HttpStatusCode.Companion.BadRequest, response.status)
        }

    // POST /properties/{id}/smartlocks tests

    @Test
    fun testAddSmartLockSuccess() =
        testApplication {
            application { configureTestModules() }

            val response =
                client.post("/v1/properties/${TEST_ACCOUNT_1_PROPERTY_1.id}/smartlocks") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                    contentType(ContentType.Application.Json)
                    setBody("""{"name": "New Nuki Smartlock", "nuki_smartlock_id": "987654321", "nuki_integration_id": "$testNukiIntegrationId"}""")
                }

            assertEquals(HttpStatusCode.Companion.Created, response.status)
            val responseBody = response.bodyAsText()
            assertTrue(responseBody.contains("New Nuki Smartlock"))
            assertTrue(responseBody.contains("987654321"))
        }

    @Test
    fun testAddSmartLockUnauthorized() =
        testApplication {
            application { configureTestModules() }

            val response =
                client.post("/v1/properties/${TEST_ACCOUNT_1_PROPERTY_1.id}/smartlocks") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"name": "New Nuki Smartlock", "nukiSmartlockId": "987654321"}""")
                }

            assertEquals(HttpStatusCode.Companion.Unauthorized, response.status)
        }

    @Test
    fun testAddSmartLockInvalidToken() =
        testApplication {
            application { configureTestModules() }

            val response =
                client.post("/v1/properties/${TEST_ACCOUNT_1_PROPERTY_1.id}/smartlocks") {
                    header(HttpHeaders.Authorization, "Bearer invalid-token")
                    contentType(ContentType.Application.Json)
                    setBody("""{"name": "New Nuki Smartlock", "nukiSmartlockId": "987654321"}""")
                }

            assertEquals(HttpStatusCode.Companion.Unauthorized, response.status)
        }

    @Test
    fun testAddSmartLockWrongAccount() =
        testApplication {
            application { configureTestModules() }

            val response =
                client.post("/v1/properties/${TEST_ACCOUNT_1_PROPERTY_1.id}/smartlocks") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_2_USER_3.token}")
                    contentType(ContentType.Application.Json)
                    setBody("""{"name": "New Nuki Smartlock", "nukiSmartlockId": "987654321"}""")
                }

            assertEquals(HttpStatusCode.Companion.Forbidden, response.status)
        }

    @Test
    fun testAddSmartLockStaffDenied() =
        testApplication {
            application { configureTestModules() }

            testDBConnection.grantCrewAccess(
                userId = TEST_ACCOUNT_2_USER_3.id,
                propertyId = TEST_ACCOUNT_1_PROPERTY_1.id,
                service = "cleaning",
                accessLevel = "staff",
            )

            val response =
                client.post("/v1/properties/${TEST_ACCOUNT_1_PROPERTY_1.id}/smartlocks") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_2_USER_3.token}")
                    contentType(ContentType.Application.Json)
                    setBody("""{"name": "New Nuki Smartlock", "nuki_smartlock_id": "987654321", "nuki_integration_id": "$testNukiIntegrationId2"}""")
                }

            assertEquals(HttpStatusCode.Companion.Forbidden, response.status)
        }

    @Test
    fun testAddSmartLockContractorDenied() =
        testApplication {
            application { configureTestModules() }

            testDBConnection.grantCrewAccess(
                userId = TEST_ACCOUNT_2_USER_3.id,
                propertyId = TEST_ACCOUNT_1_PROPERTY_1.id,
                service = "cleaning",
                accessLevel = "contractor",
            )

            val response =
                client.post("/v1/properties/${TEST_ACCOUNT_1_PROPERTY_1.id}/smartlocks") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_2_USER_3.token}")
                    contentType(ContentType.Application.Json)
                    setBody("""{"name": "New Nuki Smartlock", "nuki_smartlock_id": "987654321", "nuki_integration_id": "$testNukiIntegrationId2"}""")
                }

            assertEquals(HttpStatusCode.Companion.Forbidden, response.status)
        }

    @Test
    fun testAddSmartLockManagerAllowedUsesOwnerIntegration() =
        testApplication {
            application { configureTestModules() }

            testDBConnection.grantCrewAccess(
                userId = TEST_ACCOUNT_2_USER_3.id,
                propertyId = TEST_ACCOUNT_1_PROPERTY_1.id,
                accessLevel = "manager",
            )

            // The manager attaches the lock through the property owner's Nuki integration
            val response =
                client.post("/v1/properties/${TEST_ACCOUNT_1_PROPERTY_1.id}/smartlocks") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_2_USER_3.token}")
                    contentType(ContentType.Application.Json)
                    setBody("""{"name": "Manager Added Smartlock", "nuki_smartlock_id": "555555555", "nuki_integration_id": "$testNukiIntegrationId"}""")
                }

            assertEquals(HttpStatusCode.Companion.Created, response.status)
        }

    @Test
    fun testAddSmartLockManagerOwnAccountIntegrationRejected() =
        testApplication {
            application { configureTestModules() }

            testDBConnection.grantCrewAccess(
                userId = TEST_ACCOUNT_2_USER_3.id,
                propertyId = TEST_ACCOUNT_1_PROPERTY_1.id,
                accessLevel = "manager",
            )

            // An integration from the manager's own account is not valid for the owner's property
            val response =
                client.post("/v1/properties/${TEST_ACCOUNT_1_PROPERTY_1.id}/smartlocks") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_2_USER_3.token}")
                    contentType(ContentType.Application.Json)
                    setBody("""{"name": "Manager Added Smartlock", "nuki_smartlock_id": "555555555", "nuki_integration_id": "$testNukiIntegrationId2"}""")
                }

            assertEquals(HttpStatusCode.Companion.NotFound, response.status)
        }

    @Test
    fun testAddSmartLockManagerDuplicateInOwnerAccountRejected() =
        testApplication {
            application { configureTestModules() }

            testDBConnection.grantCrewAccess(
                userId = TEST_ACCOUNT_2_USER_3.id,
                propertyId = TEST_ACCOUNT_1_PROPERTY_1.id,
                accessLevel = "manager",
            )

            // The duplicate check runs against the property owner's account, so the manager
            // cannot re-attach a device the owner already assigned
            val response =
                client.post("/v1/properties/${TEST_ACCOUNT_1_PROPERTY_1.id}/smartlocks") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_2_USER_3.token}")
                    contentType(ContentType.Application.Json)
                    setBody("""{"name": "Duplicate Smartlock", "nuki_smartlock_id": "$testNukiDeviceId", "nuki_integration_id": "$testNukiIntegrationId"}""")
                }

            assertEquals(HttpStatusCode.Companion.Conflict, response.status)
        }

    @Test
    fun testAddSmartLockBlankName() =
        testApplication {
            application { configureTestModules() }

            val response =
                client.post("/v1/properties/${TEST_ACCOUNT_1_PROPERTY_1.id}/smartlocks") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                    contentType(ContentType.Application.Json)
                    setBody("""{"name": "", "nuki_smartlock_id": "987654321", "nuki_integration_id": "$testNukiIntegrationId"}""")
                }

            assertEquals(HttpStatusCode.Companion.BadRequest, response.status)
            assertContains(response.bodyAsText(), "Smartlock name must not be blank")
        }

    @Test
    fun testAddSmartLockNameTooLong() =
        testApplication {
            application { configureTestModules() }

            val longName = "a".repeat(101) // Exceeds SMARTLOCK_MAX_NAME_LENGTH of 100

            val response =
                client.post("/v1/properties/${TEST_ACCOUNT_1_PROPERTY_1.id}/smartlocks") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                    contentType(ContentType.Application.Json)
                    setBody("""{"name": "$longName", "nuki_smartlock_id": "987654321", "nuki_integration_id": "$testNukiIntegrationId"}""")
                }

            assertEquals(HttpStatusCode.Companion.BadRequest, response.status)
            assertContains(response.bodyAsText(), "Smartlock name must not exceed 100 characters")
        }

    @Test
    fun testAddSmartLockDuplicateNukiIdSameProperty() =
        testApplication {
            application { configureTestModules() }

            val response =
                client.post("/v1/properties/${TEST_ACCOUNT_1_PROPERTY_1.id}/smartlocks") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                    contentType(ContentType.Application.Json)
                    setBody("""{"name": "Duplicate Smartlock", "nuki_smartlock_id": "$testNukiDeviceId", "nuki_integration_id": "$testNukiIntegrationId"}""")
                }

            assertEquals(HttpStatusCode.Conflict, response.status)
            assertContains(response.bodyAsText(), "This smart lock is already assigned to a property")
        }

    @Test
    fun testAddSmartLockSameNukiIdDifferentProperty() =
        testApplication {
            application { configureTestModules() }

            // Add the same Nuki smartlock ID to a different property - should succeed
            val response =
                client.post("/v1/properties/$testAccount2Property/smartlocks") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_2_USER_3.token}")
                    contentType(ContentType.Application.Json)
                    setBody("""{"name": "Same Nuki ID Different Property", "nuki_smartlock_id": "$testNukiDeviceId", "nuki_integration_id": "$testNukiIntegrationId2"}""")
                }

            assertEquals(HttpStatusCode.Companion.Created, response.status)
            val responseBody = response.bodyAsText()
            assertTrue(responseBody.contains("Same Nuki ID Different Property"))
            assertTrue(responseBody.contains(testNukiDeviceId))
        }

    @Test
    fun testAddSmartLockPropertyNotFound() =
        testApplication {
            application { configureTestModules() }

            val nonExistentPropertyId = Uuid.Companion.parse("30000000-0000-0000-0000-000000000999")

            val response =
                client.post("/v1/properties/$nonExistentPropertyId/smartlocks") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                    contentType(ContentType.Application.Json)
                    setBody("""{"name": "New Nuki Smartlock", "nuki_smartlock_id": "987654321", "nuki_integration_id": "$testNukiIntegrationId"}""")
                }

            assertEquals(HttpStatusCode.Companion.NotFound, response.status)
        }

    // DELETE /properties/{id}/smartlocks/{doorId} tests

    @Test
    fun testDeleteSmartLockSuccess() =
        testApplication {
            application { configureTestModules() }

            val response =
                client.delete("/v1/properties/${TEST_ACCOUNT_1_PROPERTY_1.id}/smartlocks/$testSmartlockId") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                }

            assertEquals(HttpStatusCode.Companion.NoContent, response.status)
        }

    @Test
    fun testDeleteSmartLockUnauthorized() =
        testApplication {
            application { configureTestModules() }

            val response = client.delete("/v1/properties/${TEST_ACCOUNT_1_PROPERTY_1.id}/smartlocks/$testSmartlockId")

            assertEquals(HttpStatusCode.Companion.Unauthorized, response.status)
        }

    @Test
    fun testDeleteSmartLockInvalidToken() =
        testApplication {
            application { configureTestModules() }

            val response =
                client.delete("/v1/properties/${TEST_ACCOUNT_1_PROPERTY_1.id}/smartlocks/$testSmartlockId") {
                    header(HttpHeaders.Authorization, "Bearer invalid-token")
                }

            assertEquals(HttpStatusCode.Companion.Unauthorized, response.status)
        }

    @Test
    fun testDeleteSmartLockWrongAccount() =
        testApplication {
            application { configureTestModules() }

            val response =
                client.delete("/v1/properties/${TEST_ACCOUNT_1_PROPERTY_1.id}/smartlocks/$testSmartlockId") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_2_USER_3.token}")
                }

            assertEquals(HttpStatusCode.Companion.Forbidden, response.status)
        }

    @Test
    fun testDeleteSmartLockStaffDenied() =
        testApplication {
            application { configureTestModules() }

            testDBConnection.grantCrewAccess(
                userId = TEST_ACCOUNT_2_USER_3.id,
                propertyId = TEST_ACCOUNT_1_PROPERTY_1.id,
                service = "cleaning",
                accessLevel = "staff",
            )

            val response =
                client.delete("/v1/properties/${TEST_ACCOUNT_1_PROPERTY_1.id}/smartlocks/$testSmartlockId") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_2_USER_3.token}")
                }

            assertEquals(HttpStatusCode.Companion.Forbidden, response.status)
        }

    @Test
    fun testDeleteSmartLockContractorDenied() =
        testApplication {
            application { configureTestModules() }

            testDBConnection.grantCrewAccess(
                userId = TEST_ACCOUNT_2_USER_3.id,
                propertyId = TEST_ACCOUNT_1_PROPERTY_1.id,
                service = "cleaning",
                accessLevel = "contractor",
            )

            val response =
                client.delete("/v1/properties/${TEST_ACCOUNT_1_PROPERTY_1.id}/smartlocks/$testSmartlockId") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_2_USER_3.token}")
                }

            assertEquals(HttpStatusCode.Companion.Forbidden, response.status)
        }

    @Test
    fun testDeleteSmartLockManagerAllowed() =
        testApplication {
            application { configureTestModules() }

            testDBConnection.grantCrewAccess(
                userId = TEST_ACCOUNT_2_USER_3.id,
                propertyId = TEST_ACCOUNT_1_PROPERTY_1.id,
                accessLevel = "manager",
            )

            val response =
                client.delete("/v1/properties/${TEST_ACCOUNT_1_PROPERTY_1.id}/smartlocks/$testSmartlockId") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_2_USER_3.token}")
                }

            assertEquals(HttpStatusCode.Companion.NoContent, response.status)
        }

    @Test
    fun testDeleteSmartLockNotFound() =
        testApplication {
            application { configureTestModules() }

            val response =
                client.delete("/v1/properties/${TEST_ACCOUNT_1_PROPERTY_1.id}/smartlocks/$nonExistentSmartlockId") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                }

            assertEquals(HttpStatusCode.Companion.NotFound, response.status)
        }

    @Test
    fun testDeleteSmartLockInvalidDoorId() =
        testApplication {
            application { configureTestModules() }

            val response =
                client.delete("/v1/properties/${TEST_ACCOUNT_1_PROPERTY_1.id}/smartlocks/invalid-uuid") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                }

            assertEquals(HttpStatusCode.Companion.BadRequest, response.status)
            assertContains(response.bodyAsText(), "Smartlock ID has an incorrect format")
        }

    @Test
    fun testDeleteSmartLockPropertyNotFound() =
        testApplication {
            application { configureTestModules() }

            val nonExistentPropertyId = Uuid.Companion.parse("30000000-0000-0000-0000-000000000999")

            val response =
                client.delete("/v1/properties/$nonExistentPropertyId/smartlocks/$testSmartlockId") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                }

            assertEquals(HttpStatusCode.Companion.NotFound, response.status)
        }
}
