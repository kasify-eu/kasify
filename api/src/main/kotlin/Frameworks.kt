package eu.kasify

import eu.kasify.config.EmailConfig
import eu.kasify.config.TelegramConfig
import eu.kasify.db.AdminOverviewRepository
import eu.kasify.db.ApiTokensRepository
import eu.kasify.db.BookingAccessCodesRepository
import eu.kasify.db.BookingsRepository
import eu.kasify.db.CalendarEventsRepository
import eu.kasify.db.FilesRepository
import eu.kasify.db.GeoRepository
import eu.kasify.db.GuestsRepository
import eu.kasify.db.IntegrationsRepository
import eu.kasify.db.MarketingSuppressionsRepository
import eu.kasify.db.NotificationsRepository
import eu.kasify.db.PropertiesRepository
import eu.kasify.db.PropertyAccessRepository
import eu.kasify.db.PropertyChannelIntegrationsRepository
import eu.kasify.db.PropertyInvitationsRepository
import eu.kasify.db.SesCommunicationsRepository
import eu.kasify.db.SesHospedajesSubmissionsRepository
import eu.kasify.db.SmartlocksRepository
import eu.kasify.db.TasksRepository
import eu.kasify.db.UserTelegramLinksRepository
import eu.kasify.db.UsersRepository
import eu.kasify.plugins.PluginFactory
import eu.kasify.plugins.resend.LoggingResendClient
import eu.kasify.plugins.resend.ResendClient
import eu.kasify.plugins.resend.ResendClientImpl
import eu.kasify.plugins.telegram.LoggingTelegramClient
import eu.kasify.plugins.telegram.TelegramClient
import eu.kasify.plugins.telegram.TelegramClientImpl
import eu.kasify.routes.integrations.IntegrationRegistry
import eu.kasify.routes.integrations.handlers.NukiIntegrationHandler
import eu.kasify.routes.integrations.handlers.SesHospedajesIntegrationHandler
import eu.kasify.routes.integrations.handlers.UnifiIntegrationHandler
import eu.kasify.security.AuthenticationService
import eu.kasify.security.EncryptionService
import eu.kasify.security.JWTSecurity
import eu.kasify.services.ApiTokenService
import eu.kasify.services.AuthorizationService
import eu.kasify.services.BookingService
import eu.kasify.services.CheckinService
import eu.kasify.services.FileService
import eu.kasify.services.GuestHydrationService
import eu.kasify.services.KindeM2MTokenService
import eu.kasify.services.MarketingService
import eu.kasify.services.R2StorageService
import eu.kasify.services.SesBookingRejectedNotifier
import eu.kasify.services.SesCredentialsGuard
import eu.kasify.services.SesHospedajesService
import eu.kasify.services.SmartlocksService
import eu.kasify.services.UnifiSessionManager
import eu.kasify.services.email.EmailTemplateRenderer
import eu.kasify.services.notifications.ChannelResolver
import eu.kasify.services.notifications.NotificationService
import io.ktor.server.application.Application
import io.ktor.server.application.install
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger

fun Application.configureFrameworks() {
    install(Koin) {
        slf4jLogger()

        modules(
            modules =
                module {
                    single<JWTSecurity> { JWTSecurity() }
                    single<ApiTokensRepository> { ApiTokensRepository() }
                    single<ApiTokenService> { ApiTokenService(get(), get()) }
                    single<AuthenticationService> { AuthenticationService(get(), get(), get()) }
                    single<KindeM2MTokenService> {
                        KindeM2MTokenService(
                            kindeDomain = System.getenv("KINDE_DOMAIN"),
                            clientId = System.getenv("KINDE_M2M_CLIENT_ID"),
                            clientSecret = System.getenv("KINDE_M2M_CLIENT_SECRET"),
                        )
                    }
                    single<EncryptionService> { EncryptionService() }
                    single<R2StorageService?> {
                        val bucket = System.getenv("BUCKET_PUBLIC") ?: return@single null
                        R2StorageService(
                            bucket = bucket,
                            endpoint = System.getenv("STORAGE_ENDPOINT") ?: error("STORAGE_ENDPOINT required when BUCKET_PUBLIC is set"),
                            publicBaseUrl = System.getenv("BUCKET_PUBLIC_URL") ?: error("BUCKET_PUBLIC_URL required when BUCKET_PUBLIC is set"),
                            accessKeyId = System.getenv("STORAGE_ACCESS_KEY_ID") ?: error("STORAGE_ACCESS_KEY_ID required when BUCKET_PUBLIC is set"),
                            secretAccessKey = System.getenv("STORAGE_SECRET_ACCESS_KEY") ?: error("STORAGE_SECRET_ACCESS_KEY required when BUCKET_PUBLIC is set"),
                        )
                    }
                    single<FilesRepository> { FilesRepository() }
                    single<FileService> { FileService(get(), get(), getOrNull()) }
                    single<PropertiesRepository> { PropertiesRepository() }
                    single<CalendarEventsRepository> { CalendarEventsRepository() }
                    single<BookingsRepository> { BookingsRepository() }
                    single<BookingAccessCodesRepository> { BookingAccessCodesRepository() }
                    single<GuestsRepository> { GuestsRepository() }
                    single<PropertyAccessRepository> { PropertyAccessRepository() }
                    single<PropertyInvitationsRepository> { PropertyInvitationsRepository() }
                    single<TasksRepository> { TasksRepository() }
                    single<BookingService> { BookingService(get(), get(), get(), get(), get()) }
                    single<AuthorizationService> { AuthorizationService(get(), get()) }
                    single<UsersRepository> { UsersRepository() }
                    single<IntegrationsRepository> { IntegrationsRepository(get(), get()) }
                    single<UnifiSessionManager> { UnifiSessionManager(get(), get()) }
                    single<SmartlocksRepository> { SmartlocksRepository() }
                    single<SmartlocksService> { SmartlocksService(get(), get(), get()) }
                    single<GeoRepository> { GeoRepository() }
                    single<GuestHydrationService> { GuestHydrationService(get(), get()) }
                    single<MarketingSuppressionsRepository> { MarketingSuppressionsRepository() }
                    single<CheckinService> { CheckinService(get(), get(), get(), get(), get(), get(), get()) }
                    single<MarketingService> { MarketingService(get(), get()) }
                    single<SesHospedajesSubmissionsRepository> { SesHospedajesSubmissionsRepository() }
                    single<SesCommunicationsRepository> { SesCommunicationsRepository() }
                    single<AdminOverviewRepository> { AdminOverviewRepository() }
                    single<SesCredentialsGuard> { SesCredentialsGuard(get(), get(), get()) }
                    single<SesBookingRejectedNotifier> { SesBookingRejectedNotifier(get(), get()) }
                    single<SesHospedajesService> {
                        SesHospedajesService(
                            integrationsRepository = get(),
                            geoRepository = get(),
                            bookingsRepository = get(),
                            propertiesRepository = get(),
                            guestsRepository = get(),
                            submissionsRepository = get(),
                            communicationsRepository = get(),
                            credentialsGuard = get(),
                            bookingRejectedNotifier = get(),
                        )
                    }
                    single<PluginFactory> { PluginFactory(get(), get()) }
                    single<PropertyChannelIntegrationsRepository> { PropertyChannelIntegrationsRepository() }
                    single<IntegrationRegistry> {
                        IntegrationRegistry().apply {
                            register(NukiIntegrationHandler(get(), get()))
                            register(UnifiIntegrationHandler(get(), get()))
                            register(SesHospedajesIntegrationHandler(get()))
                        }
                    }
                    single<EmailConfig> { EmailConfig.fromEnv() }
                    single<ResendClient> {
                        val cfg = get<EmailConfig>()
                        if (cfg.enabled && cfg.resendApiKey.isNotBlank()) {
                            ResendClientImpl(cfg.resendApiKey)
                        } else {
                            LoggingResendClient()
                        }
                    }
                    single<EmailTemplateRenderer> { EmailTemplateRenderer() }
                    single<TelegramConfig> { TelegramConfig.fromEnv() }
                    single<TelegramClient> {
                        val cfg = get<TelegramConfig>()
                        if (cfg.enabled && cfg.botToken.isNotBlank()) {
                            TelegramClientImpl(cfg.botToken)
                        } else {
                            LoggingTelegramClient()
                        }
                    }
                    single<NotificationsRepository> { NotificationsRepository() }
                    single<UserTelegramLinksRepository> { UserTelegramLinksRepository() }
                    single<ChannelResolver> { ChannelResolver(get(), get()) }
                    single<NotificationService> { NotificationService(get(), get(), get()) }
                },
        )
    }
}
