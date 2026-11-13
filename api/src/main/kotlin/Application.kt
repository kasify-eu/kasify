package eu.kasify

import eu.kasify.config.EnvironmentValidator
import eu.kasify.routes.configureAccountRouting
import eu.kasify.routes.configureAdminListsRouting
import eu.kasify.routes.configureAdminOverviewRouting
import eu.kasify.routes.configureAdminSesMonitoringRouting
import eu.kasify.routes.configureApiTokensRouting
import eu.kasify.routes.configureAuthRouting
import eu.kasify.routes.configureBookingsRouting
import eu.kasify.routes.configureCalendarRouting
import eu.kasify.routes.configureCheckinRouting
import eu.kasify.routes.configureDigitalKeysRouting
import eu.kasify.routes.configureGeoRouting
import eu.kasify.routes.configureGuestsRouting
import eu.kasify.routes.configureHealthRouting
import eu.kasify.routes.configureMarketingRouting
import eu.kasify.routes.configurePropertiesRouting
import eu.kasify.routes.configurePropertyMembersRouting
import eu.kasify.routes.configureSmartLocksRouting
import eu.kasify.routes.configureTasksRouting
import eu.kasify.routes.configureTelegramRouting
import eu.kasify.routes.configureTimelineRouting
import eu.kasify.routes.integrations.configureIntegrationRoutes
import eu.kasify.routes.integrations.configureNukiSmartlockRoutes
import eu.kasify.routes.integrations.configureUnifiSmartlockRoutes
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain
        .main(args)
}

fun Application.module() {
    val logger = KotlinLogging.logger {}

    // Validate environment configuration before starting anything
    EnvironmentValidator.validate()
    EnvironmentValidator.printConfiguration()

    // Configure Sentry (only in production; requires SENTRY_DSN env var)
    if (System.getenv("ENVIRONMENT") == "production") {
        configureSentry()
    }

    // Background jobs
    configureScheduler()

    // Graceful shutdown
    monitor.subscribe(ApplicationStopped) {
        logger.info { "Application stopping — shutting down resources" }
        if (SchedulerManager.isInitialized) {
            try {
                SchedulerManager.scheduler.shutdown(true)
                logger.info { "Quartz scheduler shut down" }
            } catch (e: Exception) {
                logger.error(e) { "Error shutting down Quartz scheduler" }
            }
        }
        if (DatabaseManager.isInitialized) {
            try {
                DatabaseManager.dataSource.close()
                logger.info { "Database connection pool closed" }
            } catch (e: Exception) {
                logger.error(e) { "Error closing database connection pool" }
            }
        }
    }

    // API
    configureMetrics()
    configureCORS()
    configureHTTP()
    configureRequestLogging()
    configureRateLimiting()
    configureSerialization()
    configureDatabases()
    configureFrameworks()
    configureSecurity()
    configureRouting()
    configurePropertiesRouting()
    configureCalendarRouting()
    configureBookingsRouting()
    configureCheckinRouting()
    configureIntegrationRoutes()
    configureNukiSmartlockRoutes()
    configureUnifiSmartlockRoutes()
    configureSmartLocksRouting()
    configureDigitalKeysRouting()
    configureHealthRouting()
    configureAccountRouting()
    configureApiTokensRouting()
    configureAuthRouting()
    configureTimelineRouting()
    configurePropertyMembersRouting()
    configureTelegramRouting()
    configureTasksRouting()
    configureGuestsRouting()
    configureGeoRouting()
    configureMarketingRouting()
    configureAdminSesMonitoringRouting()
    configureAdminOverviewRouting()
    configureAdminListsRouting()
    configureValidation()
    configureStatusPages()
    configureOpenApi()
}
