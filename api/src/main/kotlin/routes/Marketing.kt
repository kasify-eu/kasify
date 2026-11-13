package eu.kasify.routes

import eu.kasify.model.api.responses.MarketingUnsubscribeResponse
import eu.kasify.routes.exceptions.NotFoundException
import eu.kasify.services.MarketingService
import eu.kasify.versionedRouting
import io.ktor.http.HttpStatusCode
import io.ktor.openapi.jsonSchema
import io.ktor.server.application.Application
import io.ktor.server.response.respond
import io.ktor.server.routing.openapi.describe
import io.ktor.server.routing.post
import io.ktor.utils.io.ExperimentalKtorApi
import org.koin.ktor.ext.inject
import kotlin.uuid.Uuid

@OptIn(ExperimentalKtorApi::class)
fun Application.configureMarketingRouting() {
    val marketingService by inject<MarketingService>()

    versionedRouting {
        // Public one-click unsubscribe target for the link embedded in every
        // marketing email. Authenticated solely by the per-guest random token
        // (2^122 space), so no session is needed. A malformed token maps to the
        // same not-found error as an unknown one to avoid an oracle.
        post("/marketing/unsubscribe/{token}") {
            val raw = call.parameters["token"] ?: ""
            val token =
                runCatching { Uuid.parse(raw) }.getOrElse {
                    throw NotFoundException(
                        Errors.ERROR_MARKETING_UNSUBSCRIBE_NOT_FOUND,
                        code = Errors.CODE_MARKETING_UNSUBSCRIBE_NOT_FOUND,
                    )
                }
            call.respond(marketingService.unsubscribe(token))
        }.describe {
            tag("Marketing")
            summary = "Unsubscribe from marketing emails"
            description =
                "Public endpoint. Withdraws the marketing consent behind the given unsubscribe token by adding the " +
                "guest's email to the suppression list. Idempotent: repeated calls succeed. Returns a masked email " +
                "for confirmation display."
            parameters {
                path("token") { description = "Per-guest unsubscribe token (UUID) embedded in marketing email links" }
            }
            responses {
                HttpStatusCode.OK {
                    description = "The address is now suppressed"
                    schema = jsonSchema<MarketingUnsubscribeResponse>()
                }
                HttpStatusCode.NotFound {
                    description = "Token is malformed or matches no marketing subscription (`marketing.unsubscribe_not_found`)"
                }
            }
        }
    }
}
