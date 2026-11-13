package eu.kasify.routes

import eu.kasify.RateLimitProviders
import eu.kasify.authenticatedAndRateLimited
import eu.kasify.config.TelegramConfig
import eu.kasify.db.UserTelegramLinksRepository
import eu.kasify.maybeRateLimit
import eu.kasify.model.TelegramLinkStatus
import eu.kasify.model.api.responses.TelegramLinkResponse
import eu.kasify.model.api.responses.TelegramStatusResponse
import eu.kasify.plugins.telegram.TelegramClient
import eu.kasify.plugins.telegram.model.TelegramUpdate
import eu.kasify.routes.exceptions.ConflictException
import eu.kasify.routes.exceptions.NotFoundException
import eu.kasify.security.getKasifyUser
import eu.kasify.versionedRouting
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.HttpStatusCode
import io.ktor.openapi.jsonSchema
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.openapi.describe
import io.ktor.server.routing.openapi.hide
import io.ktor.server.routing.post
import io.ktor.utils.io.ExperimentalKtorApi
import org.koin.ktor.ext.inject
import java.security.MessageDigest
import java.time.OffsetDateTime
import kotlin.uuid.Uuid

private val logger = KotlinLogging.logger {}

private const val LINK_TOKEN_TTL_MINUTES = 15L

@OptIn(ExperimentalKtorApi::class)
fun Application.configureTelegramRouting() {
    val telegramConfig by inject<TelegramConfig>()
    val telegramLinksRepository by inject<UserTelegramLinksRepository>()
    val telegramClient by inject<TelegramClient>()

    versionedRouting {
        authenticatedAndRateLimited {
            // Start linking: issue a one-time token and return the bot deep link.
            post("/account/telegram/link") {
                val user = call.getKasifyUser()
                if (!telegramConfig.enabled) {
                    throw NotFoundException(Errors.ERROR_TELEGRAM_DISABLED, code = Errors.CODE_TELEGRAM_DISABLED)
                }

                val existing = telegramLinksRepository.getByUserId(user.userId)
                if (existing?.status == TelegramLinkStatus.Linked) {
                    throw ConflictException(
                        Errors.ERROR_TELEGRAM_ALREADY_LINKED,
                        code = Errors.CODE_TELEGRAM_ALREADY_LINKED,
                    )
                }

                val token = Uuid.random().toHexString()
                val expiresAt = OffsetDateTime.now().plusMinutes(LINK_TOKEN_TTL_MINUTES)
                telegramLinksRepository.startLink(user.userId, token, expiresAt)

                call.respond(
                    TelegramLinkResponse(
                        deepLink = "https://t.me/${telegramConfig.botUsername}?start=$token",
                        expiresAt = expiresAt.toString(),
                    ),
                )
            }.describe {
                tag("Telegram")
                summary = "Start Telegram linking"
                description =
                    "Issues a one-time token and returns a `t.me/<bot>?start=<token>` deep link. The user " +
                    "opens it in Telegram and presses Start; the bot webhook then completes the link."
                responses {
                    HttpStatusCode.OK {
                        description = "Deep link and token expiry"
                        schema = jsonSchema<TelegramLinkResponse>()
                    }
                    HttpStatusCode.Conflict { description = "Already linked. Returns `telegram.already_linked`" }
                    HttpStatusCode.NotFound { description = "Telegram is disabled. Returns `telegram.disabled`" }
                }
            }

            // Current link status for the authenticated user.
            get("/account/telegram") {
                val user = call.getKasifyUser()
                val link = telegramLinksRepository.getByUserId(user.userId)
                val isLinked = link?.status == TelegramLinkStatus.Linked
                val status =
                    when (link?.status) {
                        TelegramLinkStatus.Linked -> "linked"
                        TelegramLinkStatus.Pending -> "pending"
                        else -> "none"
                    }
                call.respond(
                    TelegramStatusResponse(
                        status = status,
                        telegramUsername = link?.telegramUsername?.takeIf { isLinked },
                        linkedAt = link?.linkedAt?.toString()?.takeIf { isLinked },
                    ),
                )
            }.describe {
                tag("Telegram")
                summary = "Get Telegram link status"
                responses {
                    HttpStatusCode.OK {
                        description = "Link status (none | pending | linked)"
                        schema = jsonSchema<TelegramStatusResponse>()
                    }
                }
            }

            // Unlink: revoke and forget the chat id.
            delete("/account/telegram") {
                val user = call.getKasifyUser()
                val removed = telegramLinksRepository.unlink(user.userId)
                if (!removed) {
                    throw NotFoundException(Errors.ERROR_TELEGRAM_NOT_LINKED, code = Errors.CODE_TELEGRAM_NOT_LINKED)
                }
                call.respond(HttpStatusCode.NoContent)
            }.describe {
                tag("Telegram")
                summary = "Unlink Telegram"
                responses {
                    HttpStatusCode.NoContent { description = "Telegram unlinked" }
                    HttpStatusCode.NotFound { description = "Nothing linked. Returns `telegram.not_linked`" }
                }
            }
        }

        // Public Telegram webhook. Not behind auth — instead authenticated by the secret token
        // Telegram echoes in the X-Telegram-Bot-Api-Secret-Token header (set via setWebhook). Always
        // returns 2xx on handled updates so Telegram does not retry; only a bad secret is rejected.
        maybeRateLimit(RateLimitProviders.IpTelegram) {
            // Telegram-to-server callback, not a client API — kept out of the public OpenAPI spec.
            post("/telegram/webhook") {
                handleWebhook(call, telegramConfig, telegramLinksRepository, telegramClient)
            }.hide()
        }
    }
}

private suspend fun handleWebhook(
    call: ApplicationCall,
    config: TelegramConfig,
    links: UserTelegramLinksRepository,
    client: TelegramClient,
) {
    if (!config.enabled) {
        call.respondText("ok")
        return
    }

    val provided = call.request.headers["X-Telegram-Bot-Api-Secret-Token"]
    if (!secretMatches(provided, config.webhookSecret)) {
        logger.warn { "Rejected Telegram webhook with bad/missing secret token" }
        call.respond(HttpStatusCode.Unauthorized)
        return
    }

    val update =
        try {
            call.receive<TelegramUpdate>()
        } catch (e: Exception) {
            logger.warn(e) { "Malformed Telegram webhook payload" }
            call.respondText("ok")
            return
        }

    val message = update.message
    val text = message?.text?.trim()
    val chat = message?.chat
    if (text != null && chat != null && text.startsWith("/start")) {
        val token = text.removePrefix("/start").trim()
        if (token.isNotBlank()) {
            completeLink(links, client, token, chat.id, chat.username)
        }
    }

    call.respondText("ok")
}

private suspend fun completeLink(
    links: UserTelegramLinksRepository,
    client: TelegramClient,
    token: String,
    chatId: Long,
    username: String?,
) {
    val link = links.findByToken(token)
    val now = OffsetDateTime.now()
    val valid =
        link != null &&
            link.status == TelegramLinkStatus.Pending &&
            (link.linkTokenExpiresAt == null || link.linkTokenExpiresAt.isAfter(now))

    if (link == null || !valid) {
        logger.info { "Telegram /start with unknown or expired token; ignoring" }
        return
    }

    links.markLinked(link.userId, chatId, username)
    logger.info { "Linked Telegram chat=$chatId to user=${link.userId}" }
    try {
        client.sendMessage(chatId, "✅ Your Telegram is now linked to Kasify. You'll receive notifications here.")
    } catch (e: Exception) {
        // Confirmation is best-effort; the link is already recorded.
        logger.warn(e) { "Failed to send Telegram link confirmation to chat=$chatId" }
    }
}

/** Constant-time comparison of the webhook secret to avoid timing oracles. */
private fun secretMatches(
    provided: String?,
    expected: String,
): Boolean {
    if (provided == null || expected.isEmpty()) return false
    return MessageDigest.isEqual(provided.toByteArray(Charsets.UTF_8), expected.toByteArray(Charsets.UTF_8))
}
