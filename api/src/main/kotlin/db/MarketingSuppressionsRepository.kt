package eu.kasify.db
import eu.kasify.db.model.MarketingSuppressionsTable
import org.jetbrains.exposed.v1.core.CustomFunction
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.upsert
import java.time.OffsetDateTime
import kotlin.uuid.Uuid

class MarketingSuppressionsRepository {
    // SQL NOW(), the same clock that stamps guests.marketing_consent_at, so the
    // two timestamps compare consistently (see GuestHydrationService).
    private fun now() = CustomFunction<OffsetDateTime>("NOW", MarketingSuppressionsTable.suppressedAt.columnType)

    /**
     * Add [email] to the suppression list, or refresh its `suppressed_at` when
     * it is already there. Every unsubscribe must move the timestamp forward:
     * it withdraws every consent recorded up to now, including one given after
     * an earlier unsubscribe. Idempotent for the caller, so unsubscribe links
     * can be clicked any number of times. [guestId] (the first unsubscribing
     * guest) is audit-only and kept on refresh.
     */
    suspend fun suppress(
        email: String,
        guestId: Uuid?,
    ) {
        dbQuery {
            MarketingSuppressionsTable.upsert(
                MarketingSuppressionsTable.email,
                onUpdate = { it[MarketingSuppressionsTable.suppressedAt] = now() },
            ) {
                it[MarketingSuppressionsTable.email] = email.lowercase()
                it[MarketingSuppressionsTable.guestId] = guestId
                it[MarketingSuppressionsTable.suppressedAt] = now()
            }
        }
    }

    /**
     * Of the given [emails], return each (lowercased) one present in the
     * suppression list mapped to its latest unsubscribe time. One query per
     * call — used to resolve the effective marketing-consent flag for a page
     * of guests, each compared against its own consent time.
     */
    suspend fun suppressedAt(emails: Collection<String>): Map<String, OffsetDateTime> {
        val normalized = emails.mapTo(mutableSetOf()) { it.lowercase() }
        if (normalized.isEmpty()) return emptyMap()
        return dbQuery {
            MarketingSuppressionsTable
                .select(MarketingSuppressionsTable.email, MarketingSuppressionsTable.suppressedAt)
                .where { MarketingSuppressionsTable.email inList normalized }
                .associate { it[MarketingSuppressionsTable.email] to it[MarketingSuppressionsTable.suppressedAt] }
        }
    }
}
