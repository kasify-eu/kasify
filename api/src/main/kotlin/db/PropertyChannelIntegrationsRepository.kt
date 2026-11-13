package eu.kasify.db
import eu.kasify.db.model.BookingChannel
import eu.kasify.db.model.PropertiesTable
import eu.kasify.db.model.PropertyChannelIntegrationsTable
import eu.kasify.model.PropertyChannelIntegration
import org.jetbrains.exposed.v1.core.CustomFunction
import org.jetbrains.exposed.v1.core.Expression
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.QueryBuilder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.intLiteral
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.core.plus
import org.jetbrains.exposed.v1.jdbc.andWhere
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insertReturning
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import java.time.OffsetDateTime
import kotlin.uuid.Uuid

private const val SCHEDULE_GRACE_MINUTES = 1

class PropertyChannelIntegrationsRepository {
    // Custom function to get current timestamp with timezone from database
    private fun now() = CustomFunction<OffsetDateTime>("NOW", PropertyChannelIntegrationsTable.lastSyncAt.columnType)

    // Custom function to add minutes to NOW()
    private fun nowPlusMinutes(minutes: Expression<Int>) =
        object : CustomFunction<OffsetDateTime>(
            "NOW() + (? || ' minutes')::INTERVAL",
            PropertyChannelIntegrationsTable.nextSyncAt.columnType,
            minutes,
        ) {
            override fun toQueryBuilder(queryBuilder: QueryBuilder) {
                queryBuilder.append("NOW() + (")
                minutes.toQueryBuilder(queryBuilder)
                queryBuilder.append(" || ' minutes')::INTERVAL")
            }
        }

    suspend fun getIntegrationsNeedingExecution(): List<PropertyChannelIntegration> =
        dbQuery {
            PropertyChannelIntegrationsTable
                .join(PropertiesTable, JoinType.INNER, PropertyChannelIntegrationsTable.propertyId, PropertiesTable.id)
                .selectAll()
                .where {
                    // Property is not deleted
                    PropertiesTable.deletedAt.isNull()
                }.andWhere {
                    // Integration is enabled
                    PropertyChannelIntegrationsTable.syncEnabled eq true
                }.andWhere {
                    // Due when the schedule is unset (never synced, or reset by a URL change) or
                    // has arrived. Eligibility keys off nextSyncAt alone — markSyncFailed sets
                    // nextSyncAt but never lastSyncAt, so a lastSyncAt-based "never synced" branch
                    // would retry a feed whose first sync failed on every tick, ignoring the retry
                    // backoff. The grace window absorbs the few hundred ms by which nextSyncAt
                    // (= sync time + interval) trails the job tick that fires exactly one interval
                    // later — without it a 60-min interval effectively becomes 75 min (the tick
                    // misses and the next one is 15 min away).
                    PropertyChannelIntegrationsTable.nextSyncAt.isNull() or
                        (
                            PropertyChannelIntegrationsTable.nextSyncAt lessEq
                                nowPlusMinutes(intLiteral(SCHEDULE_GRACE_MINUTES))
                        )
                }.map { row ->
                    PropertyChannelIntegration.from(row)
                }
        }

    suspend fun getAllIntegrations(): List<PropertyChannelIntegration> =
        dbQuery {
            PropertyChannelIntegrationsTable
                .selectAll()
                .map { row ->
                    PropertyChannelIntegration.from(row)
                }
        }

    suspend fun getIntegrationsByPropertyId(propertyId: Uuid): List<PropertyChannelIntegration> =
        dbQuery {
            PropertyChannelIntegrationsTable
                .selectAll()
                .where {
                    PropertyChannelIntegrationsTable.propertyId eq propertyId
                }.map { row ->
                    PropertyChannelIntegration.from(row)
                }
        }

    /**
     * Batch variant of [getIntegrationsByPropertyId]: fetches every integration for the given
     * properties in a single `WHERE property_id IN (…)` query, so a page of properties can be
     * enriched with their calendars without an N+1 query. Returns empty for an empty input.
     */
    suspend fun getIntegrationsByPropertyIds(propertyIds: List<Uuid>): List<PropertyChannelIntegration> =
        dbQuery {
            if (propertyIds.isEmpty()) {
                emptyList()
            } else {
                PropertyChannelIntegrationsTable
                    .selectAll()
                    .where {
                        PropertyChannelIntegrationsTable.propertyId inList propertyIds
                    }.map { row ->
                        PropertyChannelIntegration.from(row)
                    }
            }
        }

    /**
     * Creates a new PropertyChannelIntegration
     * @param url The URL for the integration
     * @param propertyId The property UUID
     * @param bookingChannel The booking channel type
     * @param syncIntervalMinutes The sync interval in minutes (defaults to 60)
     * @return The created PropertyChannelIntegration
     */
    suspend fun createPropertyChannelIntegration(
        url: String,
        propertyId: Uuid,
        bookingChannel: BookingChannel,
        syncIntervalMinutes: Int = 60,
    ): PropertyChannelIntegration =
        dbQuery {
            PropertyChannelIntegrationsTable
                .insertReturning {
                    it[PropertyChannelIntegrationsTable.propertyId] = propertyId
                    it[channel] = bookingChannel
                    it[PropertyChannelIntegrationsTable.url] = url
                    it[PropertyChannelIntegrationsTable.syncIntervalMinutes] = syncIntervalMinutes
                    it[syncEnabled] = true
                    it[errorCount] = 0
                    it[createdAt] = now()
                    it[updatedAt] = now()
                }.map { row ->
                    PropertyChannelIntegration.from(row)
                }.single()
        }

    /**
     * Updates the integration sync timestamps after a successful sync.
     * Sets lastSyncAt to NOW() and nextSyncAt to NOW() + syncIntervalMinutes
     */
    suspend fun updateSyncTimestamps(integrationId: Uuid) {
        dbQuery {
            PropertyChannelIntegrationsTable.update({ PropertyChannelIntegrationsTable.id eq integrationId }) {
                it[lastSyncAt] = now()
                it[nextSyncAt] = nowPlusMinutes(PropertyChannelIntegrationsTable.syncIntervalMinutes)
            }
        }
    }

    /**
     * Marks an integration sync as failed.
     * Sets lastError to the error message (truncated to 1000 chars),
     * increments errorCount, and schedules next retry.
     */
    suspend fun markSyncFailed(
        integrationId: Uuid,
        error: String,
    ) {
        dbQuery {
            PropertyChannelIntegrationsTable.update({ PropertyChannelIntegrationsTable.id eq integrationId }) {
                it[lastError] = error.take(1000)
                it[errorCount] = errorCount plus 1
                it[nextSyncAt] = nowPlusMinutes(PropertyChannelIntegrationsTable.syncIntervalMinutes)
                it[updatedAt] = now()
            }
        }
    }

    /**
     * Marks an integration sync as succeeded.
     * Sets lastSyncAt to NOW(), schedules next sync,
     * clears lastError and resets errorCount to 0.
     */
    suspend fun markSyncSucceeded(integrationId: Uuid) {
        dbQuery {
            PropertyChannelIntegrationsTable.update({ PropertyChannelIntegrationsTable.id eq integrationId }) {
                it[lastSyncAt] = now()
                it[nextSyncAt] = nowPlusMinutes(PropertyChannelIntegrationsTable.syncIntervalMinutes)
                it[lastError] = null
                it[errorCount] = 0
                it[updatedAt] = now()
            }
        }
    }

    /**
     * Creates or updates a PropertyChannelIntegration.
     * If an integration already exists for the property and channel, updates the URL.
     * Otherwise, creates a new integration.
     */
    suspend fun upsertPropertyChannelIntegration(
        url: String,
        propertyId: Uuid,
        bookingChannel: BookingChannel,
        syncIntervalMinutes: Int = 60,
    ): PropertyChannelIntegration =
        dbQuery {
            val existing =
                PropertyChannelIntegrationsTable
                    .selectAll()
                    .where {
                        (PropertyChannelIntegrationsTable.propertyId eq propertyId) and
                            (PropertyChannelIntegrationsTable.channel eq bookingChannel)
                    }.singleOrNull()

            if (existing != null) {
                // A changed URL means the previous sync state (schedule, errors) belongs to the old
                // feed. Clearing nextSyncAt makes the integration due in
                // getIntegrationsNeedingExecution, so the new URL is tried on the next job tick
                // instead of waiting out the previous schedule. If that first sync fails,
                // markSyncFailed re-populates nextSyncAt and the normal retry backoff applies.
                val urlChanged = existing[PropertyChannelIntegrationsTable.url] != url
                PropertyChannelIntegrationsTable.update({
                    (PropertyChannelIntegrationsTable.propertyId eq propertyId) and
                        (PropertyChannelIntegrationsTable.channel eq bookingChannel)
                }) {
                    it[PropertyChannelIntegrationsTable.url] = url
                    it[updatedAt] = now()
                    if (urlChanged) {
                        it[lastSyncAt] = null
                        it[nextSyncAt] = null
                        it[lastError] = null
                        it[errorCount] = 0
                    }
                }
                PropertyChannelIntegrationsTable
                    .selectAll()
                    .where {
                        (PropertyChannelIntegrationsTable.propertyId eq propertyId) and
                            (PropertyChannelIntegrationsTable.channel eq bookingChannel)
                    }.map { row ->
                        PropertyChannelIntegration.from(row)
                    }.single()
            } else {
                PropertyChannelIntegrationsTable
                    .insertReturning {
                        it[PropertyChannelIntegrationsTable.propertyId] = propertyId
                        it[channel] = bookingChannel
                        it[PropertyChannelIntegrationsTable.url] = url
                        it[PropertyChannelIntegrationsTable.syncIntervalMinutes] = syncIntervalMinutes
                        it[syncEnabled] = true
                        it[errorCount] = 0
                        it[createdAt] = now()
                        it[updatedAt] = now()
                    }.map { row ->
                        PropertyChannelIntegration.from(row)
                    }.single()
            }
        }

    /**
     * Deletes a PropertyChannelIntegration for a specific property and channel.
     * @param propertyId The property UUID
     * @param bookingChannel The booking channel type
     * @return true if a record was deleted, false otherwise
     */
    suspend fun deletePropertyChannelIntegration(
        propertyId: Uuid,
        bookingChannel: BookingChannel,
    ): Boolean =
        dbQuery {
            PropertyChannelIntegrationsTable.deleteWhere {
                (PropertyChannelIntegrationsTable.propertyId eq propertyId) and
                    (channel eq bookingChannel)
            } > 0
        }
}
