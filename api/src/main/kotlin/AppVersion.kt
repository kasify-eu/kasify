package eu.kasify

import java.util.Properties

/** The application version, taken from `version` in build.gradle.kts via the generated `version.properties`. */
object AppVersion {
    val current: String =
        AppVersion::class.java
            .getResourceAsStream("/version.properties")
            ?.use { stream -> Properties().apply { load(stream) }.getProperty("version") }
            ?: "unknown"
}
