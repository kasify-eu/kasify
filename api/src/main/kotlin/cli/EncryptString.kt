package eu.kasify.cli

import eu.kasify.security.EncryptionService

/**
 * CLI utility to encrypt strings using the application's encryption service.
 *
 * Usage:
 * ```bash
 * # Using Gradle task (recommended)
 * ./gradlew encryptString -Dexec.args="my-secret-password"
 *
 * # Or with spaces in the string
 * ./gradlew encryptString -Dexec.args="my secret password"
 *
 * # Or compile and run directly
 * ./gradlew build
 * java -cp build/libs/api-all.jar eu.kasify.cli.EncryptStringKt "my-secret-password"
 * ```
 *
 * Requirements:
 * - ENCRYPTION_KEY environment variable must be set
 * - Key should be generated using GenerateEncryptionKey utility
 *
 * The encrypted string can be stored in the database and decrypted later
 * using EncryptionService.decrypt()
 */
fun main(args: Array<String>) {
    println("=".repeat(70))
    println("String Encryption Utility")
    println("=".repeat(70))
    println()

    // Check if plaintext was provided
    if (args.isEmpty()) {
        println("❌ Error: No string provided to encrypt")
        println()
        println("Usage:")
        println("  ./gradlew encryptString -Dexec.args=\"your-secret-text\"")
        println()
        println("Example:")
        println("  ./gradlew encryptString -Dexec.args=\"myApiKey123\"")
        println()
        return
    }

    // Get the plaintext (join all args in case there are spaces)
    val plaintext = args.joinToString(" ")

    try {
        // Initialize encryption service (will use ENCRYPTION_KEY env var)
        val encryptionService = EncryptionService()

        // Encrypt the string
        val encrypted = encryptionService.encrypt(plaintext)

        println("✅ Successfully encrypted string:")
        println()
        println("    $encrypted")
        println()
        println("ℹ️  Information:")
        println()
        println("• This encrypted value can be stored in the database")
        println("• Use EncryptionService.decrypt() to retrieve the original value")
        println("• The same ENCRYPTION_KEY must be used for decryption")
        println("• Each encryption produces a different output (unique IV)")
        println()
        println("=".repeat(70))
        println()

        // Test decryption to verify it works
        val decrypted = encryptionService.decrypt(encrypted)
        if (decrypted == plaintext) {
            println("✅ Verification: Encryption/decryption working correctly")
        } else {
            println("⚠️  Warning: Decryption test failed")
        }
    } catch (e: IllegalStateException) {
        println("❌ Encryption failed!")
        println()
        println("Error: ${e.message}")
        println()
        println("Make sure:")
        println("1. ENCRYPTION_KEY environment variable is set")
        println("2. Key is valid (use generateEncryptionKey task to create one)")
        println()
        println("Generate a key:")
        println("  ./gradlew generateEncryptionKey")
        println()
    } catch (e: Exception) {
        println("❌ Unexpected error: ${e.message}")
        e.printStackTrace()
    }
}
