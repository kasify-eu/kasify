package eu.kasify.routes

import eu.kasify.TEST_ACCOUNT_1_USER_1
import eu.kasify.TEST_ACCOUNT_1_USER_2
import eu.kasify.TEST_ACCOUNT_2_USER_3
import eu.kasify.extensions.configureTestModules
import eu.kasify.extensions.createClient
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
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TelegramRouteTest : BaseRouteDBTests() {
    private fun startLinkAndExtractToken(body: String): String {
        val deepLink =
            Json
                .parseToJsonElement(body)
                .jsonObject["deep_link"]!!
                .jsonPrimitive.content
        assertTrue(deepLink.startsWith("https://t.me/KasifyBot?start="), "unexpected deep link: $deepLink")
        return deepLink.substringAfter("start=")
    }

    @Test
    fun `start link returns a bot deep link`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val response =
                client.post("/v1/account/telegram/link") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                }

            assertEquals(HttpStatusCode.OK, response.status)
            assertTrue(startLinkAndExtractToken(response.bodyAsText()).isNotBlank())
        }

    @Test
    fun `status is none before linking`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val response =
                client.get("/v1/account/telegram") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_2.token}")
                }

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(
                "none",
                Json
                    .parseToJsonElement(response.bodyAsText())
                    .jsonObject["status"]!!
                    .jsonPrimitive.content,
            )
        }

    @Test
    fun `webhook with a bad secret is rejected`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val response =
                client.post("/v1/telegram/webhook") {
                    header("X-Telegram-Bot-Api-Secret-Token", "wrong-secret")
                    contentType(ContentType.Application.Json)
                    setBody("""{"update_id":1,"message":{"message_id":1,"chat":{"id":1},"text":"/start abc"}}""")
                }

            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }

    @Test
    fun `webhook start with a valid token links the chat`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            // 1. Start linking to obtain a one-time token.
            val linkBody =
                client
                    .post("/v1/account/telegram/link") {
                        header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                    }.bodyAsText()
            val token = startLinkAndExtractToken(linkBody)

            // 2. Telegram calls the webhook with the correct secret and a /start <token> message.
            val webhook =
                client.post("/v1/telegram/webhook") {
                    header("X-Telegram-Bot-Api-Secret-Token", "test-secret")
                    contentType(ContentType.Application.Json)
                    setBody(
                        """{"update_id":2,"message":{"message_id":5,"chat":{"id":4242,"username":"alice"},"text":"/start $token"}}""",
                    )
                }
            assertEquals(HttpStatusCode.OK, webhook.status)

            // 3. The user is now linked.
            val statusBody =
                client
                    .get("/v1/account/telegram") {
                        header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_1_USER_1.token}")
                    }.bodyAsText()
            val status = Json.parseToJsonElement(statusBody).jsonObject
            assertEquals("linked", status["status"]!!.jsonPrimitive.content)
            assertEquals("alice", status["telegram_username"]!!.jsonPrimitive.content)
        }

    @Test
    fun `unlink without a link returns not_linked`() =
        testApplication {
            application { configureTestModules() }
            val client = createClient()

            val response =
                client.delete("/v1/account/telegram") {
                    header(HttpHeaders.Authorization, "Bearer ${TEST_ACCOUNT_2_USER_3.token}")
                }

            assertEquals(HttpStatusCode.NotFound, response.status)
            assertTrue(response.bodyAsText().contains("telegram.not_linked"))
        }
}
