plugins {
    // Lets Gradle download the JDK requested by the toolchain when it is not installed locally.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "kasify"
