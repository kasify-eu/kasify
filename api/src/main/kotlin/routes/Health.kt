package eu.kasify.routes

import eu.kasify.DatabaseManager
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.HttpStatusCode
import io.ktor.openapi.jsonSchema
import io.ktor.server.application.Application
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.openapi.describe
import io.ktor.server.routing.routing
import io.ktor.utils.io.ExperimentalKtorApi
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.Instant

private val logger = KotlinLogging.logger {}

@Serializable
data class HealthResponse(
    val status: String,
    val timestamp: String,
    val components: Map<String, ComponentHealth>,
)

@Serializable
data class ComponentHealth(
    val status: String,
    val details: Map<String, String> = emptyMap(),
)

@OptIn(ExperimentalKtorApi::class)
fun Application.configureHealthRouting() {
    routing {
        get("/health") {
            val components = mutableMapOf<String, ComponentHealth>()
            var overallHealthy = true

            // Check database connectivity
            val dbHealth = checkDatabaseHealth()
            components["database"] = dbHealth
            if (dbHealth.status != "UP") {
                overallHealthy = false
            }

            // Check connection pool
            val poolHealth = checkConnectionPoolHealth()
            components["connectionPool"] = poolHealth
            if (poolHealth.status != "UP") {
                overallHealthy = false
            }

            // Build response
            val response =
                HealthResponse(
                    status = if (overallHealthy) "UP" else "DOWN",
                    timestamp = Instant.now().toString(),
                    components = components,
                )

            // Return appropriate status code
            val statusCode = if (overallHealthy) HttpStatusCode.OK else HttpStatusCode.ServiceUnavailable
            call.respond(statusCode, response)
        }.describe {
            tag("Health")
            summary = "Service liveness and component health"
            description = "Reports the aggregate status plus per-component health (database, HikariCP connection pool). Returns 503 when any component is down."
            responses {
                HttpStatusCode.OK {
                    description = "All components healthy"
                    schema = jsonSchema<HealthResponse>()
                }
                HttpStatusCode.ServiceUnavailable {
                    description = "One or more components are down or degraded"
                    schema = jsonSchema<HealthResponse>()
                }
            }
        }
    }
}

/**
 * Checks database connectivity by executing a simple query
 */
private fun checkDatabaseHealth(): ComponentHealth =
    try {
        val startTime = System.currentTimeMillis()

        // Execute a simple query to verify database connectivity
        transaction {
            exec("SELECT 1") { rs ->
                rs.next()
                rs.getInt(1)
            }
        }

        val duration = System.currentTimeMillis() - startTime

        ComponentHealth(
            status = "UP",
            details =
                mapOf(
                    "responseTime" to "${duration}ms",
                    "database" to "PostgreSQL",
                ),
        )
    } catch (e: Exception) {
        logger.error(e) { "Database health check failed" }
        ComponentHealth(
            status = "DOWN",
            details =
                mapOf(
                    "error" to (e.message ?: "Unknown error"),
                    "database" to "PostgreSQL",
                ),
        )
    }

/**
 * Checks HikariCP connection pool health
 */
private fun checkConnectionPoolHealth(): ComponentHealth =
    try {
        val dataSource = DatabaseManager.dataSource
        val activeConnections = dataSource.hikariPoolMXBean.activeConnections
        val idleConnections = dataSource.hikariPoolMXBean.idleConnections
        val totalConnections = dataSource.hikariPoolMXBean.totalConnections
        val threadsAwaitingConnection = dataSource.hikariPoolMXBean.threadsAwaitingConnection
        val maxPoolSize = dataSource.maximumPoolSize

        // Consider pool unhealthy if it's exhausted or has many waiting threads
        val isHealthy = threadsAwaitingConnection == 0 && totalConnections < maxPoolSize

        ComponentHealth(
            status = if (isHealthy) "UP" else "DEGRADED",
            details =
                mapOf(
                    "active" to activeConnections.toString(),
                    "idle" to idleConnections.toString(),
                    "total" to totalConnections.toString(),
                    "max" to maxPoolSize.toString(),
                    "waiting" to threadsAwaitingConnection.toString(),
                ),
        )
    } catch (e: Exception) {
        logger.error(e) { "Connection pool health check failed" }
        ComponentHealth(
            status = "DOWN",
            details = mapOf("error" to (e.message ?: "Unknown error")),
        )
    }
