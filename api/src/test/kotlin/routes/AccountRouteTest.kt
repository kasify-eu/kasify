package eu.kasify.routes

import eu.kasify.TEST_ACCOUNT_1_USER_1
import eu.kasify.extensions.configureTestModules
import eu.kasify.extensions.createClient
import eu.kasify.model.api.UpdateAccountRequest
import eu.kasify.model.email.Locale
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import kotlin.test.assertEquals

class AccountRouteTest : BaseRouteDBTests() {
    @Test
    fun `GET account returns the seeded default locale`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val response =
                client.get("/v1/account") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                }

            assertEquals(HttpStatusCode.OK, response.status)
            val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            // The users table defaults locale to 'es'.
            assertEquals("es", json["locale"]!!.jsonPrimitive.content)
        }

    @Test
    fun `PUT account updates the preferred locale and persists it`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val putResponse =
                client.put("/v1/account") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                    contentType(ContentType.Application.Json)
                    setBody(UpdateAccountRequest(name = "Ana", lastName = "Owner", locale = Locale.EN))
                }

            assertEquals(HttpStatusCode.OK, putResponse.status)
            val putJson = Json.parseToJsonElement(putResponse.bodyAsText()).jsonObject
            assertEquals("en", putJson["locale"]!!.jsonPrimitive.content)
            assertEquals("Ana", putJson["name"]!!.jsonPrimitive.content)

            // A subsequent GET reflects the persisted value.
            val getResponse =
                client.get("/v1/account") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                }
            val getJson = Json.parseToJsonElement(getResponse.bodyAsText()).jsonObject
            assertEquals("en", getJson["locale"]!!.jsonPrimitive.content)
        }

    @Test
    fun `PUT account without locale leaves the existing locale untouched`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            // Omitting locale (older clients) must not reset it — it stays at the seeded 'es'.
            val response =
                client.put("/v1/account") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                    contentType(ContentType.Application.Json)
                    setBody(UpdateAccountRequest(name = "Ana", lastName = "Owner"))
                }

            assertEquals(HttpStatusCode.OK, response.status)
            val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            assertEquals("es", json["locale"]!!.jsonPrimitive.content)
        }
}
