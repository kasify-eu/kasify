package eu.kasify.routes

import eu.kasify.authenticatedAndRateLimited
import eu.kasify.db.AdminOverviewRepository
import eu.kasify.model.api.responses.AdminOverviewResponse
import eu.kasify.model.api.responses.CountWithMonthlyDelta
import eu.kasify.model.api.responses.IntegrationsOverview
import eu.kasify.model.api.responses.SesOverview
import eu.kasify.security.requireRole
import eu.kasify.versionedRouting
import io.ktor.http.HttpStatusCode
import io.ktor.openapi.jsonSchema
import io.ktor.server.application.Application
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.openapi.describe
import io.ktor.server.routing.openapi.hide
import io.ktor.utils.io.ExperimentalKtorApi
import org.koin.ktor.ext.inject
import java.time.OffsetDateTime
import java.time.ZoneId

/**
 * Platform-admin overview endpoint that backs the admin home dashboard. Counts span every
 * account (no `property_access` scoping) and are gated behind the `admin` role, mirroring the
 * admin SES monitoring routes.
 */
@OptIn(ExperimentalKtorApi::class)
fun Application.configureAdminOverviewRouting() {
    val overviewRepository by inject<AdminOverviewRepository>()
    val madridZone = ZoneId.of("Europe/Madrid")

    versionedRouting {
        authenticatedAndRateLimited {
            requireRole("admin") {
                get("/admin/overview") {
                    val counts = overviewRepository.getOverviewCounts()

                    val response =
                        AdminOverviewResponse(
                            generatedAt = OffsetDateTime.now(madridZone).toString(),
                            properties =
                                CountWithMonthlyDelta(counts.propertiesTotal, counts.propertiesNewThisMonth),
                            users = CountWithMonthlyDelta(counts.usersTotal, counts.usersNewThisMonth),
                            bookings = CountWithMonthlyDelta(counts.bookingsTotal, counts.bookingsNewThisMonth),
                            guests = CountWithMonthlyDelta(counts.guestsTotal, counts.guestsNewThisMonth),
                            ses =
                                SesOverview(
                                    submissionsSent = counts.sesSubmissionsSent,
                                    submissionsWithErrors = counts.sesSubmissionsWithErrors,
                                ),
                            integrations =
                                IntegrationsOverview(
                                    nukiIntegrations = counts.nukiIntegrations,
                                    unifiIntegrations = counts.unifiIntegrations,
                                    bookingCodesCreated = counts.bookingCodesCreated,
                                ),
                        )

                    call.respond(response)
                }.describe {
                    tag("Admin")
                    summary = "Platform overview metrics for the admin dashboard (admin)"
                    description =
                        "Returns platform-wide counts (properties, users, bookings, guests), SES " +
                        "Hospedajes submission health, and integration/booking-code totals. Admin only."
                    responses {
                        HttpStatusCode.OK {
                            description = "Platform overview snapshot"
                            schema = jsonSchema<AdminOverviewResponse>()
                        }
                        HttpStatusCode.Unauthorized { description = "Not authenticated" }
                        HttpStatusCode.Forbidden { description = "Caller is not an admin" }
                    }
                }
                // Internal platform-admin API — kept out of the public OpenAPI spec.
            }.hide()
        }
    }
}
