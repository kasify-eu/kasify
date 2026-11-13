package eu.kasify.model
import eu.kasify.db.model.BookingChannel
import eu.kasify.db.model.PropertyChannelIntegrationsTable
import eu.kasify.db.toKotlinLocalDate
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.ResultRow
import kotlin.uuid.Uuid

@Serializable
data class PropertyChannelIntegration(
    val id: Uuid,
    val propertyId: Uuid,
    val channel: BookingChannel,
    val url: String,
    val syncEnabled: Boolean,
    val syncIntervalMinutes: Int,
    val lastSyncAt: LocalDate?,
    val nextSyncAt: LocalDate?,
    val lastError: String?,
    val errorCount: Int,
    val createdAt: LocalDate,
    val updatedAt: LocalDate,
) {
    companion object {
        fun from(row: ResultRow): PropertyChannelIntegration =
            PropertyChannelIntegration(
                id = row[PropertyChannelIntegrationsTable.id],
                propertyId = row[PropertyChannelIntegrationsTable.propertyId],
                channel = row[PropertyChannelIntegrationsTable.channel],
                url = row[PropertyChannelIntegrationsTable.url],
                syncEnabled = row[PropertyChannelIntegrationsTable.syncEnabled],
                syncIntervalMinutes = row[PropertyChannelIntegrationsTable.syncIntervalMinutes],
                lastSyncAt = row[PropertyChannelIntegrationsTable.lastSyncAt]?.toLocalDate()?.toKotlinLocalDate(),
                nextSyncAt = row[PropertyChannelIntegrationsTable.nextSyncAt]?.toLocalDate()?.toKotlinLocalDate(),
                lastError = row[PropertyChannelIntegrationsTable.lastError],
                errorCount = row[PropertyChannelIntegrationsTable.errorCount],
                createdAt = row[PropertyChannelIntegrationsTable.createdAt].toLocalDate().toKotlinLocalDate(),
                updatedAt = row[PropertyChannelIntegrationsTable.updatedAt].toLocalDate().toKotlinLocalDate(),
            )
    }
}
