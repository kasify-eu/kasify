package eu.kasify.routes

import eu.kasify.TEST_ACCOUNT_1_USER_1
import eu.kasify.extensions.configureTestModules
import eu.kasify.extensions.createClient
import eu.kasify.model.api.CreateApiTokenRequest
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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ApiTokensRouteTest : BaseRouteDBTests() {
    private val authHeader = "Bearer ${TEST_ACCOUNT_1_USER_1.token}"

    @Test
    fun `POST creates token with default 365 day expiration when expires_in_days omitted`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val response =
                client.post("/v1/account/tokens") {
                    header(HttpHeaders.Authorization, authHeader)
                    contentType(ContentType.Application.Json)
                    setBody(buildJsonObject { put("name", "default-expiry") })
                }

            assertEquals(HttpStatusCode.Created, response.status)
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            assertNotNull(body["token"]?.jsonPrimitive?.content)
            val expiresAt = body["expires_at"]
            assertNotNull(expiresAt)
            assertTrue(expiresAt !is JsonNull, "expected default expiration to be set")
        }

    @Test
    fun `POST returns the raw token exactly once and only in the create response`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val createBody =
                client
                    .post("/v1/account/tokens") {
                        header(HttpHeaders.Authorization, authHeader)
                        contentType(ContentType.Application.Json)
                        setBody(CreateApiTokenRequest("only-once"))
                    }.bodyAsText()
            val raw =
                Json
                    .parseToJsonElement(createBody)
                    .jsonObject["token"]
                    ?.jsonPrimitive
                    ?.content
            assertNotNull(raw)
            assertTrue(raw.startsWith("kas_pat_"))

            val listBody =
                client
                    .get("/v1/account/tokens") { header(HttpHeaders.Authorization, authHeader) }
                    .bodyAsText()
            val list = Json.parseToJsonElement(listBody).jsonArray
            assertEquals(1, list.size)
            val first = list[0].jsonObject
            assertNull(first["token"], "list response must not include raw token")
            assertNull(first["token_hash"], "list response must not include token hash")
            assertEquals("only-once", first["name"]?.jsonPrimitive?.content)
        }

    @Test
    fun `POST with explicit null expires_in_days creates a never-expires token`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val response =
                client.post("/v1/account/tokens") {
                    header(HttpHeaders.Authorization, authHeader)
                    contentType(ContentType.Application.Json)
                    setBody(
                        buildJsonObject {
                            put("name", "never-expires")
                            put("expires_in_days", JsonNull)
                        },
                    )
                }

            assertEquals(HttpStatusCode.Created, response.status)
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            assertEquals(JsonNull, body["expires_at"])
        }

    @Test
    fun `POST returns 400 for expiration outside 1-730 range`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val response =
                client.post("/v1/account/tokens") {
                    header(HttpHeaders.Authorization, authHeader)
                    contentType(ContentType.Application.Json)
                    setBody(CreateApiTokenRequest("too-long", expiresInDays = 1000))
                }

            assertEquals(HttpStatusCode.BadRequest, response.status)
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            assertEquals("api_token.invalid_expiration", body["code"]?.jsonPrimitive?.content)
        }

    @Test
    fun `POST returns 409 api_token_name_taken when name reused while active`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            client.post("/v1/account/tokens") {
                header(HttpHeaders.Authorization, authHeader)
                contentType(ContentType.Application.Json)
                setBody(CreateApiTokenRequest("dup"))
            }
            val second =
                client.post("/v1/account/tokens") {
                    header(HttpHeaders.Authorization, authHeader)
                    contentType(ContentType.Application.Json)
                    setBody(CreateApiTokenRequest("dup"))
                }

            assertEquals(HttpStatusCode.Conflict, second.status)
            val body = Json.parseToJsonElement(second.bodyAsText()).jsonObject
            assertEquals("api_token.name_taken", body["code"]?.jsonPrimitive?.content)
        }

    @Test
    fun `POST returns 409 api_token_limit_reached on the 21st active token`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            repeat(10) { i ->
                val response =
                    client.post("/v1/account/tokens") {
                        header(HttpHeaders.Authorization, authHeader)
                        contentType(ContentType.Application.Json)
                        setBody(CreateApiTokenRequest("token-$i"))
                    }
                assertEquals(HttpStatusCode.Created, response.status, "creating token $i")
            }

            val twentyFirst =
                client.post("/v1/account/tokens") {
                    header(HttpHeaders.Authorization, authHeader)
                    contentType(ContentType.Application.Json)
                    setBody(CreateApiTokenRequest("over-limit"))
                }

            assertEquals(HttpStatusCode.Conflict, twentyFirst.status)
            val body = Json.parseToJsonElement(twentyFirst.bodyAsText()).jsonObject
            assertEquals("api_token.limit_reached", body["code"]?.jsonPrimitive?.content)
        }

    @Test
    fun `DELETE returns 204 then 404 on the second call`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val createResponse =
                client.post("/v1/account/tokens") {
                    header(HttpHeaders.Authorization, authHeader)
                    contentType(ContentType.Application.Json)
                    setBody(CreateApiTokenRequest("to-revoke"))
                }
            val id =
                Json
                    .parseToJsonElement(createResponse.bodyAsText())
                    .jsonObject["id"]!!
                    .jsonPrimitive.content

            val firstDelete =
                client.delete("/v1/account/tokens/$id") {
                    header(HttpHeaders.Authorization, authHeader)
                }
            assertEquals(HttpStatusCode.NoContent, firstDelete.status)

            val secondDelete =
                client.delete("/v1/account/tokens/$id") {
                    header(HttpHeaders.Authorization, authHeader)
                }
            assertEquals(HttpStatusCode.NotFound, secondDelete.status)
        }

    @Test
    fun `DELETE another users token returns 404 without leaking existence`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val createResponse =
                client.post("/v1/account/tokens") {
                    header(HttpHeaders.Authorization, authHeader)
                    contentType(ContentType.Application.Json)
                    setBody(CreateApiTokenRequest("owned-by-user-1"))
                }
            val id =
                Json
                    .parseToJsonElement(createResponse.bodyAsText())
                    .jsonObject["id"]!!
                    .jsonPrimitive.content

            val response =
                client.delete("/v1/account/tokens/$id") {
                    header(HttpHeaders.Authorization, "Bearer ${eu.kasify.TEST_ACCOUNT_2_USER_3.token}")
                }
            assertEquals(HttpStatusCode.NotFound, response.status)
        }

    @Test
    fun `PAT-authenticated request cannot list api tokens`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val createResponse =
                client.post("/v1/account/tokens") {
                    header(HttpHeaders.Authorization, authHeader)
                    contentType(ContentType.Application.Json)
                    setBody(CreateApiTokenRequest("self"))
                }
            val rawToken =
                Json
                    .parseToJsonElement(createResponse.bodyAsText())
                    .jsonObject["token"]!!
                    .jsonPrimitive.content

            val response =
                client.get("/v1/account/tokens") {
                    header(HttpHeaders.Authorization, "Bearer $rawToken")
                }
            assertEquals(HttpStatusCode.Forbidden, response.status)
        }

    @Test
    fun `PAT-authenticated request cannot create api tokens`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val createResponse =
                client.post("/v1/account/tokens") {
                    header(HttpHeaders.Authorization, authHeader)
                    contentType(ContentType.Application.Json)
                    setBody(CreateApiTokenRequest("self2"))
                }
            val rawToken =
                Json
                    .parseToJsonElement(createResponse.bodyAsText())
                    .jsonObject["token"]!!
                    .jsonPrimitive.content

            val response =
                client.post("/v1/account/tokens") {
                    header(HttpHeaders.Authorization, "Bearer $rawToken")
                    contentType(ContentType.Application.Json)
                    setBody(CreateApiTokenRequest("from-pat"))
                }
            assertEquals(HttpStatusCode.Forbidden, response.status)
        }
}
