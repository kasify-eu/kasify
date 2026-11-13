package eu.kasify
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.application.Application
import io.ktor.server.application.log
import org.jetbrains.exposed.v1.jdbc.Database

/**
 * Singleton to hold the HikariDataSource for health checks and monitoring
 */
object DatabaseManager {
    lateinit var dataSource: HikariDataSource
        private set

    val isInitialized: Boolean
        get() = ::dataSource.isInitialized

    fun initialize(dataSource: HikariDataSource) {
        this.dataSource = dataSource
    }
}

fun Application.configureDatabases() {
    val config =
        HikariConfig().apply {
            // Database connection settings - use environment variables with fallback to defaults
            jdbcUrl = System.getenv("DATABASE_URL")
            username = System.getenv("DATABASE_USER")
            password = System.getenv("DATABASE_PASSWORD")
            driverClassName = "org.postgresql.Driver"

            // Connection pool settings
            maximumPoolSize = System.getenv("DB_POOL_SIZE")?.toIntOrNull() ?: 10
            minimumIdle = System.getenv("DB_MIN_IDLE")?.toIntOrNull() ?: 2

            // Connection timeout settings (in milliseconds)
            connectionTimeout = 30000 // 30 seconds
            idleTimeout = 600000 // 10 minutes
            maxLifetime = 1800000 // 30 minutes

            // Connection validation
            connectionTestQuery = "SELECT 1"
            validationTimeout = 5000 // 5 seconds

            // Performance optimizations
            isAutoCommit = true
            transactionIsolation = "TRANSACTION_READ_COMMITTED"

            // Leak detection (helps catch connection leaks in development)
            leakDetectionThreshold = if (System.getenv("ENVIRONMENT") == "production") 0 else 60000 // 1 minute in dev

            // Pool name for monitoring
            poolName = "KasifyHikariPool"
        }

    val dataSource = HikariDataSource(config)
    DatabaseManager.initialize(dataSource)
    Database.connect(dataSource)

    log.info("Database connection pool configured: ${config.poolName} (max: ${config.maximumPoolSize}, min idle: ${config.minimumIdle})")
}
