package eu.kasify.db.model
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.timestampWithTimeZone

/**
 * Emails that opted out of marketing (LSSI Art. 21). Keyed by lowercased
 * address. [suppressedAt] is the latest unsubscribe for the address: it
 * withdraws every guest consent recorded at or before it, in every account,
 * while a consent recorded later counts for its own guest row only. A consent
 * never deletes the row (the address on a registration is self-asserted).
 * [guestId] and [createdAt] (first unsubscribe) are audit-only.
 */
object MarketingSuppressionsTable : Table(name = "marketing_suppressions") {
    val id = uuid("id").autoGenerate()
    val email = text("email").uniqueIndex()
    val guestId = reference("guest_id", GuestsTable.id).nullable()
    val createdAt = timestampWithTimeZone("created_at")
    val suppressedAt = timestampWithTimeZone("suppressed_at")

    override val primaryKey = PrimaryKey(id)
}
