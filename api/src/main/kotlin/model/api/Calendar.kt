package eu.kasify.model.api

import eu.kasify.model.PropertyChannelIntegration
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

@Serializable
class Calendar(
    val channel: String,
    val url: String,
    val syncIntervalMinutes: Int,
    val lastSyncAt: LocalDate?,
    val lastError: String?,
) {
    companion object {
        fun from(integration: PropertyChannelIntegration): Calendar =
            Calendar(
                channel = integration.channel.name,
                url = integration.url,
                syncIntervalMinutes = integration.syncIntervalMinutes,
                lastSyncAt = integration.lastSyncAt,
                lastError = integration.lastError,
            )
    }
}
