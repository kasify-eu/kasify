val exposedVersion: String by project
val koinVersion: String by project
val kotlinVersion: String by project
val logbackVersion: String by project
val mockkVersion: String by project
val ktlintVersion: String by project
val detektVersion: String by project
val sentryVersion: String by project
val kindeVersion: String by project
val javaJwtVersion: String by project
val jwksRsaVersion: String by project
val exposedPaginationVersion: String by project
val postgresqlVersion: String by project
val hikariCPVersion: String by project
val kotlinLoggingVersion: String by project
val quartzVersion: String by project
val awsSdkVersion: String by project
val resendVersion: String by project
val logstashEncoderVersion: String by project
val kotlinxCoroutinesVersion: String by project
val micrometerVersion: String by project

plugins {
    kotlin("jvm") version "2.3.0"
    id("io.ktor.plugin") version "3.4.2"
    id("org.jlleitschuh.gradle.ktlint") version "13.1.0"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.3.0"
    id("io.gitlab.arturbosch.detekt") version "1.23.8"
    id("io.sentry.jvm.gradle") version "5.12.2"
}

group = "eu.kasify"
version = "0.10.0" // x-release-please-version

application {
    mainClass = "io.ktor.server.netty.EngineMain"
}

ktor {
    openApi {
        enabled = true
        codeInferenceEnabled = true
        onlyCommented = true
    }
}

repositories {
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
}

// Bakes the project version (kept up to date by release-please) into the app, see AppVersion.kt.
tasks.processResources {
    inputs.property("version", project.version.toString())
    filesMatching("version.properties") {
        expand("version" to project.version.toString())
    }
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        freeCompilerArgs.add("-opt-in=kotlin.uuid.ExperimentalUuidApi")
    }
}

sentry {
    // Generates a JVM (Java, Kotlin, etc.) source bundle and uploads your source code to Sentry.
    // This enables source context, allowing you to see your source
    // code as part of your stack traces in Sentry.
    includeSourceContext = true

    org = "kasify-oa"
    projectName = "kotlin-api"
    authToken = System.getenv("SENTRY_AUTH_TOKEN")
}

dependencies {
    implementation("io.ktor:ktor-server-core")
    implementation("io.ktor:ktor-server-routing-openapi")
    implementation("ch.qos.logback:logback-classic:$logbackVersion")
    implementation("net.logstash.logback:logstash-logback-encoder:$logstashEncoderVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-slf4j:$kotlinxCoroutinesVersion")
    implementation("io.ktor:ktor-server-auth")
    implementation("io.ktor:ktor-server-cors")
    implementation("io.ktor:ktor-server-default-headers")
    implementation("io.ktor:ktor-server-forwarded-header")
    implementation("io.ktor:ktor-server-rate-limit")
    implementation("io.ktor:ktor-server-compression")
    implementation("io.ktor:ktor-server-metrics-micrometer")
    implementation("io.micrometer:micrometer-registry-prometheus:$micrometerVersion")
    implementation("io.ktor:ktor-server-call-id")
    implementation("io.ktor:ktor-server-call-logging")
    implementation("io.ktor:ktor-client-content-negotiation")
    implementation("io.ktor:ktor-server-content-negotiation")
    // implementation("io.ktor:ktor-serialization-kotlinx-json")
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm")
    implementation("com.kinde:kinde-core:$kindeVersion")
    implementation("io.ktor:ktor-server-request-validation")
    implementation("io.ktor:ktor-server-status-pages")
    // https://github.com/auth0/java-jwt
    implementation("com.auth0:java-jwt:$javaJwtVersion")
    implementation("com.auth0:jwks-rsa:$jwksRsaVersion")
    implementation("org.jetbrains.exposed:exposed-core:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-kotlin-datetime:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-java-time:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-json:$exposedVersion")
    implementation("io.github.perracodex:exposed-pagination:$exposedPaginationVersion")
    implementation("org.postgresql:postgresql:$postgresqlVersion")
    implementation("com.zaxxer:HikariCP:$hikariCPVersion")
    implementation("io.insert-koin:koin-ktor:$koinVersion")
    implementation("io.insert-koin:koin-logger-slf4j:$koinVersion")
    implementation("io.ktor:ktor-server-netty")
    implementation("io.github.oshai:kotlin-logging-jvm:$kotlinLoggingVersion")
    implementation("io.ktor:ktor-server-config-yaml")
    implementation("io.ktor:ktor-client-core")
    implementation("io.ktor:ktor-client-cio")
    implementation("org.quartz-scheduler:quartz:$quartzVersion")
    implementation("aws.sdk.kotlin:s3:$awsSdkVersion")
    implementation("org.jetbrains.kotlin:kotlin-reflect:$kotlinVersion")
    implementation("com.resend:resend-java:$resendVersion")
    testImplementation("io.mockk:mockk:$mockkVersion")
    testImplementation("io.ktor:ktor-server-test-host")
    testImplementation("io.ktor:ktor-client-mock")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:$kotlinVersion")
}

// CLI tasks
tasks.register<JavaExec>("generateEncryptionKey") {
    group = "cli"
    description = "Generate AES-256 encryption key"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("eu.kasify.cli.GenerateEncryptionKeyKt")
}

tasks.register<JavaExec>("generateKindeToken") {
    group = "cli"
    description = "Generate Kinde Management API token for updating user properties"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("eu.kasify.cli.GenerateKindeTokenKt")
}

tasks.register<JavaExec>("encryptString") {
    group = "cli"
    description = "Encrypt a string using the application's encryption service"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("eu.kasify.cli.EncryptStringKt")
    args = System.getProperty("exec.args", "")?.split(" ") ?: emptyList()
}

tasks.register<JavaExec>("runSesMock") {
    group = "cli"
    description = "Run the local SES Hospedajes mock server (default port 8888; see README.md)"
    // Lives in the test source set on purpose: the mock must never ship in the production fat JAR.
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("eu.kasify.sesmock.SesMockServerKt")
}

tasks.register("fetchOpenApiSpec") {
    group = "documentation"
    description = "Download the OpenAPI spec from the running local server into docs/apis/openapi.json"
    doLast {
        val url = providers.gradleProperty("openapi.url").getOrElse("http://localhost:8080/openapi.json")
        val output = rootProject.file("../docs/apis/openapi.json")
        output.parentFile.mkdirs()
        val exit =
            exec {
                commandLine("curl", "--fail", "--silent", "--show-error", "--output", output.absolutePath, url)
                isIgnoreExitValue = true
            }.exitValue
        if (exit != 0) {
            throw GradleException(
                "Failed to fetch OpenAPI spec from $url (curl exit $exit). Is the server running on 8080?",
            )
        }
        // Workaround for Ktor OpenAPI generator: schemas are stored under short names
        // (e.g. "Task") but some $refs use the fully-qualified name
        // ("eu.kasify.model.api.Task"), which breaks strict validators like Zudoku.
        val fqnRefPattern = Regex("\"#/components/schemas/eu\\.kasify\\.model\\.api\\.([A-Za-z0-9_]+)\"")
        val before = output.readText()
        val after = fqnRefPattern.replace(before) { "\"#/components/schemas/${it.groupValues[1]}\"" }
        if (before != after) {
            output.writeText(after)
            logger.lifecycle("Normalized FQN \$refs in OpenAPI spec")
        }
        logger.lifecycle("Wrote OpenAPI spec to ${output.relativeTo(rootDir)}")
    }
}
