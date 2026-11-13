package eu.kasify.model.api.responses

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Platform-wide snapshot powering the admin home dashboard (`GET /admin/overview`).
 * All figures are counts across every account — this is a staff/admin view, not
 * scoped by `property_access`.
 */
@Serializable
data class AdminOverviewResponse(
    @SerialName("generated_at")
    val generatedAt: String,
    val properties: CountWithMonthlyDelta,
    val users: CountWithMonthlyDelta,
    val bookings: CountWithMonthlyDelta,
    val guests: CountWithMonthlyDelta,
    val ses: SesOverview,
    val integrations: IntegrationsOverview,
)

/** A running total plus how many of those rows were created in the current calendar month. */
@Serializable
data class CountWithMonthlyDelta(
    val total: Long,
    @SerialName("new_this_month")
    val newThisMonth: Long,
)

@Serializable
data class SesOverview(
    /** Submissions SES accepted without errors (`1_tramitado_sin_errores`). */
    @SerialName("submissions_sent")
    val submissionsSent: Long,
    /** Submissions in a terminal error state that need review. */
    @SerialName("submissions_with_errors")
    val submissionsWithErrors: Long,
)

@Serializable
data class IntegrationsOverview(
    @SerialName("nuki_integrations")
    val nukiIntegrations: Long,
    @SerialName("unifi_integrations")
    val unifiIntegrations: Long,
    @SerialName("booking_codes_created")
    val bookingCodesCreated: Long,
)
