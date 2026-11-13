package eu.kasify

import com.auth0.jwk.Jwk
import com.auth0.jwk.JwkProvider
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import eu.kasify.security.JWTSecurity
import eu.kasify.security.JWTValidationException
import io.mockk.every
import io.mockk.mockk
import org.junit.Test
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.util.Date
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JWTSecurityTest {
    private fun generateRSAKeyPair(): KeyPair {
        val keyPairGenerator = KeyPairGenerator.getInstance("RSA")
        keyPairGenerator.initialize(2048)
        return keyPairGenerator.generateKeyPair()
    }

    private fun createTestJWT(
        keyPair: KeyPair,
        keyId: String? = "test-key-id",
        userId: UUID? = null,
        accountId: UUID? = null,
        roles: Any? = null,
        rolesAsString: String? = null,
        expiresAt: Date = Date(System.currentTimeMillis() + 3600000),
    ): String {
        val privateKey = keyPair.private as RSAPrivateKey
        val publicKey = keyPair.public as RSAPublicKey
        val algorithm = Algorithm.RSA256(publicKey, privateKey)

        var builder = JWT.create()
        if (keyId != null) builder = builder.withKeyId(keyId)
        builder = builder.withExpiresAt(expiresAt)

        if (userId != null && accountId != null) {
            builder =
                builder.withClaim(
                    "user_properties",
                    mapOf(
                        "k_uid" to mapOf("v" to userId.toString()),
                        "k_aid" to mapOf("v" to accountId.toString()),
                    ),
                )
        }

        if (roles != null) {
            @Suppress("UNCHECKED_CAST")
            builder = builder.withClaim("roles", roles as List<Map<String, Any>>)
        } else if (rolesAsString != null) {
            builder = builder.withClaim("roles", rolesAsString)
        }

        return builder.sign(algorithm)
    }

    private fun createJWTSecurityWithProvider(provider: JwkProvider): JWTSecurity {
        val jwtSecurity = JWTSecurity(skipInit = true)
        val providerField = JWTSecurity::class.java.getDeclaredField("provider")
        providerField.isAccessible = true
        providerField.set(jwtSecurity, provider)
        return jwtSecurity
    }

    // ---- validateToken tests ----

    @Test
    fun `validateToken should throw when keyId is missing`() {
        val keyPair = generateRSAKeyPair()
        val token = createTestJWT(keyPair, keyId = null)
        val jwtSecurity = JWTSecurity(skipInit = true)

        try {
            jwtSecurity.validateToken(token)
            throw AssertionError("Expected JWTValidationException")
        } catch (e: JWTValidationException) {
            assertTrue(e.message!!.contains("Missing keyId"), "Expected 'Missing keyId' but got: ${e.message}")
        }
    }

    @Test
    fun `validateToken should throw when provider returns null public key`() {
        val keyPair = generateRSAKeyPair()
        val token = createTestJWT(keyPair, keyId = "test-key-id")

        val mockJwk = mockk<Jwk>()
        every { mockJwk.publicKey } returns null

        val mockProvider = mockk<JwkProvider>()
        every { mockProvider.get("test-key-id") } returns mockJwk

        val jwtSecurity = createJWTSecurityWithProvider(mockProvider)

        try {
            jwtSecurity.validateToken(token)
            throw AssertionError("Expected JWTValidationException")
        } catch (e: JWTValidationException) {
            assertTrue(
                e.message!!.contains("Unable to retrieve"),
                "Expected 'Unable to retrieve' but got: ${e.message}",
            )
        }
    }

    @Test
    fun `validateToken should throw when signature does not match`() {
        val signingKeyPair = generateRSAKeyPair()
        val verifyingKeyPair = generateRSAKeyPair()
        val token = createTestJWT(signingKeyPair, keyId = "test-key-id")

        val verifyingPublicKey = verifyingKeyPair.public as RSAPublicKey

        val mockJwk = mockk<Jwk>()
        every { mockJwk.publicKey } returns verifyingPublicKey

        val mockProvider = mockk<JwkProvider>()
        every { mockProvider.get("test-key-id") } returns mockJwk

        val jwtSecurity = createJWTSecurityWithProvider(mockProvider)

        try {
            jwtSecurity.validateToken(token)
            throw AssertionError("Expected JWTValidationException")
        } catch (e: JWTValidationException) {
            assertTrue(
                e.message!!.contains("JWT verification failed"),
                "Expected 'JWT verification failed' but got: ${e.message}",
            )
        }
    }

    @Test
    fun `validateToken should return DecodedJWT for valid token`() {
        val keyPair = generateRSAKeyPair()
        val publicKey = keyPair.public as RSAPublicKey
        val token = createTestJWT(keyPair, keyId = "test-key-id")

        val mockJwk = mockk<Jwk>()
        every { mockJwk.publicKey } returns publicKey

        val mockProvider = mockk<JwkProvider>()
        every { mockProvider.get("test-key-id") } returns mockJwk

        val jwtSecurity = createJWTSecurityWithProvider(mockProvider)

        val result = jwtSecurity.validateToken(token)
        assertNotNull(result)
        assertEquals("test-key-id", result.keyId)
    }

    // ---- getUserInfo tests ----

    @Test
    fun `getUserInfo should extract user properties and roles from decoded JWT`() {
        val keyPair = generateRSAKeyPair()
        val userId = UUID.randomUUID()
        val accountId = UUID.randomUUID()
        val roles =
            listOf(
                mapOf("id" to "role1", "key" to "admin", "name" to "Administrator"),
                mapOf("id" to "role2", "key" to "user", "name" to "User"),
            )
        val token = createTestJWT(keyPair, userId = userId, accountId = accountId, roles = roles)
        val decodedJWT = JWT.decode(token)
        val jwtSecurity = JWTSecurity(skipInit = true)

        val result = jwtSecurity.getUserInfo(decodedJWT)

        assertNotNull(result)
        assertEquals(userId, result.userId)
        assertEquals(accountId, result.accountId)
        assertEquals(2, result.roles.size)
        assertEquals("admin", result.roles[0].key)
        assertEquals("user", result.roles[1].key)
    }

    @Test
    fun `getUserInfo should return null when user_properties are missing`() {
        val keyPair = generateRSAKeyPair()
        val token = createTestJWT(keyPair)
        val decodedJWT = JWT.decode(token)
        val jwtSecurity = JWTSecurity(skipInit = true)

        val result = jwtSecurity.getUserInfo(decodedJWT)

        assertNull(result)
    }

    @Test
    fun `getUserInfo should handle empty roles list`() {
        val keyPair = generateRSAKeyPair()
        val userId = UUID.randomUUID()
        val accountId = UUID.randomUUID()
        val token = createTestJWT(keyPair, userId = userId, accountId = accountId)
        val decodedJWT = JWT.decode(token)
        val jwtSecurity = JWTSecurity(skipInit = true)

        val result = jwtSecurity.getUserInfo(decodedJWT)

        assertNotNull(result)
        assertEquals(0, result.roles.size)
    }

    @Test
    fun `getUserInfo should parse roles from JSON string`() {
        val keyPair = generateRSAKeyPair()
        val userId = UUID.randomUUID()
        val accountId = UUID.randomUUID()
        val rolesJson = """[{"id":"r1","key":"owner","name":"Owner"}]"""
        val token = createTestJWT(keyPair, userId = userId, accountId = accountId, rolesAsString = rolesJson)
        val decodedJWT = JWT.decode(token)
        val jwtSecurity = JWTSecurity(skipInit = true)

        val result = jwtSecurity.getUserInfo(decodedJWT)

        assertNotNull(result)
        assertEquals(1, result.roles.size)
        assertEquals("owner", result.roles[0].key)
        assertEquals("Owner", result.roles[0].name)
    }

    @Test
    fun `getUserInfo should return empty roles for invalid JSON roles string`() {
        val keyPair = generateRSAKeyPair()
        val userId = UUID.randomUUID()
        val accountId = UUID.randomUUID()
        val token = createTestJWT(keyPair, userId = userId, accountId = accountId, rolesAsString = "not-valid-json")
        val decodedJWT = JWT.decode(token)
        val jwtSecurity = JWTSecurity(skipInit = true)

        val result = jwtSecurity.getUserInfo(decodedJWT)

        assertNotNull(result)
        assertEquals(0, result.roles.size)
    }

    @Test
    fun `getUserInfo should return null when k_aid is missing`() {
        val keyPair = generateRSAKeyPair()
        val privateKey = keyPair.private as RSAPrivateKey
        val publicKey = keyPair.public as RSAPublicKey
        val algorithm = Algorithm.RSA256(publicKey, privateKey)

        val token =
            JWT
                .create()
                .withKeyId("test-key-id")
                .withClaim(
                    "user_properties",
                    mapOf(
                        "k_uid" to mapOf("v" to UUID.randomUUID().toString()),
                    ),
                ).withExpiresAt(Date(System.currentTimeMillis() + 3600000))
                .sign(algorithm)

        val decodedJWT = JWT.decode(token)
        val jwtSecurity = JWTSecurity(skipInit = true)

        assertNull(jwtSecurity.getUserInfo(decodedJWT))
    }

    @Test
    fun `getUserInfo should return null when k_uid is missing`() {
        val keyPair = generateRSAKeyPair()
        val privateKey = keyPair.private as RSAPrivateKey
        val publicKey = keyPair.public as RSAPublicKey
        val algorithm = Algorithm.RSA256(publicKey, privateKey)

        val token =
            JWT
                .create()
                .withKeyId("test-key-id")
                .withClaim(
                    "user_properties",
                    mapOf(
                        "k_aid" to mapOf("v" to UUID.randomUUID().toString()),
                    ),
                ).withExpiresAt(Date(System.currentTimeMillis() + 3600000))
                .sign(algorithm)

        val decodedJWT = JWT.decode(token)
        val jwtSecurity = JWTSecurity(skipInit = true)

        assertNull(jwtSecurity.getUserInfo(decodedJWT))
    }

    @Test
    fun `getUserInfo should return null for invalid UUID in user properties`() {
        val keyPair = generateRSAKeyPair()
        val privateKey = keyPair.private as RSAPrivateKey
        val publicKey = keyPair.public as RSAPublicKey
        val algorithm = Algorithm.RSA256(publicKey, privateKey)

        val token =
            JWT
                .create()
                .withKeyId("test-key-id")
                .withClaim(
                    "user_properties",
                    mapOf(
                        "k_uid" to mapOf("v" to "not-a-uuid"),
                        "k_aid" to mapOf("v" to "also-not-a-uuid"),
                    ),
                ).withExpiresAt(Date(System.currentTimeMillis() + 3600000))
                .sign(algorithm)

        val decodedJWT = JWT.decode(token)
        val jwtSecurity = JWTSecurity(skipInit = true)

        assertNull(jwtSecurity.getUserInfo(decodedJWT))
    }
}
