package eu.kasify.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.interfaces.DecodedJWT
import eu.kasify.ErrorResponse
import eu.kasify.configureStatusPages
import eu.kasify.configureValidation
import eu.kasify.db.BookingAccessCodesRepository
import eu.kasify.db.BookingsRepository
import eu.kasify.db.CalendarEventsRepository
import eu.kasify.db.GuestsRepository
import eu.kasify.db.IntegrationsRepository
import eu.kasify.db.PropertiesRepository
import eu.kasify.db.PropertyAccessRepository
import eu.kasify.db.PropertyChannelIntegrationsRepository
import eu.kasify.db.PropertyInvitationsRepository
import eu.kasify.db.SmartlocksRepository
import eu.kasify.db.UsersRepository
import eu.kasify.extensions.TEST_ENCRYPTION_KEY
import eu.kasify.extensions.createClient
import eu.kasify.mocks.MockPluginFactory
import eu.kasify.model.api.Account
import eu.kasify.model.api.requests.SignupRequest
import eu.kasify.plugins.PluginFactory
import eu.kasify.security.EncryptionService
import eu.kasify.security.JWTSecurity
import eu.kasify.security.JWTValidationException
import eu.kasify.services.AuthorizationService
import eu.kasify.services.KindeM2MTokenService
import eu.kasify.services.SmartlocksService
import io.ktor.client.request.cookie
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import org.junit.Test
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.util.Date
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class SignupRouteTest : BaseRouteDBTests() {
    // Generate a test RSA key pair for creating signed JWTs
    private val keyPair =
        KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    private val privateKey = keyPair.private as RSAPrivateKey
    private val publicKey = keyPair.public as RSAPublicKey
    private val algorithm = Algorithm.RSA256(publicKey, privateKey)

    private fun createTestToken(
        subject: String,
        email: String?,
    ): String {
        val builder =
            JWT
                .create()
                .withKeyId("test-key-id")
                .withSubject(subject)
                .withExpiresAt(Date(System.currentTimeMillis() + 3600000))

        if (email != null) {
            builder.withClaim("email", email)
        }

        return builder.sign(algorithm)
    }

    private fun Application.configureSignupTestModules(mockJwtSecurity: JWTSecurity) {
        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                },
            )
        }

        install(Koin) {
            modules(
                module {
                    single<PropertiesRepository> { PropertiesRepository() }
                    single<CalendarEventsRepository> { CalendarEventsRepository() }
                    single<BookingsRepository> { BookingsRepository() }
                    single<PropertyAccessRepository> { PropertyAccessRepository() }
                    single<AuthorizationService> { AuthorizationService(get(), get()) }
                    single<PropertyChannelIntegrationsRepository> { PropertyChannelIntegrationsRepository() }
                    single<EncryptionService> { EncryptionService(key = TEST_ENCRYPTION_KEY) }
                    single<IntegrationsRepository> { IntegrationsRepository(get(), get()) }
                    single<SmartlocksRepository> { SmartlocksRepository() }
                    single<BookingAccessCodesRepository> { BookingAccessCodesRepository() }
                    single<PluginFactory> { MockPluginFactory() }
                    single<SmartlocksService> { SmartlocksService(get(), get(), get()) }
                    single<UsersRepository> { UsersRepository() }
                    single<PropertyInvitationsRepository> { PropertyInvitationsRepository() }
                    single<GuestsRepository> { GuestsRepository() }
                    // Use the mock JWTSecurity
                    single<JWTSecurity> { mockJwtSecurity }
                    // Mock KindeM2MTokenService - returns a fake token
                    single<KindeM2MTokenService> {
                        MockKindeM2MTokenService()
                    }
                },
            )
        }

        configureStatusPages()
        configureValidation()
        configureAuthRouting()
    }

    // Mock KindeM2MTokenService that returns a fake token
    class MockKindeM2MTokenService : KindeM2MTokenService("https://mock.kinde.com", "mock-client", "mock-secret") {
        override suspend fun getToken(refreshBufferSeconds: Long): String = "mock-m2m-token"
    }

    // Mock JWTSecurity that validates tokens based on the tokensToAccept set
    inner class MockJWTSecurity(
        private val tokensToAccept: Set<String>,
    ) : JWTSecurity(skipInit = true) {
        override fun validateToken(token: String): DecodedJWT {
            if (token in tokensToAccept) {
                return JWT.decode(token)
            }
            throw JWTValidationException("Invalid token for testing")
        }
    }

    // ============================================================
    // MISSING COOKIES TESTS - POST /signup
    // ============================================================

    @Test
    fun `Signup fails when access_token cookie is missing`() =
        testApplication {
            val mockJwtSecurity = MockJWTSecurity(emptySet())
            application { configureSignupTestModules(mockJwtSecurity) }
            val client = createClient()

            val response =
                client.post("/v1/signup") {
                    contentType(ContentType.Application.Json)
                    setBody(SignupRequest(name = "John", lastname = "Doe"))
                    // No cookies set
                }

            assertEquals(HttpStatusCode.BadRequest, response.status)
            val responseText = response.bodyAsText()
            assertContains(responseText, "Missing access_token or id_token cookie")
        }

    @Test
    fun `Signup fails when id_token cookie is missing`() =
        testApplication {
            val mockJwtSecurity = MockJWTSecurity(emptySet())
            application { configureSignupTestModules(mockJwtSecurity) }
            val client = createClient()

            val response =
                client.post("/v1/signup") {
                    contentType(ContentType.Application.Json)
                    setBody(SignupRequest(name = "John", lastname = "Doe"))
                    cookie("access_token", "some-token")
                    // id_token missing
                }

            assertEquals(HttpStatusCode.BadRequest, response.status)
            val responseText = response.bodyAsText()
            assertContains(responseText, "Missing access_token or id_token cookie")
        }

    @Test
    fun `Signup fails when access_token cookie is missing but id_token present`() =
        testApplication {
            val mockJwtSecurity = MockJWTSecurity(emptySet())
            application { configureSignupTestModules(mockJwtSecurity) }
            val client = createClient()

            val response =
                client.post("/v1/signup") {
                    contentType(ContentType.Application.Json)
                    setBody(SignupRequest(name = "John", lastname = "Doe"))
                    cookie("id_token", "some-id-token")
                    // access_token missing
                }

            assertEquals(HttpStatusCode.BadRequest, response.status)
            val responseText = response.bodyAsText()
            assertContains(responseText, "Missing access_token or id_token cookie")
        }

    // ============================================================
    // INVALID TOKEN TESTS - POST /signup
    // ============================================================

    @Test
    fun `Signup fails with invalid access_token`() =
        testApplication {
            val idToken = createTestToken("kp_test_user", "test@example.com")
            val mockJwtSecurity = MockJWTSecurity(setOf(idToken)) // Only id_token is valid
            application { configureSignupTestModules(mockJwtSecurity) }
            val client = createClient()

            val response =
                client.post("/v1/signup") {
                    contentType(ContentType.Application.Json)
                    setBody(SignupRequest(name = "John", lastname = "Doe"))
                    cookie("access_token", "invalid-token")
                    cookie("id_token", idToken)
                }

            assertEquals(HttpStatusCode.Unauthorized, response.status)
            val responseText = response.bodyAsText()
            assertContains(responseText, "Invalid or expired token")
        }

    @Test
    fun `Signup fails with invalid id_token`() =
        testApplication {
            val accessToken = createTestToken("kp_test_user", null)
            val mockJwtSecurity = MockJWTSecurity(setOf(accessToken)) // Only access_token is valid
            application { configureSignupTestModules(mockJwtSecurity) }
            val client = createClient()

            val response =
                client.post("/v1/signup") {
                    contentType(ContentType.Application.Json)
                    setBody(SignupRequest(name = "John", lastname = "Doe"))
                    cookie("access_token", accessToken)
                    cookie("id_token", "invalid-token")
                }

            assertEquals(HttpStatusCode.Unauthorized, response.status)
            val responseText = response.bodyAsText()
            assertContains(responseText, "Invalid or expired token")
        }

    // ============================================================
    // MISSING CLAIMS TESTS - POST /signup
    // ============================================================

    @Test
    fun `Signup fails when subject claim is missing`() =
        testApplication {
            // Create a token without subject
            val tokenWithoutSubject =
                JWT
                    .create()
                    .withKeyId("test-key-id")
                    .withClaim("email", "test@example.com")
                    .withExpiresAt(Date(System.currentTimeMillis() + 3600000))
                    .sign(algorithm)

            val mockJwtSecurity = MockJWTSecurity(setOf(tokenWithoutSubject))
            application { configureSignupTestModules(mockJwtSecurity) }
            val client = createClient()

            val response =
                client.post("/v1/signup") {
                    contentType(ContentType.Application.Json)
                    setBody(SignupRequest(name = "John", lastname = "Doe"))
                    cookie("access_token", tokenWithoutSubject)
                    cookie("id_token", tokenWithoutSubject)
                }

            assertEquals(HttpStatusCode.BadRequest, response.status)
            val responseText = response.bodyAsText()
            assertContains(responseText, "Invalid token: missing required user information")
        }

    @Test
    fun `Signup fails when email claim is missing`() =
        testApplication {
            val tokenWithoutEmail = createTestToken("kp_test_user", null)
            val mockJwtSecurity = MockJWTSecurity(setOf(tokenWithoutEmail))
            application { configureSignupTestModules(mockJwtSecurity) }
            val client = createClient()

            val response =
                client.post("/v1/signup") {
                    contentType(ContentType.Application.Json)
                    setBody(SignupRequest(name = "John", lastname = "Doe"))
                    cookie("access_token", tokenWithoutEmail)
                    cookie("id_token", tokenWithoutEmail)
                }

            assertEquals(HttpStatusCode.BadRequest, response.status)
            val responseText = response.bodyAsText()
            assertContains(responseText, "Invalid token: missing required user information")
        }

    // ============================================================
    // REQUEST VALIDATION TESTS - POST /signup
    // ============================================================

    @Test
    fun `Signup fails when request body is missing`() =
        testApplication {
            val token = createTestToken("kp_test_user", "test@example.com")
            val mockJwtSecurity = MockJWTSecurity(setOf(token))
            application { configureSignupTestModules(mockJwtSecurity) }
            val client = createClient()

            val response =
                client.post("/v1/signup") {
                    contentType(ContentType.Application.Json)
                    cookie("access_token", token)
                    cookie("id_token", token)
                    setBody("{}") // Empty JSON body - missing required fields
                }

            assertEquals(HttpStatusCode.BadRequest, response.status)
        }

    @Test
    fun `Signup fails when name is missing in request`() =
        testApplication {
            val token = createTestToken("kp_test_user", "test@example.com")
            val mockJwtSecurity = MockJWTSecurity(setOf(token))
            application { configureSignupTestModules(mockJwtSecurity) }
            val client = createClient()

            val response =
                client.post("/v1/signup") {
                    contentType(ContentType.Application.Json)
                    cookie("access_token", token)
                    cookie("id_token", token)
                    setBody("""{"last_name": "Doe"}""")
                }

            assertEquals(HttpStatusCode.BadRequest, response.status)
            val responseText = response.bodyAsText()
            assertContains(responseText, "name")
        }

    @Test
    fun `Signup fails when last_name is missing in request`() =
        testApplication {
            val token = createTestToken("kp_test_user", "test@example.com")
            val mockJwtSecurity = MockJWTSecurity(setOf(token))
            application { configureSignupTestModules(mockJwtSecurity) }
            val client = createClient()

            val response =
                client.post("/v1/signup") {
                    contentType(ContentType.Application.Json)
                    cookie("access_token", token)
                    cookie("id_token", token)
                    setBody("""{"name": "John"}""")
                }

            assertEquals(HttpStatusCode.BadRequest, response.status)
            val responseText = response.bodyAsText()
            assertContains(responseText, "last_name")
        }

    // ============================================================
    // SUCCESS TESTS - POST /signup
    // ============================================================

    @Test
    fun `Signup creates new user successfully`() =
        testApplication {
            val externalUserId = "kp_new_user_${System.currentTimeMillis()}"
            val email = "newuser_${System.currentTimeMillis()}@example.com"
            val token = createTestToken(externalUserId, email)
            val mockJwtSecurity = MockJWTSecurity(setOf(token))
            application { configureSignupTestModules(mockJwtSecurity) }
            val client = createClient()

            val response =
                client.post("/v1/signup") {
                    contentType(ContentType.Application.Json)
                    cookie("access_token", token)
                    cookie("id_token", token)
                    setBody(SignupRequest(name = "John", lastname = "Doe"))
                }

            assertEquals(HttpStatusCode.OK, response.status)
            val responseText = response.bodyAsText()
            // Response should contain UserInfo with id and account_id
            assertContains(responseText, "id")
            assertContains(responseText, "account_id")

            // Parse response to verify structure
            val userInfo = Json.decodeFromString<Account>(responseText)
            assertNotNull(userInfo.id)
            assertNotNull(userInfo.accountId)
        }

    @Test
    fun `Signup returns existing user when already registered`() =
        testApplication {
            val externalUserId = "kp_existing_user_${System.currentTimeMillis()}"
            val email = "existing_${System.currentTimeMillis()}@example.com"
            val token = createTestToken(externalUserId, email)
            val mockJwtSecurity = MockJWTSecurity(setOf(token))
            application { configureSignupTestModules(mockJwtSecurity) }
            val client = createClient()

            // First signup - should create user
            val firstResponse =
                client.post("/v1/signup") {
                    contentType(ContentType.Application.Json)
                    cookie("access_token", token)
                    cookie("id_token", token)
                    setBody(SignupRequest(name = "John", lastname = "Doe"))
                }

            assertEquals(HttpStatusCode.OK, firstResponse.status)
            val firstUserInfo = Json.decodeFromString<Account>(firstResponse.bodyAsText())

            // Second signup with same token - should return existing user
            val secondResponse =
                client.post("/v1/signup") {
                    contentType(ContentType.Application.Json)
                    cookie("access_token", token)
                    cookie("id_token", token)
                    setBody(SignupRequest(name = "Jane", lastname = "Smith")) // Different name
                }

            assertEquals(HttpStatusCode.OK, secondResponse.status)
            val secondUserInfo = Json.decodeFromString<Account>(secondResponse.bodyAsText())

            // Should be the same user
            assertEquals(firstUserInfo.id, secondUserInfo.id)
            assertEquals(firstUserInfo.accountId, secondUserInfo.accountId)
        }

    // ============================================================
    // ERROR RESPONSE FORMAT TESTS
    // ============================================================

    @Test
    fun `Error responses use standard ErrorResponse format`() =
        testApplication {
            val mockJwtSecurity = MockJWTSecurity(emptySet())
            application { configureSignupTestModules(mockJwtSecurity) }
            val client = createClient()

            val response =
                client.post("/v1/signup") {
                    contentType(ContentType.Application.Json)
                    setBody(SignupRequest(name = "John", lastname = "Doe"))
                    // No cookies - will trigger BadRequest
                }

            assertEquals(HttpStatusCode.BadRequest, response.status)
            val responseText = response.bodyAsText()

            // Verify the response uses ErrorResponse format (has code, message and timestamp fields)
            val errorResponse = Json.decodeFromString<ErrorResponse>(responseText)
            assertNotNull(errorResponse.code)
            assertNotNull(errorResponse.message)
            assertNotNull(errorResponse.timestamp)
            assertContains(errorResponse.message, "Missing access_token or id_token cookie")
        }

    @Test
    fun `Unauthorized error uses standard ErrorResponse format`() =
        testApplication {
            val idToken = createTestToken("kp_test_user", "test@example.com")
            val mockJwtSecurity = MockJWTSecurity(emptySet()) // Reject all tokens
            application { configureSignupTestModules(mockJwtSecurity) }
            val client = createClient()

            val response =
                client.post("/v1/signup") {
                    contentType(ContentType.Application.Json)
                    setBody(SignupRequest(name = "John", lastname = "Doe"))
                    cookie("access_token", idToken)
                    cookie("id_token", idToken)
                }

            assertEquals(HttpStatusCode.Unauthorized, response.status)
            val responseText = response.bodyAsText()

            // Verify the response uses ErrorResponse format
            val errorResponse = Json.decodeFromString<ErrorResponse>(responseText)
            assertNotNull(errorResponse.code)
            assertNotNull(errorResponse.message)
            assertNotNull(errorResponse.timestamp)
            assertEquals("auth.invalid_token", errorResponse.code)
            assertContains(errorResponse.message, "Invalid or expired token")
        }
}
