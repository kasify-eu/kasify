package eu.kasify.model

/**
 * Platform-wide counts for the admin overview, produced by `AdminOverviewRepository`.
 * Plain domain holder — the route maps it to `AdminOverviewResponse`.
 */
data class AdminOverviewCounts(
    val propertiesTotal: Long,
    val propertiesNewThisMonth: Long,
    val usersTotal: Long,
    val usersNewThisMonth: Long,
    val bookingsTotal: Long,
    val bookingsNewThisMonth: Long,
    val guestsTotal: Long,
    val guestsNewThisMonth: Long,
    val sesSubmissionsSent: Long,
    val sesSubmissionsWithErrors: Long,
    val nukiIntegrations: Long,
    val unifiIntegrations: Long,
    val bookingCodesCreated: Long,
)
