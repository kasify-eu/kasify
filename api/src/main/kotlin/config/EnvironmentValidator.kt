package eu.kasify.config

import eu.kasify.security.EncryptionService
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Configuration validation errors
 */
class EnvironmentValidationException(
    message: String,
) : Exception(message)

/**
 * Validates required environment variables and configuration at application startup.
 *
 * This ensures the application fails fast with clear error messages if critical
 * configuration is missing, rather than failing later with cryptic errors.
 */
object EnvironmentValidator {
    /**
     * Required environment variables that must be present
     */
    private val requiredVariables =
        mapOf(
            "ENCRYPTION_KEY" to "Encryption key for sensitive data. Generate with: ./gradlew run --args=\"generate-key\"",
            "DATABASE_URL" to "Database connection URL (e.g., jdbc:postgresql://localhost:5432/kasify)",
            "DATABASE_USER" to "Database username",
            "DATABASE_PASSWORD" to "Database password",
            "KINDE_DOMAIN" to "kinde.com application domain",
        )

    /**
     * Optional environment variables with defaults
     */
    private val optionalVariables =
        mapOf(
            "ENVIRONMENT" to "development",
            "LOGBACK_LEVEL" to "INFO",
            "DB_POOL_SIZE" to "10",
            "DB_MIN_IDLE" to "2",
        )

    /**
     * Validates all environment configuration.
     *
     * @throws EnvironmentValidationException if validation fails
     */
    fun validate() {
        logger.info { "Validating environment configuration..." }

        val errors = mutableListOf<String>()

        // Check required variables
        requiredVariables.forEach { (key, description) ->
            val value = System.getenv(key)
            if (value.isNullOrBlank()) {
                errors.add("Missing required environment variable: $key\n  Description: $description")
            } else {
                // Validate specific formats
                when (key) {
                    "ENCRYPTION_KEY" -> validateEncryptionKey(value, errors)
                    "DATABASE_URL" -> validateDatabaseUrl(value, errors)
                }
            }
        }

        // Log optional variables (informational)
        logger.debug { "Optional environment variables:" }
        optionalVariables.forEach { (key, default) ->
            val value = System.getenv(key) ?: default
            logger.debug { "  $key = $value" }
        }

        // If any errors, fail fast
        if (errors.isNotEmpty()) {
            val errorMessage =
                buildString {
                    appendLine()
                    appendLine("=".repeat(70))
                    appendLine("ENVIRONMENT CONFIGURATION ERROR")
                    appendLine("=".repeat(70))
                    appendLine()
                    appendLine("The following required environment variables are missing or invalid:")
                    appendLine()
                    errors.forEach { error ->
                        appendLine("❌ $error")
                        appendLine()
                    }
                    appendLine("=".repeat(70))
                    appendLine("HOW TO FIX:")
                    appendLine("=".repeat(70))
                    appendLine()
                    appendLine("1. Create a .env file in the project root (if not exists)")
                    appendLine("2. Copy .env.example to .env")
                    appendLine("3. Fill in all required values:")
                    appendLine()
                    requiredVariables.forEach { (key, desc) ->
                        appendLine("   $key=<your-value>")
                        appendLine("   # $desc")
                        appendLine()
                    }
                    appendLine("4. For ENCRYPTION_KEY, generate with:")
                    appendLine("   ./gradlew run --args=\"generate-key\"")
                    appendLine()
                    appendLine("5. Restart the application")
                    appendLine()
                    appendLine("=".repeat(70))
                }

            logger.error { errorMessage }
            throw EnvironmentValidationException(errorMessage)
        }

        logger.info { "✅ Environment configuration validated successfully" }
    }

    /**
     * Validates the encryption key format and strength
     */
    private fun validateEncryptionKey(
        key: String,
        errors: MutableList<String>,
    ) {
        try {
            if (!EncryptionService.isValidKey(key)) {
                errors.add(
                    "ENCRYPTION_KEY is invalid. Must be a base64-encoded 256-bit (32 byte) key.\n" +
                        "  Generate a new key with: ./gradlew run --args=\"generate-key\"",
                )
            }
        } catch (e: Exception) {
            errors.add(
                "ENCRYPTION_KEY format is invalid: ${e.message}\n" +
                    "  Generate a new key with: ./gradlew run --args=\"generate-key\"",
            )
        }
    }

    /**
     * Validates database URL format
     */
    private fun validateDatabaseUrl(
        url: String,
        errors: MutableList<String>,
    ) {
        if (!url.startsWith("jdbc:")) {
            errors.add(
                "DATABASE_URL must start with 'jdbc:' (got: $url)\n" +
                    "  Example: jdbc:postgresql://localhost:5432/kasify",
            )
        }

        if (url.contains("your_") || url.contains("example") || url.contains("replace")) {
            errors.add(
                "DATABASE_URL appears to be a placeholder value\n" +
                    "  Please set a real database connection string",
            )
        }
    }

    /**
     * Prints current environment configuration (sanitized for logging)
     */
    fun printConfiguration() {
        logger.info { "Application Environment Configuration:" }
        logger.info { "  ENVIRONMENT: ${System.getenv("ENVIRONMENT") ?: "development"}" }
        logger.info { "  DATABASE_URL: ${sanitizeDatabaseUrl(System.getenv("DATABASE_URL"))}" }
        logger.info { "  DATABASE_USER: ${System.getenv("DATABASE_USER")}" }
        logger.info { "  DATABASE_PASSWORD: ${"*".repeat(8)}" }
        logger.info { "  ENCRYPTION_KEY: ${"*".repeat(16)} (${System.getenv("ENCRYPTION_KEY")?.length ?: 0} chars)" }
        logger.info { "  KINDE_DOMAIN: ${System.getenv("KINDE_DOMAIN")}" }
        logger.info { "  DB_POOL_SIZE: ${System.getenv("DB_POOL_SIZE") ?: "10"}" }
        logger.info { "  DB_MIN_IDLE: ${System.getenv("DB_MIN_IDLE") ?: "2"}" }
        logger.info { "  LOGBACK_LEVEL: ${System.getenv("LOGBACK_LEVEL") ?: "INFO"}" }
    }

    /**
     * Sanitizes database URL for logging (removes credentials)
     */
    private fun sanitizeDatabaseUrl(url: String?): String {
        if (url == null) return "<not set>"

        // Remove embedded credentials from URL if present
        return url.replace(Regex("://([^:]+):([^@]+)@"), "://*****:*****@")
    }
}
