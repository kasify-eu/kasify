package eu.kasify.cli

import eu.kasify.security.EncryptionService

/**
 * CLI utility to generate a secure encryption key for the application.
 *
 * Usage:
 * ```bash
 * # Using Gradle
 * ./gradlew run --args="generate-key"
 *
 * # Or compile and run
 * ./gradlew build
 * java -cp build/libs/api-all.jar eu.kasify.cli.GenerateEncryptionKeyKt
 * ```
 *
 * The generated key should be:
 * 1. Stored securely (never commit to git)
 * 2. Added to .env file: ENCRYPTION_KEY=<generated-key>
 * 3. Set in production environment variables
 */
fun main() {
    println("=".repeat(70))
    println("Generating AES-256 Encryption Key")
    println("=".repeat(70))
    println()

    val key = EncryptionService.generateEncryptionKey()

    println("✅ Successfully generated 256-bit encryption key:")
    println()
    println("    $key")
    println()
    println("⚠️  IMPORTANT SECURITY INSTRUCTIONS:")
    println()
    println("1. Copy this key to your .env file:")
    println("   ENCRYPTION_KEY=$key")
    println()
    println("2. Add .env to .gitignore (if not already)")
    println()
    println("3. For production, set as environment variable:")
    println("   export ENCRYPTION_KEY=$key")
    println()
    println("4. NEVER commit this key to version control!")
    println()
    println("5. Use the same key across all instances (staging, production)")
    println("   or you won't be able to decrypt existing data")
    println()
    println("=".repeat(70))
    println()

    // Validate the generated key
    if (EncryptionService.isValidKey(key)) {
        println("✅ Key validation: PASSED")
    } else {
        println("❌ Key validation: FAILED")
    }
}
