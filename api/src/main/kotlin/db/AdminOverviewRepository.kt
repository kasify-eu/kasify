package eu.kasify.db

import eu.kasify.db.model.AccountIntegrationsTable
import eu.kasify.db.model.BookingAccessCodesTable
import eu.kasify.db.model.BookingsTable
import eu.kasify.db.model.GuestsTable
import eu.kasify.db.model.IntegrationVendor
import eu.kasify.db.model.PropertiesTable
import eu.kasify.db.model.SesCommunicationsTable
import eu.kasify.db.model.UsersTable
import eu.kasify.model.AdminOverviewCounts
import eu.kasify.model.SesCommunicationOperation
import eu.kasify.model.SesCommunicationStatus
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.jdbc.selectAll
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

open class AdminOverviewRepository {
    private val madridZone = ZoneId.of("Europe/Madrid")

    /** Start of the current calendar month (Europe/Madrid) used for the "new this month" deltas. */
    private fun currentMonthStart(): OffsetDateTime =
        ZonedDateTime
            .now(madridZone)
            .toLocalDate()
            .withDayOfMonth(1)
            .atStartOfDay(madridZone)
            .toOffsetDateTime()

    open suspend fun getOverviewCounts(): AdminOverviewCounts =
        dbQuery {
            val monthStart = currentMonthStart()

            val propertiesTotal =
                PropertiesTable
                    .selectAll()
                    .where { PropertiesTable.deletedAt.isNull() }
                    .count()
            val propertiesNewThisMonth =
                PropertiesTable
                    .selectAll()
                    .where { PropertiesTable.deletedAt.isNull() and (PropertiesTable.createdAt greaterEq monthStart) }
                    .count()

            val usersTotal = UsersTable.selectAll().count()
            val usersNewThisMonth =
                UsersTable
                    .selectAll()
                    .where { UsersTable.createdAt greaterEq monthStart }
                    .count()

            val bookingsTotal = BookingsTable.selectAll().count()
            val bookingsNewThisMonth =
                BookingsTable
                    .selectAll()
                    .where { BookingsTable.createdAt greaterEq monthStart }
                    .count()

            val guestsTotal = GuestsTable.selectAll().count()
            val guestsNewThisMonth =
                GuestsTable
                    .selectAll()
                    .where { GuestsTable.createdAt greaterEq monthStart }
                    .count()

            // Accepted altas (one per booking × type), not wire-log rows: the submissions log
            // records every SOAP attempt, so re-polling an already-accepted lote would inflate it.
            val sesSubmissionsSent =
                SesCommunicationsTable
                    .selectAll()
                    .where {
                        (SesCommunicationsTable.operation eq SesCommunicationOperation.Alta) and
                            (SesCommunicationsTable.status eq SesCommunicationStatus.Accepted)
                    }.count()
            // Altas currently in a terminal error state — not error events in the wire log, which
            // would keep counting attempts of communications that later succeeded.
            val sesSubmissionsWithErrors =
                SesCommunicationsTable
                    .selectAll()
                    .where {
                        (SesCommunicationsTable.operation eq SesCommunicationOperation.Alta) and
                            (SesCommunicationsTable.status inList listOf(SesCommunicationStatus.Rejected, SesCommunicationStatus.Failed))
                    }.count()

            val nukiIntegrations =
                AccountIntegrationsTable
                    .selectAll()
                    .where { AccountIntegrationsTable.vendor eq IntegrationVendor.Nuki }
                    .count()
            val unifiIntegrations =
                AccountIntegrationsTable
                    .selectAll()
                    .where { AccountIntegrationsTable.vendor eq IntegrationVendor.Unifi }
                    .count()

            val bookingCodesCreated =
                BookingAccessCodesTable
                    .selectAll()
                    .where { BookingAccessCodesTable.deletedAt.isNull() }
                    .count()

            AdminOverviewCounts(
                propertiesTotal = propertiesTotal,
                propertiesNewThisMonth = propertiesNewThisMonth,
                usersTotal = usersTotal,
                usersNewThisMonth = usersNewThisMonth,
                bookingsTotal = bookingsTotal,
                bookingsNewThisMonth = bookingsNewThisMonth,
                guestsTotal = guestsTotal,
                guestsNewThisMonth = guestsNewThisMonth,
                sesSubmissionsSent = sesSubmissionsSent,
                sesSubmissionsWithErrors = sesSubmissionsWithErrors,
                nukiIntegrations = nukiIntegrations,
                unifiIntegrations = unifiIntegrations,
                bookingCodesCreated = bookingCodesCreated,
            )
        }
}
