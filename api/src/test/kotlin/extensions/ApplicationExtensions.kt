package eu.kasify.extensions

import eu.kasify.AUTH_BEARER
import eu.kasify.AUTH_COOKIE
import eu.kasify.config.EmailConfig
import eu.kasify.configureRouting
import eu.kasify.configureStatusPages
import eu.kasify.configureValidation
import eu.kasify.db.ApiTokensRepository
import eu.kasify.db.BookingAccessCodesRepository
import eu.kasify.db.BookingsRepository
import eu.kasify.db.CalendarEventsRepository
import eu.kasify.db.GeoRepository
import eu.kasify.db.GuestsRepository
import eu.kasify.db.IntegrationsRepository
import eu.kasify.db.PropertiesRepository
import eu.kasify.db.PropertyAccessRepository
import eu.kasify.db.PropertyChannelIntegrationsRepository
import eu.kasify.db.PropertyInvitationsRepository
import eu.kasify.db.SmartlocksRepository
import eu.kasify.db.TasksRepository
import eu.kasify.db.UsersRepository
import eu.kasify.helpers.loadNukiTestResource
import eu.kasify.mocks.MockPluginFactory
import eu.kasify.plugins.PluginFactory
import eu.kasify.plugins.resend.LoggingResendClient
import eu.kasify.plugins.resend.ResendClient
import eu.kasify.routes.configureAccountRouting
import eu.kasify.routes.configureApiTokensRouting
import eu.kasify.routes.configureBookingsRouting
import eu.kasify.routes.configureCalendarRouting
import eu.kasify.routes.configureCheckinRouting
import eu.kasify.routes.configureDigitalKeysRouting
import eu.kasify.routes.configureGuestsRouting
import eu.kasify.routes.configurePropertiesRouting
import eu.kasify.routes.configurePropertyMembersRouting
import eu.kasify.routes.configureSmartLocksRouting
import eu.kasify.routes.configureTasksRouting
import eu.kasify.routes.configureTelegramRouting
import eu.kasify.routes.configureTimelineRouting
import eu.kasify.routes.integrations.configureIntegrationRoutes
import eu.kasify.routes.integrations.configureNukiSmartlockRoutes
import eu.kasify.routes.integrations.configureUnifiSmartlockRoutes
import eu.kasify.security.AuthMethod
import eu.kasify.security.EncryptionService
import eu.kasify.security.KasifyUserPrincipal
import eu.kasify.services.ApiTokenService
import eu.kasify.services.AuthorizationService
import eu.kasify.services.BookingService
import eu.kasify.services.CheckinService
import eu.kasify.services.GuestHydrationService
import eu.kasify.services.SmartlocksService
import eu.kasify.services.email.EmailTemplateRenderer
import eu.kasify.users
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.bearer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.utils.io.ByteReadChannel
import kotlinx.serialization.json.Json
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin

// Test encryption key - must match the one used in configureTestModules()
const val TEST_ENCRYPTION_KEY = "xyIS7FYppqteC/FTXqkLHZaObBgMQLZM/LhCYia2bvQ="

val mockEngine =
    MockEngine { request ->
        when {
            request.url.toString().endsWith("/smartlock") && request.method == HttpMethod.Get -> {
                respond(
                    content = ByteReadChannel(loadNukiTestResource("smartlocks_response.json")),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            }

            request.url.toString().contains("/smartlock/123456789") && request.method == HttpMethod.Get -> {
                respond(
                    content = ByteReadChannel(loadNukiTestResource("smartlock_single_response.json")),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            }

            request.url.toString().contains("/smartlock/123456789/auth") && request.method == HttpMethod.Put -> {
                respond(
                    content = "",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            }

            request.url.toString().contains("/smartlock/1") && request.method == HttpMethod.Get -> {
                respond(
                    content = ByteReadChannel(loadNukiTestResource("smartlock_single_response.json")),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            }

            request.url.toString().contains("/action") && request.method == HttpMethod.Post -> {
                respond(
                    content = ByteReadChannel(loadNukiTestResource("action_response.json")),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            }

            else -> {
                respond(
                    content = ByteReadChannel("Not Found"),
                    status = HttpStatusCode.NotFound,
                )
            }
        }
    }

fun Application.configureTestModules(pluginFactory: PluginFactory = MockPluginFactory()) {
    install(ContentNegotiation) {
        json(
            Json {
                ignoreUnknownKeys = true
            },
        )
    }

    install(Authentication) {
        bearer(AUTH_BEARER) {
            authenticate { token ->
                val tokenString = token.token
                if (tokenString.startsWith(ApiTokenService.TOKEN_PREFIX)) {
                    val service = ApiTokenService(ApiTokensRepository(), UsersRepository())
                    return@authenticate try {
                        service.validate(tokenString)
                    } catch (_: Exception) {
                        null
                    }
                }
                users.firstOrNull { it.token == tokenString }?.let {
                    return@authenticate KasifyUserPrincipal(it.id, it.accountId, authMethod = AuthMethod.Jwt)
                }
                return@authenticate null
            }
        }

        provider(AUTH_COOKIE) {
            authenticate { context ->
                val token = context.call.request.cookies["access_token"]
                if (token != null) {
                    val user = users.firstOrNull { it.token == token }
                    if (user != null) {
                        context.principal(KasifyUserPrincipal(user.id, user.accountId, authMethod = AuthMethod.Jwt))
                    }
                }
            }
        }
    }

    install(Koin) {
        modules(
            module {
                single<PropertiesRepository> { PropertiesRepository() }
                single<CalendarEventsRepository> { CalendarEventsRepository() }
                single<BookingsRepository> { BookingsRepository() }
                single<PropertyAccessRepository> { PropertyAccessRepository() }
                single<AuthorizationService> { AuthorizationService(get(), get()) }
                single<PropertyChannelIntegrationsRepository> { PropertyChannelIntegrationsRepository() }
                // Use a test encryption key so tests don't require ENCRYPTION_KEY env var
                single<EncryptionService> {
                    EncryptionService(key = TEST_ENCRYPTION_KEY)
                }
                single<IntegrationsRepository> { IntegrationsRepository(get(), get()) }
                single<SmartlocksRepository> { SmartlocksRepository() }
                single<TasksRepository> { TasksRepository() }
                single<BookingService> { BookingService(get(), get(), get(), get()) }
                single<GeoRepository> { GeoRepository() }
                single<GuestHydrationService> { GuestHydrationService(get(), get()) }
                single<eu.kasify.db.MarketingSuppressionsRepository> { eu.kasify.db.MarketingSuppressionsRepository() }
                single<CheckinService> { CheckinService(get(), get(), get(), get(), get(), get(), get()) }
                single<eu.kasify.services.MarketingService> { eu.kasify.services.MarketingService(get(), get()) }
                single<BookingAccessCodesRepository> { BookingAccessCodesRepository() }
                // Use MockPluginFactory to avoid calling real external APIs in tests
                single<PluginFactory> { pluginFactory }
                single<SmartlocksService> { SmartlocksService(get(), get(), get()) }
                single<UsersRepository> { UsersRepository() }
                single<ApiTokensRepository> { ApiTokensRepository() }
                single<ApiTokenService> { ApiTokenService(get(), get()) }
                single<PropertyInvitationsRepository> { PropertyInvitationsRepository() }
                single<GuestsRepository> { GuestsRepository() }
                single<eu.kasify.db.SesCommunicationsRepository> { eu.kasify.db.SesCommunicationsRepository() }
                single<eu.kasify.services.UnifiSessionManager> { eu.kasify.services.UnifiSessionManager(get(), get()) }
                single<EmailConfig> {
                    EmailConfig(
                        enabled = false,
                        fromAddress = "Kasify <test@example.com>",
                        resendApiKey = "",
                        dashboardBaseUrl = "http://localhost:5173",
                    )
                }
                single<ResendClient> { LoggingResendClient() }
                single<EmailTemplateRenderer> { EmailTemplateRenderer() }
                single<eu.kasify.config.TelegramConfig> {
                    eu.kasify.config.TelegramConfig(
                        enabled = true,
                        botToken = "test-token",
                        botUsername = "KasifyBot",
                        webhookSecret = "test-secret",
                    )
                }
                single<eu.kasify.plugins.telegram.TelegramClient> {
                    eu.kasify.plugins.telegram
                        .LoggingTelegramClient()
                }
                single<eu.kasify.db.NotificationsRepository> { eu.kasify.db.NotificationsRepository() }
                single<eu.kasify.db.UserTelegramLinksRepository> { eu.kasify.db.UserTelegramLinksRepository() }
                single<eu.kasify.services.notifications.ChannelResolver> {
                    eu.kasify.services.notifications
                        .ChannelResolver(get(), get())
                }
                single<eu.kasify.services.notifications.NotificationService> {
                    eu.kasify.services.notifications
                        .NotificationService(get(), get(), get())
                }
                single<eu.kasify.routes.integrations.IntegrationRegistry> {
                    eu.kasify.routes.integrations.IntegrationRegistry().apply {
                        register(
                            eu.kasify.routes.integrations.handlers
                                .NukiIntegrationHandler(get(), get()),
                        )
                        register(
                            eu.kasify.routes.integrations.handlers
                                .UnifiIntegrationHandler(get(), get()),
                        )
                        register(
                            eu.kasify.routes.integrations.handlers
                                .SesHospedajesIntegrationHandler(get()),
                        )
                    }
                }
            },
        )
    }

    configureStatusPages()
    configureValidation()
    configureRouting()
    configurePropertiesRouting()
    configureCalendarRouting()
    configureBookingsRouting()
    configureCheckinRouting()
    configureIntegrationRoutes()
    configureNukiSmartlockRoutes()
    configureUnifiSmartlockRoutes()
    configureSmartLocksRouting()
    configureTimelineRouting()
    configureDigitalKeysRouting()
    configurePropertyMembersRouting()
    configureTelegramRouting()
    configureTasksRouting()
    configureApiTokensRouting()
    configureGuestsRouting()
    configureAccountRouting()
}
