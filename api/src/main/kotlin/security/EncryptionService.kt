package eu.kasify.security

import io.github.oshai.kotlinlogging.KotlinLogging
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

private val logger = KotlinLogging.logger {}

/**
 * Service for encrypting and decrypting sensitive data (passwords, API tokens)
 * using AES-256-GCM (Galois/Counter Mode) authenticated encryption.
 *
 * Security features:
 * - AES-256-GCM provides both confidentiality and integrity
 * - Unique IV (initialization vector) for each encryption
 * - Authenticated encryption prevents tampering
 * - Constant-time operations to prevent timing attacks
 *
 * Key Management:
 * - Encryption key MUST be stored securely
 * - Key should be 256 bits (32 bytes) base64-encoded
 * - Generate key using: generateEncryptionKey()
 *
 * Key can be provided in two ways:
 * 1. Constructor parameter (useful for testing): EncryptionService(key = "your-base64-key")
 * 2. Environment variable (production): ENCRYPTION_KEY=<base64-key>
 *
 * Usage:
 * ```kotlin
 * // Production: uses ENCRYPTION_KEY from environment
 * val service = EncryptionService()
 *
 * // Testing: provide key directly
 * val testService = EncryptionService(key = "test-key-base64")
 *
 * val encrypted = service.encrypt("sensitive-password")
 * val decrypted = service.decrypt(encrypted)
 * ```
 *
 * @param key Optional base64-encoded 256-bit encryption key. If null, reads from ENCRYPTION_KEY env var.
 */
class EncryptionService(
    val key: String? = null,
) {
    private val algorithm = "AES/GCM/NoPadding"
    private val gcmTagLength = 128 // bits
    private val ivLength = 12 // bytes (96 bits recommended for GCM)

    private val secretKey: SecretKey by lazy {
        // Use constructor parameter if provided, otherwise fall back to environment variable
        val keyString =
            key ?: System.getenv("ENCRYPTION_KEY")
                ?: throw IllegalStateException(
                    "Encryption key not provided. Either pass key to constructor or set " +
                        "ENCRYPTION_KEY environment variable. " +
                        "Generate a key using EncryptionService.generateEncryptionKey()",
                )

        try {
            val keyBytes = Base64.getDecoder().decode(keyString)
            if (keyBytes.size != 32) {
                throw IllegalArgumentException("Encryption key must be 256 bits (32 bytes)")
            }
            SecretKeySpec(keyBytes, "AES")
        } catch (e: Exception) {
            logger.error(e) { "Failed to load encryption key" }
            throw IllegalStateException("Invalid ENCRYPTION_KEY format. Must be base64-encoded 256-bit key", e)
        }
    }

    /**
     * Encrypts sensitive data using AES-256-GCM.
     *
     * @param plaintext The data to encrypt (password, API token, etc.)
     * @return Base64-encoded string containing IV + ciphertext + authentication tag
     * @throws IllegalStateException if encryption fails
     */
    fun encrypt(plaintext: String): String {
        try {
            // Generate random IV for this encryption
            val iv = ByteArray(ivLength)
            SecureRandom().nextBytes(iv)

            // Initialize cipher with GCM parameters
            val cipher = Cipher.getInstance(algorithm)
            val gcmSpec = GCMParameterSpec(gcmTagLength, iv)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec)

            // Encrypt the data
            val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

            // Combine IV + ciphertext for storage (IV is not secret, just needs to be unique)
            val combined = iv + ciphertext

            // Return base64-encoded result
            return Base64.getEncoder().encodeToString(combined)
        } catch (e: Exception) {
            logger.error(e) { "Encryption failed" }
            throw IllegalStateException("Failed to encrypt data", e)
        }
    }

    /**
     * Decrypts data that was encrypted with encrypt().
     *
     * @param encryptedData Base64-encoded string containing IV + ciphertext + auth tag
     * @return Decrypted plaintext
     * @throws IllegalStateException if decryption fails (wrong key, tampered data, etc.)
     */
    fun decrypt(encryptedData: String): String {
        try {
            // Decode from base64
            val combined = Base64.getDecoder().decode(encryptedData)

            // Extract IV and ciphertext
            val iv = combined.sliceArray(0 until ivLength)
            val ciphertext = combined.sliceArray(ivLength until combined.size)

            // Initialize cipher for decryption
            val cipher = Cipher.getInstance(algorithm)
            val gcmSpec = GCMParameterSpec(gcmTagLength, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)

            // Decrypt and verify authentication tag
            val plaintext = cipher.doFinal(ciphertext)

            return String(plaintext, Charsets.UTF_8)
        } catch (e: Exception) {
            logger.error(e) { "Decryption failed - data may be corrupted or tampered with" }
            throw IllegalStateException("Failed to decrypt data", e)
        }
    }

    companion object {
        /**
         * Generates a new 256-bit encryption key suitable for AES-256.
         *
         * IMPORTANT: Run this once, store the output securely, and set as ENCRYPTION_KEY env var.
         *
         * Usage:
         * ```bash
         * # Generate key
         * ./gradlew run --args="generate-key"
         *
         * # Add to .env file
         * echo "ENCRYPTION_KEY=<generated-key>" >> .env
         * ```
         *
         * @return Base64-encoded 256-bit key
         */
        fun generateEncryptionKey(): String {
            val keyGenerator = KeyGenerator.getInstance("AES")
            keyGenerator.init(256, SecureRandom())
            val key = keyGenerator.generateKey()
            return Base64.getEncoder().encodeToString(key.encoded)
        }

        /**
         * Validates that an encryption key is properly formatted.
         *
         * @param base64Key The base64-encoded key to validate
         * @return true if valid, false otherwise
         */
        fun isValidKey(base64Key: String): Boolean =
            try {
                val keyBytes = Base64.getDecoder().decode(base64Key)
                keyBytes.size == 32 // 256 bits
            } catch (e: Exception) {
                false
            }
    }
}
