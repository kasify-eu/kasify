package eu.kasify.routes

import eu.kasify.TEST_ACCOUNT_1_PROPERTY_1
import eu.kasify.TEST_ACCOUNT_1_USER_1
import eu.kasify.TEST_ACCOUNT_2_USER_3
import eu.kasify.TEST_ACCOUNT_ID_1
import eu.kasify.extensions.configureTestModules
import eu.kasify.extensions.createClient
import eu.kasify.helpers.grantCrewAccess
import eu.kasify.routes.extensions.INVALID_PROPERTY_ID_FORMAT_ERROR
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class PropertiesRouteTest : BaseRouteDBTests() {
    private val otherPropertyId = Uuid.Companion.parse("11111111-1111-1111-1111-111111111111")
    private val sesAccountIntegrationId = Uuid.Companion.parse("70000000-0000-0000-0000-000000000001")
    private val sesIntegrationRowId = Uuid.Companion.parse("70000000-0000-0000-0000-000000000002")
    private val testSmartlockId = Uuid.Companion.parse("50000000-0000-0000-0000-000000000001")
    private val testNukiSmartlockId = Uuid.Companion.parse("60000000-0000-0000-0000-000000000001")

    private val usaCountryId = eu.kasify.TEST_USA_COUNTRY_ID
    private val spainCountryId = eu.kasify.TEST_SPAIN_COUNTRY_ID
    private val madridProvinceId = Uuid.Companion.parse("c1000000-0000-0000-0000-000000000001")
    private val madridCityId = Uuid.Companion.parse("c2000000-0000-0000-0000-000000000001")
    private val barcelonaProvinceId = Uuid.Companion.parse("c1000000-0000-0000-0000-000000000002")

    override fun setupData() {
        // Spain is already seeded by the base; add USA + Spanish provinces and a city.
        testDBConnection.executeQuery(
            "INSERT INTO countries (id, name, name_es, alpha_2, alpha_3) VALUES " +
                "('$usaCountryId', 'United States', 'Estados Unidos', 'US', 'USA')",
        )
        testDBConnection.executeQuery(
            "INSERT INTO provinces (id, country_id, name, code_iso_3166_2, ine_code_pro) VALUES " +
                "('$madridProvinceId', '$spainCountryId', 'Madrid', 'ES-M', '28'), " +
                "('$barcelonaProvinceId', '$spainCountryId', 'Barcelona', 'ES-B', '08')",
        )
        testDBConnection.executeQuery(
            "INSERT INTO cities (id, country_id, province_id, name, ine_code, ine_code_mun) VALUES " +
                "('$madridCityId', '$spainCountryId', '$madridProvinceId', 'Madrid', '28079', '079')",
        )

        // Insert a test smartlock for the property
        testDBConnection.executeQuery(
            """
            INSERT INTO property_smartlocks (id, property_id, vendor, name, created_at, updated_at)
            VALUES ('$testSmartlockId', '${TEST_ACCOUNT_1_PROPERTY_1.id}', 'nuki', 'Main Door', NOW(), NOW())
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
                "VALUES(gen_random_uuid(), (SELECT id FROM account_integrations WHERE account_id = '${TEST_ACCOUNT_ID_1}' AND vendor = 'nuki' LIMIT 1), 'test_token', now(), now());"
        testDBConnection.executeQuery(sqlNukiConfig)

        // Insert Nuki-specific smartlock data
        // Insert Nuki smartlock details
        val sqlNukiSmartlock =
            "INSERT INTO public.property_nuki_smartlocks " +
                "(id, property_smartlock_id, nuki_integration_id, nuki_smartlock_id, created_at, updated_at) " +
                "VALUES('$testNukiSmartlockId', '$testSmartlockId', (SELECT id FROM account_nuki_integrations LIMIT 1), '123456789', now(), now())"
        testDBConnection.executeQuery(sqlNukiSmartlock)
    }

    @Test
    fun testGetPropertyByIdSuccess() =
        testApplication {
            application { configureTestModules() }

            val response =
                client.get("/v1/properties/${TEST_ACCOUNT_1_PROPERTY_1.id}") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                }

            assertEquals(HttpStatusCode.Companion.OK, response.status)
            val responseBody = response.bodyAsText()
            assertTrue(responseBody.contains(TEST_ACCOUNT_1_PROPERTY_1.name))
            assertTrue(responseBody.contains(TEST_ACCOUNT_1_PROPERTY_1.id.toString()))
        }

    @Test
    fun testGetPropertyByIdWrongAccountIDNotFound() =
        testApplication {
            application { configureTestModules() }

            val response =
                client.get("/v1/properties/${TEST_ACCOUNT_1_PROPERTY_1.id}") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_2_USER_3.token}")
                }

            assertEquals(HttpStatusCode.Companion.Forbidden, response.status)
        }

    @Test
    fun testGetPropertyByIdNotFound() =
        testApplication {
            application { configureTestModules() }

            val response =
                client.get("/v1/properties/$otherPropertyId") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                }

            assertEquals(HttpStatusCode.Companion.NotFound, response.status)
            assertContains(response.bodyAsText(), "Property not found")
        }

    @Test
    fun testGetPropertyByIdInvalidUuid() =
        testApplication {
            application { configureTestModules() }

            val response =
                client.get("/v1/properties/invalid-uuid") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                }

            assertEquals(HttpStatusCode.Companion.BadRequest, response.status)
            assertContains(response.bodyAsText(), "Property ID has an incorrect format")
        }

    @Test
    fun testGetPropertyByIdUnauthorized() =
        testApplication {
            application { configureTestModules() }

            val response = client.get("/v1/properties/${TEST_ACCOUNT_1_PROPERTY_1.id}")

            assertEquals(HttpStatusCode.Companion.Unauthorized, response.status)
        }

    @Test
    fun testGetPropertyByIdInvalidToken() =
        testApplication {
            application { configureTestModules() }

            val response =
                client.get("/v1/properties/${TEST_ACCOUNT_1_PROPERTY_1.id}") {
                    header(HttpHeaders.Authorization, "Bearer invalid-token")
                }

            assertEquals(HttpStatusCode.Companion.Unauthorized, response.status)
        }

    @Test
    fun `properties should return a list of properties`() =
        testApplication {
            application { configureTestModules() }

            val response =
                client.get("/v1/properties") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                }

            assertEquals(HttpStatusCode.Companion.OK, response.status)
            val responseBody = response.bodyAsText()
            assertTrue(responseBody.contains(TEST_ACCOUNT_1_PROPERTY_1.name))
        }

    private fun seedSesEstablishmentCode() {
        testDBConnection.executeQuery(
            "UPDATE properties SET ses_establishment_code = 'EST-0001' " +
                "WHERE id = '${TEST_ACCOUNT_1_PROPERTY_1.id}'",
        )
    }

    private fun seedSesIntegration(
        enabled: Boolean = true,
        integrationId: Uuid = sesIntegrationRowId,
        accountIntegrationId: Uuid = sesAccountIntegrationId,
        arrendadorCode: String = "ARR001",
    ) {
        testDBConnection.insertSesIntegration(
            accountId = TEST_ACCOUNT_ID_1,
            integrationId = integrationId,
            accountIntegrationId = accountIntegrationId,
            arrendadorCode = arrendadorCode,
            enabled = enabled,
        )
        // Strict model: ses_enabled (and the whole SES pipeline) requires the explicit
        // property → integration assignment on top of the code, so assign the test property
        // whenever it already carries one (tests seed the code first when they want both).
        // Never reassigns: a property stays with the first seeded integration.
        testDBConnection.executeQuery(
            "UPDATE properties SET ses_integration_id = '$integrationId' " +
                "WHERE id = '${TEST_ACCOUNT_1_PROPERTY_1.id}' AND ses_establishment_code IS NOT NULL " +
                "AND ses_integration_id IS NULL",
        )
    }

    @Test
    fun `properties list reports ses_enabled true when integration and establishment code are present`() =
        testApplication {
            application { configureTestModules() }
            seedSesEstablishmentCode()
            seedSesIntegration()

            val response =
                client.get("/v1/properties") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                }

            assertEquals(HttpStatusCode.Companion.OK, response.status)
            assertContains(response.bodyAsText(), "\"ses_enabled\":true")
        }

    @Test
    fun `properties list reports ses_enabled false when the integration has been removed`() =
        testApplication {
            application { configureTestModules() }
            // Establishment code still on the property, but no account integration exists.
            seedSesEstablishmentCode()

            val response =
                client.get("/v1/properties") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                }

            assertEquals(HttpStatusCode.Companion.OK, response.status)
            assertContains(response.bodyAsText(), "\"ses_enabled\":false")
        }

    @Test
    fun `properties list reports ses_enabled false when the integration is disabled`() =
        testApplication {
            application { configureTestModules() }
            seedSesEstablishmentCode()
            seedSesIntegration(enabled = false)

            val response =
                client.get("/v1/properties") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                }

            assertEquals(HttpStatusCode.Companion.OK, response.status)
            assertContains(response.bodyAsText(), "\"ses_enabled\":false")
        }

    @Test
    fun `properties list reports ses_enabled false when the establishment code is missing`() =
        testApplication {
            application { configureTestModules() }
            seedSesIntegration()

            val response =
                client.get("/v1/properties") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                }

            assertEquals(HttpStatusCode.Companion.OK, response.status)
            assertContains(response.bodyAsText(), "\"ses_enabled\":false")
        }

    @Test
    fun `properties list hides SES and manager-only fields from staff and reports their role`() =
        testApplication {
            application { configureTestModules() }
            seedSesEstablishmentCode()
            seedSesIntegration()
            testDBConnection.grantCrewAccess(
                userId = TEST_ACCOUNT_2_USER_3.id,
                propertyId = TEST_ACCOUNT_1_PROPERTY_1.id,
                service = "cleaning",
                accessLevel = "staff",
            )

            val response =
                client.get("/v1/properties") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_2_USER_3.token}")
                }

            assertEquals(HttpStatusCode.Companion.OK, response.status)
            val body = response.bodyAsText()
            assertContains(body, TEST_ACCOUNT_1_PROPERTY_1.name)
            assertContains(body, "\"role\":\"staff\"")
            assertFalse(body.contains("ses_enabled"))
            assertFalse(body.contains("ses_establishment_code"))
            assertFalse(body.contains("rooms_count"))
        }

    @Test
    fun `properties list shows SES fields to managers`() =
        testApplication {
            application { configureTestModules() }
            seedSesEstablishmentCode()
            seedSesIntegration()
            testDBConnection.grantCrewAccess(
                userId = TEST_ACCOUNT_2_USER_3.id,
                propertyId = TEST_ACCOUNT_1_PROPERTY_1.id,
                accessLevel = "manager",
            )

            val response =
                client.get("/v1/properties") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_2_USER_3.token}")
                }

            assertEquals(HttpStatusCode.Companion.OK, response.status)
            val body = response.bodyAsText()
            assertContains(body, "\"role\":\"manager\"")
            assertContains(body, "\"ses_enabled\":true")
            assertContains(body, "\"ses_establishment_code\":\"EST-0001\"")
        }

    @Test
    fun `properties list hides SES and manager-only fields from contractors`() =
        testApplication {
            application { configureTestModules() }
            seedSesEstablishmentCode()
            seedSesIntegration()
            testDBConnection.grantCrewAccess(
                userId = TEST_ACCOUNT_2_USER_3.id,
                propertyId = TEST_ACCOUNT_1_PROPERTY_1.id,
                service = "cleaning",
                accessLevel = "contractor",
            )

            val response =
                client.get("/v1/properties") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_2_USER_3.token}")
                }

            assertEquals(HttpStatusCode.Companion.OK, response.status)
            val body = response.bodyAsText()
            assertContains(body, "\"role\":\"contractor\"")
            assertFalse(body.contains("ses_enabled"))
            assertFalse(body.contains("ses_establishment_code"))
            assertFalse(body.contains("rooms_count"))
        }

    @Test
    fun `properties list shows SES fields to owners`() =
        testApplication {
            application { configureTestModules() }
            seedSesEstablishmentCode()
            seedSesIntegration()

            val response =
                client.get("/v1/properties") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                }

            assertEquals(HttpStatusCode.Companion.OK, response.status)
            val body = response.bodyAsText()
            assertContains(body, "\"role\":\"owner\"")
            assertContains(body, "\"ses_enabled\":true")
            assertContains(body, "\"ses_establishment_code\":\"EST-0001\"")
        }

    @Test
    fun `properties list reports role owner for account properties`() =
        testApplication {
            application { configureTestModules() }

            val response =
                client.get("/v1/properties") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                }

            assertEquals(HttpStatusCode.Companion.OK, response.status)
            assertContains(response.bodyAsText(), "\"role\":\"owner\"")
        }

    @Test
    fun `property details report ses_enabled true when integration and establishment code are present`() =
        testApplication {
            application { configureTestModules() }
            seedSesEstablishmentCode()
            seedSesIntegration()

            val response =
                client.get("/v1/properties/${TEST_ACCOUNT_1_PROPERTY_1.id}") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                }

            assertEquals(HttpStatusCode.Companion.OK, response.status)
            assertContains(response.bodyAsText(), "\"ses_enabled\":true")
        }

    @Test
    fun `propertiesSLASH should return a not found error because no id has been specified`() =
        testApplication {
            application { configureTestModules() }

            val response =
                client.get("/v1/properties/") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                }

            assertEquals(HttpStatusCode.Companion.NotFound, response.status)
        }

    @Test
    fun testGetPropertyByIdInvalidPropertyId() =
        testApplication {
            application { configureTestModules() }

            val response =
                client.get("/v1/properties/wrong-id") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                }

            assertEquals(HttpStatusCode.Companion.BadRequest, response.status)
            assertContains(response.bodyAsText(), INVALID_PROPERTY_ID_FORMAT_ERROR)
        }

    @Test
    fun testCreatePropertySuccess() =
        testApplication {
            application { configureTestModules() }

            val response =
                client.post("/v1/properties") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                    contentType(ContentType.Application.Json)
                    setBody(
                        """{"name": "New Test Property", "address": "123 Test St", "zip_code": "12345", "province_id": "$madridProvinceId", "city_id": "$madridCityId"}""",
                    )
                }

            assertEquals(HttpStatusCode.Created, response.status)
            val responseBody = response.bodyAsText()
            assertTrue(responseBody.contains("New Test Property"))
            // city is auto-populated from the looked-up city row
            assertTrue(responseBody.contains("\"city\":\"Madrid\""))
            assertTrue(responseBody.contains("\"country_code\":\"ESP\""))
        }

    @Test
    fun `POST properties rejects Spanish property without province_id`() =
        testApplication {
            application { configureTestModules() }

            val response =
                client.post("/v1/properties") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                    contentType(ContentType.Application.Json)
                    setBody(
                        """{"name": "Bad", "address": "addr", "zip_code": "12345", "city_id": "$madridCityId"}""",
                    )
                }

            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertContains(response.bodyAsText(), "province_id is required")
        }

    @Test
    fun `POST properties rejects city not in province`() =
        testApplication {
            application { configureTestModules() }

            val response =
                client.post("/v1/properties") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                    contentType(ContentType.Application.Json)
                    setBody(
                        """{"name": "Bad", "address": "addr", "zip_code": "12345", "province_id": "$barcelonaProvinceId", "city_id": "$madridCityId"}""",
                    )
                }

            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertContains(response.bodyAsText(), "property.city_province_mismatch")
        }

    @Test
    fun `POST properties accepts non-Spanish residence with city text`() =
        testApplication {
            application { configureTestModules() }

            val response =
                client.post("/v1/properties") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                    contentType(ContentType.Application.Json)
                    setBody(
                        """{"name": "US Place", "address": "5th Ave", "city": "New York", "zip_code": "10001", "country_code": "USA"}""",
                    )
                }

            assertEquals(HttpStatusCode.Created, response.status)
            val responseBody = response.bodyAsText()
            assertContains(responseBody, "\"city\":\"New York\"")
            assertContains(responseBody, "\"country_code\":\"USA\"")
        }

    @Test
    fun `POST properties rejects non-Spanish without city text`() =
        testApplication {
            application { configureTestModules() }

            val response =
                client.post("/v1/properties") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                    contentType(ContentType.Application.Json)
                    setBody(
                        """{"name": "US Place", "address": "5th Ave", "zip_code": "10001", "country_code": "USA"}""",
                    )
                }

            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertContains(response.bodyAsText(), "city is required")
        }

    @Test
    fun testCreatePropertyUnauthorized() =
        testApplication {
            application { configureTestModules() }

            val response =
                client.post("/v1/properties") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"name": "New Test Property"}""")
                }

            assertEquals(HttpStatusCode.Companion.Unauthorized, response.status)
        }

    @Test
    fun testCreatePropertyInvalidToken() =
        testApplication {
            application { configureTestModules() }

            val response =
                client.post("/v1/properties") {
                    header(HttpHeaders.Authorization, "Bearer invalid-token")
                    contentType(ContentType.Application.Json)
                    setBody("""{"name": "New Test Property"}""")
                }

            assertEquals(HttpStatusCode.Companion.Unauthorized, response.status)
        }

    @Test
    fun testCreatePropertyBlankName() =
        testApplication {
            application { configureTestModules() }

            val response =
                client.post("/v1/properties") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                    contentType(ContentType.Application.Json)
                    setBody(
                        """{"name": "", "address": "Test Address", "zip_code": "12345", "province_id": "$madridProvinceId", "city_id": "$madridCityId"}""",
                    )
                }

            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertContains(response.bodyAsText(), "Property name must not be blank")
        }

    @Test
    fun testCreatePropertyNameTooLong() =
        testApplication {
            application { configureTestModules() }

            val longName = "a".repeat(51) // Exceeds PROPERTY_MAX_NAME_LENGTH of 50

            val response =
                client.post("/v1/properties") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                    contentType(ContentType.Application.Json)
                    setBody(
                        """{"name": "$longName", "address": "Test Address", "zip_code": "12345", "province_id": "$madridProvinceId", "city_id": "$madridCityId"}""",
                    )
                }

            assertEquals(HttpStatusCode.Companion.BadRequest, response.status)
            val responseBody = response.bodyAsText()
            assertContains(responseBody, "Property name must not exceed 50 characters")
        }

    @Test
    fun testGetPropertyByIdIncludesSmartLocks() =
        testApplication {
            application { configureTestModules() }

            val response =
                client.get("/v1/properties/${TEST_ACCOUNT_1_PROPERTY_1.id}") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                }

            assertEquals(HttpStatusCode.Companion.OK, response.status)
            val responseBody = response.bodyAsText()

            // Verify property details
            assertTrue(responseBody.contains(TEST_ACCOUNT_1_PROPERTY_1.name))

            // Verify smartLocks array exists and contains the test smartlock
            assertTrue(responseBody.contains("\"smart_locks\""))
            assertTrue(responseBody.contains("Main Door"))
            assertTrue(responseBody.contains(testSmartlockId.toString()))
            assertTrue(responseBody.contains("\"vendor\":\"nuki\"") || responseBody.contains("\"vendor\":\"Nuki\""))
        }

    @Test
    fun `Owner sees smart_locks in property details`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val response =
                client.get("/v1/properties/${TEST_ACCOUNT_1_PROPERTY_1.id}") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                }

            assertEquals(HttpStatusCode.OK, response.status)
            val responseBody = response.bodyAsText()
            assertContains(responseBody, "\"smart_locks\"")
            assertContains(responseBody, "Main Door")
            assertContains(responseBody, testSmartlockId.toString())
        }

    @Test
    fun `Manager sees smart_locks in property details`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            testDBConnection.grantCrewAccess(
                userId = TEST_ACCOUNT_2_USER_3.id,
                propertyId = TEST_ACCOUNT_1_PROPERTY_1.id,
                accessLevel = "manager",
            )

            val response =
                client.get("/v1/properties/${TEST_ACCOUNT_1_PROPERTY_1.id}") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_2_USER_3.token}")
                }

            assertEquals(HttpStatusCode.OK, response.status)
            val responseBody = response.bodyAsText()
            assertContains(responseBody, "\"smart_locks\"")
            assertContains(responseBody, "Main Door")
            assertContains(responseBody, testSmartlockId.toString())
        }

    @Test
    fun `Staff does not see smart_locks in property details`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            testDBConnection.grantCrewAccess(
                userId = TEST_ACCOUNT_2_USER_3.id,
                propertyId = TEST_ACCOUNT_1_PROPERTY_1.id,
                accessLevel = "staff",
            )

            val response =
                client.get("/v1/properties/${TEST_ACCOUNT_1_PROPERTY_1.id}") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_2_USER_3.token}")
                }

            assertEquals(HttpStatusCode.OK, response.status)
            val responseBody = response.bodyAsText()
            assertFalse(responseBody.contains("\"smart_locks\""), "Staff should not see smart_locks")
        }

    @Test
    fun `Contractor does not see smart_locks in property details`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            testDBConnection.grantCrewAccess(
                userId = TEST_ACCOUNT_2_USER_3.id,
                propertyId = TEST_ACCOUNT_1_PROPERTY_1.id,
                accessLevel = "contractor",
            )

            val response =
                client.get("/v1/properties/${TEST_ACCOUNT_1_PROPERTY_1.id}") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_2_USER_3.token}")
                }

            assertEquals(HttpStatusCode.OK, response.status)
            val responseBody = response.bodyAsText()
            assertFalse(responseBody.contains("\"smart_locks\""), "Contractor should not see smart_locks")
        }

    @Test
    fun `Owner sees team in property details`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val response =
                client.get("/v1/properties/${TEST_ACCOUNT_1_PROPERTY_1.id}") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                }

            assertEquals(HttpStatusCode.OK, response.status)
            assertContains(response.bodyAsText(), "\"team\"")
        }

    @Test
    fun `Manager sees team in property details`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            testDBConnection.grantCrewAccess(
                userId = TEST_ACCOUNT_2_USER_3.id,
                propertyId = TEST_ACCOUNT_1_PROPERTY_1.id,
                accessLevel = "manager",
            )

            val response =
                client.get("/v1/properties/${TEST_ACCOUNT_1_PROPERTY_1.id}") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_2_USER_3.token}")
                }

            assertEquals(HttpStatusCode.OK, response.status)
            assertContains(response.bodyAsText(), "\"team\"")
        }

    @Test
    fun `Staff does not see team in property details`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            testDBConnection.grantCrewAccess(
                userId = TEST_ACCOUNT_2_USER_3.id,
                propertyId = TEST_ACCOUNT_1_PROPERTY_1.id,
                accessLevel = "staff",
            )

            val response =
                client.get("/v1/properties/${TEST_ACCOUNT_1_PROPERTY_1.id}") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_2_USER_3.token}")
                }

            assertEquals(HttpStatusCode.OK, response.status)
            assertFalse(response.bodyAsText().contains("\"team\""), "Staff should not see team")
        }

    @Test
    fun `Contractor does not see team in property details`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            testDBConnection.grantCrewAccess(
                userId = TEST_ACCOUNT_2_USER_3.id,
                propertyId = TEST_ACCOUNT_1_PROPERTY_1.id,
                accessLevel = "contractor",
            )

            val response =
                client.get("/v1/properties/${TEST_ACCOUNT_1_PROPERTY_1.id}") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_2_USER_3.token}")
                }

            assertEquals(HttpStatusCode.OK, response.status)
            assertFalse(response.bodyAsText().contains("\"team\""), "Contractor should not see team")
        }

    // ============================================================
    // LEAVE PROPERTY TESTS - POST /properties/{id}/leave
    // ============================================================

    @Test
    fun `Crew member leaves property successfully`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            testDBConnection.grantCrewAccess(
                userId = TEST_ACCOUNT_2_USER_3.id,
                propertyId = TEST_ACCOUNT_1_PROPERTY_1.id,
                accessLevel = "staff",
            )

            val response =
                client.post("/v1/properties/${TEST_ACCOUNT_1_PROPERTY_1.id}/leave") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_2_USER_3.token}")
                }

            assertEquals(HttpStatusCode.NoContent, response.status)

            // Access is gone — fetching the property is now denied.
            val getResponse =
                client.get("/v1/properties/${TEST_ACCOUNT_1_PROPERTY_1.id}") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_2_USER_3.token}")
                }
            assertEquals(HttpStatusCode.Forbidden, getResponse.status)
        }

    @Test
    fun `Owner cannot leave property`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val response =
                client.post("/v1/properties/${TEST_ACCOUNT_1_PROPERTY_1.id}/leave") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                }

            assertEquals(HttpStatusCode.Forbidden, response.status)
            assertContains(response.bodyAsText(), "property.owner_cannot_leave")
        }

    @Test
    fun `Non-member cannot leave property`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val response =
                client.post("/v1/properties/${TEST_ACCOUNT_1_PROPERTY_1.id}/leave") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_2_USER_3.token}")
                }

            assertEquals(HttpStatusCode.Forbidden, response.status)
        }

    // ============================================================
    // PATCH PROPERTY TESTS - PATCH /properties/{id}
    // ============================================================

    @Test
    fun `PATCH property updates only supplied fields and leaves others untouched`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()
            // Assign the property first: a code edit without ses_integration_id is only allowed
            // on an already-assigned property (strict model).
            seedSesEstablishmentCode()
            seedSesIntegration()

            val getBefore =
                client.get("/v1/properties/${TEST_ACCOUNT_1_PROPERTY_1.id}") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                }
            val bodyBefore = getBefore.bodyAsText()
            val originalName = TEST_ACCOUNT_1_PROPERTY_1.name

            val response =
                client.patch("/v1/properties/${TEST_ACCOUNT_1_PROPERTY_1.id}") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                    contentType(ContentType.Application.Json)
                    setBody("""{"ses_establishment_code": "AB1234"}""")
                }

            assertEquals(HttpStatusCode.OK, response.status)
            val body = response.bodyAsText()
            assertContains(body, "\"ses_establishment_code\":\"AB1234\"")
            // Original name preserved
            assertContains(body, originalName)
            // Sanity: address from the pre-PATCH GET still present in PATCH response
            // (cross-checks that PATCH did not wipe unsupplied columns)
            assertTrue(bodyBefore.contains("\"address\""))
        }

    @Test
    fun `PATCH property rejects non-owner caller`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            testDBConnection.grantCrewAccess(
                userId = TEST_ACCOUNT_2_USER_3.id,
                propertyId = TEST_ACCOUNT_1_PROPERTY_1.id,
                accessLevel = "manager",
            )

            val response =
                client.patch("/v1/properties/${TEST_ACCOUNT_1_PROPERTY_1.id}") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_2_USER_3.token}")
                    contentType(ContentType.Application.Json)
                    setBody("""{"ses_establishment_code": "AB1234"}""")
                }

            assertEquals(HttpStatusCode.Forbidden, response.status)
        }

    @Test
    fun `PATCH property rejects blank name`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val response =
                client.patch("/v1/properties/${TEST_ACCOUNT_1_PROPERTY_1.id}") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                    contentType(ContentType.Application.Json)
                    setBody("""{"name": ""}""")
                }

            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertContains(response.bodyAsText(), "Property name must not be blank")
        }

    @Test
    fun `PATCH property returns NotFound for nonexistent id`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val response =
                client.patch("/v1/properties/$otherPropertyId") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                    contentType(ContentType.Application.Json)
                    setBody("""{"ses_establishment_code": "AB1234"}""")
                }

            assertEquals(HttpStatusCode.NotFound, response.status)
        }

    // --- property ↔ SES integration assignment (strict: one integration per property) ---

    private suspend fun patchSes(
        client: io.ktor.client.HttpClient,
        body: String,
    ) = client.patch("/v1/properties/${TEST_ACCOUNT_1_PROPERTY_1.id}") {
        header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
        contentType(ContentType.Application.Json)
        setBody(body)
    }

    @Test
    fun `PATCH establishment code on an unassigned property without integration id is rejected`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()
            seedSesIntegration() // integration exists, but the property is not assigned

            val response = patchSes(client, """{"ses_establishment_code": "AB1234"}""")

            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertContains(response.bodyAsText(), "ses_hospedajes.integration_required")
        }

    @Test
    fun `PATCH establishment code with the integration id assigns the property`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()
            seedSesIntegration()

            val response =
                patchSes(
                    client,
                    """{"ses_establishment_code": "AB1234", "ses_integration_id": "$sesIntegrationRowId"}""",
                )

            assertEquals(HttpStatusCode.OK, response.status)
            val body = response.bodyAsText()
            assertContains(body, "\"ses_establishment_code\":\"AB1234\"")
            assertContains(body, "\"ses_integration_id\":\"$sesIntegrationRowId\"")
        }

    @Test
    fun `PATCH establishment code on a property configured on another integration is rejected`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()
            seedSesEstablishmentCode()
            seedSesIntegration() // assigns the property to sesIntegrationRowId
            // A second integration in the same account.
            val otherIntegrationId = Uuid.parse("70000000-0000-0000-0000-000000000004")
            seedSesIntegration(
                integrationId = otherIntegrationId,
                accountIntegrationId = Uuid.parse("70000000-0000-0000-0000-000000000003"),
                arrendadorCode = "ARR002",
            )

            val response =
                patchSes(
                    client,
                    """{"ses_establishment_code": "AB1234", "ses_integration_id": "$otherIntegrationId"}""",
                )

            assertEquals(HttpStatusCode.Conflict, response.status)
            assertContains(response.bodyAsText(), "ses_hospedajes.property_already_configured")
        }

    @Test
    fun `PATCH with an unknown integration id is rejected`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()
            seedSesIntegration()

            val response =
                patchSes(
                    client,
                    """{"ses_establishment_code": "AB1234", "ses_integration_id": "70000000-0000-0000-0000-0000000000ff"}""",
                )

            assertEquals(HttpStatusCode.NotFound, response.status)
        }

    @Test
    fun `PATCH with a blank establishment code clears the code and the assignment`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()
            seedSesEstablishmentCode()
            seedSesIntegration() // property assigned

            val response = patchSes(client, """{"ses_establishment_code": ""}""")

            assertEquals(HttpStatusCode.OK, response.status)
            // The "unconfigure" step of a two-step reassignment: both columns go NULL, so the
            // property can now be configured on any integration.
            val body = response.bodyAsText()
            assertFalse(body.contains("\"ses_establishment_code\":\"EST-0001\""))
            assertFalse(body.contains("\"ses_integration_id\":\"$sesIntegrationRowId\""))
        }

    @Test
    fun `PATCH code edit on an assigned property without integration id keeps the assignment`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()
            seedSesEstablishmentCode()
            seedSesIntegration() // property assigned

            val response = patchSes(client, """{"ses_establishment_code": "AB9999"}""")

            assertEquals(HttpStatusCode.OK, response.status)
            val body = response.bodyAsText()
            assertContains(body, "\"ses_establishment_code\":\"AB9999\"")
            assertContains(body, "\"ses_integration_id\":\"$sesIntegrationRowId\"")
        }

    @Test
    fun `PATCH property rejects establishment code equal to the SES arrendador code`() =
        testApplication {
            application { configureTestModules() }
            seedSesIntegration()
            val client = createClient()

            val response =
                client.patch("/v1/properties/${TEST_ACCOUNT_1_PROPERTY_1.id}") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                    contentType(ContentType.Application.Json)
                    setBody("""{"ses_establishment_code": "ARR001"}""")
                }

            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertContains(response.bodyAsText(), "property.establishment_code_matches_arrendador")
        }

    @Test
    fun `PATCH property rejects establishment code equal to the arrendador code ignoring case and whitespace`() =
        testApplication {
            application { configureTestModules() }
            seedSesIntegration()
            val client = createClient()

            val response =
                client.patch("/v1/properties/${TEST_ACCOUNT_1_PROPERTY_1.id}") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                    contentType(ContentType.Application.Json)
                    setBody("""{"ses_establishment_code": " arr001 "}""")
                }

            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertContains(response.bodyAsText(), "property.establishment_code_matches_arrendador")
        }

    @Test
    fun `PATCH property accepts establishment code different from the arrendador code`() =
        testApplication {
            application { configureTestModules() }
            seedSesIntegration()
            val client = createClient()

            val response =
                client.patch("/v1/properties/${TEST_ACCOUNT_1_PROPERTY_1.id}") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                    contentType(ContentType.Application.Json)
                    // Strict model: setting a code on an unassigned property carries the integration id.
                    setBody("""{"ses_establishment_code": "EST-9999", "ses_integration_id": "$sesIntegrationRowId"}""")
                }

            assertEquals(HttpStatusCode.OK, response.status)
            assertContains(response.bodyAsText(), "\"ses_establishment_code\":\"EST-9999\"")
        }

    @Test
    fun `PUT property rejects establishment code equal to the SES arrendador code`() =
        testApplication {
            application { configureTestModules() }
            seedSesIntegration()
            val client = createClient()

            val response =
                client.put("/v1/properties/${TEST_ACCOUNT_1_PROPERTY_1.id}") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                    contentType(ContentType.Application.Json)
                    setBody(
                        """
                        {
                            "name": "Updated",
                            "address": "Calle Mayor 1",
                            "zip_code": "28001",
                            "province_id": "$madridProvinceId",
                            "city_id": "$madridCityId",
                            "ses_establishment_code": "ARR001"
                        }
                        """.trimIndent(),
                    )
                }

            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertContains(response.bodyAsText(), "property.establishment_code_matches_arrendador")
        }

    @Test
    fun `POST property rejects establishment code equal to the SES arrendador code`() =
        testApplication {
            application { configureTestModules() }
            seedSesIntegration()
            val client = createClient()

            val response =
                client.post("/v1/properties") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                    contentType(ContentType.Application.Json)
                    setBody(
                        """
                        {
                            "name": "New Property",
                            "address": "Calle Mayor 1",
                            "zip_code": "28001",
                            "province_id": "$madridProvinceId",
                            "city_id": "$madridCityId",
                            "ses_establishment_code": "ARR001"
                        }
                        """.trimIndent(),
                    )
                }

            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertContains(response.bodyAsText(), "property.establishment_code_matches_arrendador")
        }
}
