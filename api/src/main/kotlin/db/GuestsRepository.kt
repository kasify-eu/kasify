package eu.kasify.db
import eu.kasify.db.model.BookingsTable
import eu.kasify.db.model.CountriesTable
import eu.kasify.db.model.DocumentType
import eu.kasify.db.model.Gender
import eu.kasify.db.model.GuestsTable
import eu.kasify.db.model.PropertiesTable
import eu.kasify.db.model.PropertyAccessLevel
import eu.kasify.db.model.PropertyAccessTable
import eu.kasify.db.model.UsersTable
import eu.kasify.model.AdminGuestListItem
import eu.kasify.model.Guest
import eu.kasify.model.GuestBookingContext
import eu.kasify.model.api.requests.GuestsFilters
import io.perracodex.exposed.pagination.Page
import io.perracodex.exposed.pagination.Pageable
import io.perracodex.exposed.pagination.paginate
import org.jetbrains.exposed.v1.core.CustomFunction
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.inSubQuery
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.core.lowerCase
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insertReturning
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import java.time.LocalDate
import java.time.OffsetDateTime
import kotlin.uuid.Uuid

class GuestsRepository {
    // SQL NOW() so updated_at matches the column DEFAULT used on insert.
    private fun now() = CustomFunction<OffsetDateTime>("NOW", GuestsTable.updatedAt.columnType)

    // Bump the parent booking's updated_at so it doubles as a "guest roster changed" watermark.
    // The SES auto-submit discovery (BookingsRepository.noUnchangedRejectedAltaExists) compares it
    // against a rejected alta's last_attempt_at to decide whether re-filing is worthwhile — without
    // this, editing/adding/deleting a guest to fix a validation rejection would never unblock the
    // retry. Runs inside the caller's dbQuery transaction.
    private fun touchBooking(bookingId: Uuid) {
        BookingsTable.update({ BookingsTable.id eq bookingId }) {
            it[BookingsTable.updatedAt] = now()
        }
    }

    private fun bookingIdOf(guestId: Uuid): Uuid? =
        GuestsTable
            .select(GuestsTable.bookingId)
            .where { GuestsTable.id eq guestId }
            .firstOrNull()
            ?.get(GuestsTable.bookingId)

    suspend fun getGuestsByBooking(
        bookingId: Uuid,
        pageable: Pageable? = null,
    ): Page<Guest> =
        dbQuery {
            GuestsTable
                .selectAll()
                .where { GuestsTable.bookingId eq bookingId }
                .paginate(
                    pageable = pageable,
                    map = Guest.Companion,
                )
        }

    suspend fun getAllGuestsByBooking(bookingId: Uuid): List<Guest> =
        dbQuery {
            GuestsTable
                .selectAll()
                .where { GuestsTable.bookingId eq bookingId }
                .map { row -> Guest.from(row) }
        }

    suspend fun getGuestsByAccount(
        accountId: Uuid,
        userId: Uuid,
        filters: GuestsFilters = GuestsFilters(),
        pageable: Pageable? = null,
    ): Page<Guest> =
        dbQuery {
            val managerPropertyIds =
                PropertyAccessTable
                    .select(PropertyAccessTable.propertyId)
                    .where {
                        (PropertyAccessTable.userId eq userId) and
                            (PropertyAccessTable.accessLevel eq PropertyAccessLevel.Manager)
                    }

            var whereCondition =
                PropertiesTable.deletedAt.isNull() and
                    (
                        (PropertiesTable.accountId eq accountId) or
                            (PropertiesTable.id inSubQuery managerPropertyIds)
                    )

            if (filters.propertyId != null) {
                whereCondition = whereCondition and (GuestsTable.propertyId eq filters.propertyId)
            }

            if (!filters.channelId.isNullOrBlank()) {
                val pattern = "%${escapeLike(filters.channelId.trim().lowercase())}%"
                whereCondition = whereCondition and (BookingsTable.channelId.lowerCase() like pattern)
            }

            if (!filters.query.isNullOrBlank()) {
                val pattern = "%${escapeLike(filters.query.trim().lowercase())}%"
                whereCondition =
                    whereCondition and
                    (
                        (GuestsTable.firstName.lowerCase() like pattern) or
                            (GuestsTable.lastName.lowerCase() like pattern) or
                            (GuestsTable.email.lowerCase() like pattern)
                    )
            }

            val source = GuestsTable.innerJoin(PropertiesTable)

            // The bookings join only exists to serve the channelId predicate; skip it otherwise.
            val sourceWithBooking =
                if (!filters.channelId.isNullOrBlank()) {
                    source.join(
                        BookingsTable,
                        JoinType.INNER,
                        onColumn = GuestsTable.bookingId,
                        otherColumn = BookingsTable.id,
                    )
                } else {
                    source
                }

            sourceWithBooking
                .select(GuestsTable.columns)
                .where { whereCondition }
                .paginate(
                    pageable = pageable,
                    map = Guest.Companion,
                )
        }

    /**
     * Booking-level context for a page of guests, keyed by booking id. Joins bookings to
     * their property so the caller can gate the owner-private `would_host_again` by comparing
     * [GuestBookingContext.propertyAccountId] to the viewer's account. Returns an empty map for
     * an empty input.
     */
    suspend fun getGuestBookingContext(bookingIds: Collection<Uuid>): Map<Uuid, GuestBookingContext> {
        if (bookingIds.isEmpty()) return emptyMap()
        return dbQuery {
            BookingsTable
                .innerJoin(PropertiesTable)
                .select(
                    BookingsTable.id,
                    BookingsTable.channel,
                    BookingsTable.channelId,
                    BookingsTable.wouldHostAgain,
                    PropertiesTable.accountId,
                ).where { BookingsTable.id inList bookingIds }
                .associate { row ->
                    row[BookingsTable.id] to
                        GuestBookingContext(
                            bookingId = row[BookingsTable.id],
                            channel = row[BookingsTable.channel],
                            channelId = row[BookingsTable.channelId],
                            wouldHostAgain = row[BookingsTable.wouldHostAgain],
                            propertyAccountId = row[PropertiesTable.accountId],
                        )
                }
        }
    }

    /**
     * Cross-account guests list with no `property_access` enforcement, joined to each
     * guest's property, its booking (for the channel reference), and that property's owner
     * (`created_by_id`). Backs the admin guests view. Filters by `propertyId`, `channelId`
     * (substring of the booking's external reference), and/or free-text `query` (guest
     * name/email). Newest first.
     */
    suspend fun getAllGuests(
        filters: GuestsFilters = GuestsFilters(),
        pageable: Pageable? = null,
    ): Page<AdminGuestListItem> =
        dbQuery {
            var where: Op<Boolean> = PropertiesTable.deletedAt.isNull()

            if (filters.propertyId != null) {
                where = where and (GuestsTable.propertyId eq filters.propertyId)
            }

            if (!filters.channelId.isNullOrBlank()) {
                val pattern = "%${escapeLike(filters.channelId.trim().lowercase())}%"
                where = where and (BookingsTable.channelId.lowerCase() like pattern)
            }

            if (!filters.query.isNullOrBlank()) {
                val pattern = "%${escapeLike(filters.query.trim().lowercase())}%"
                where =
                    where and (
                        (GuestsTable.firstName.lowerCase() like pattern) or
                            (GuestsTable.lastName.lowerCase() like pattern) or
                            (GuestsTable.email.lowerCase() like pattern)
                    )
            }

            GuestsTable
                .innerJoin(PropertiesTable)
                .join(
                    BookingsTable,
                    JoinType.INNER,
                    onColumn = GuestsTable.bookingId,
                    otherColumn = BookingsTable.id,
                ).join(
                    UsersTable,
                    JoinType.INNER,
                    onColumn = PropertiesTable.createdById,
                    otherColumn = UsersTable.id,
                ).join(
                    CountriesTable,
                    JoinType.LEFT,
                    onColumn = GuestsTable.nationalityCountryId,
                    otherColumn = CountriesTable.id,
                ).selectAll()
                .where(where)
                .orderBy(GuestsTable.createdAt to SortOrder.DESC)
                .paginate(
                    pageable = pageable,
                    map = AdminGuestListItem.Companion,
                )
        }

    suspend fun getGuestsByProperty(
        propertyId: Uuid,
        pageable: Pageable? = null,
    ): Page<Guest> =
        dbQuery {
            GuestsTable
                .selectAll()
                .where { GuestsTable.propertyId eq propertyId }
                .paginate(
                    pageable = pageable,
                    map = Guest.Companion,
                )
        }

    suspend fun getGuest(guestId: Uuid): Guest? =
        dbQuery {
            GuestsTable
                .selectAll()
                .where { GuestsTable.id eq guestId }
                .firstNotNullOfOrNull { row ->
                    Guest.from(row)
                }
        }

    suspend fun createGuest(
        bookingId: Uuid,
        propertyId: Uuid,
        firstName: String,
        lastName: String,
        secondLastName: String? = null,
        nationalityCountryId: Uuid? = null,
        dateOfBirth: LocalDate? = null,
        sex: Gender? = null,
        residenceCountryId: Uuid? = null,
        residenceProvinceId: Uuid? = null,
        residenceCityId: Uuid? = null,
        residenceCity: String? = null,
        residenceAddress: String? = null,
        residencePostalCode: String? = null,
        documentType: DocumentType? = null,
        documentNumber: String? = null,
        documentSupportNumber: String? = null,
        email: String? = null,
        phone: String? = null,
        phonePrefix: String? = null,
        relationshipCode: String? = null,
        // Non-null means the guest opted in to marketing: stamps consent time,
        // wording version and a fresh unsubscribe token together.
        marketingConsentVersion: String? = null,
    ): Guest? =
        dbQuery {
            val result =
                GuestsTable.insertReturning {
                    it[GuestsTable.bookingId] = bookingId
                    it[GuestsTable.propertyId] = propertyId
                    it[GuestsTable.firstName] = firstName
                    it[GuestsTable.lastName] = lastName
                    it[GuestsTable.secondLastName] = secondLastName
                    it[GuestsTable.nationalityCountryId] = nationalityCountryId
                    it[GuestsTable.dateOfBirth] = dateOfBirth
                    it[GuestsTable.sex] = sex
                    it[GuestsTable.residenceCountryId] = residenceCountryId
                    it[GuestsTable.residenceProvinceId] = residenceProvinceId
                    it[GuestsTable.residenceCityId] = residenceCityId
                    it[GuestsTable.residenceCity] = residenceCity
                    it[GuestsTable.residenceAddress] = residenceAddress
                    it[GuestsTable.residencePostalCode] = residencePostalCode
                    it[GuestsTable.documentType] = documentType
                    it[GuestsTable.documentNumber] = documentNumber
                    it[GuestsTable.documentSupportNumber] = documentSupportNumber
                    it[GuestsTable.email] = email
                    it[GuestsTable.phone] = phone
                    it[GuestsTable.phonePrefix] = phonePrefix
                    it[GuestsTable.relationshipCode] = relationshipCode
                    if (marketingConsentVersion != null) {
                        // SQL NOW(), the clock that stamps marketing_suppressions.suppressed_at:
                        // the effective consent compares the two (GuestHydrationService).
                        it[GuestsTable.marketingConsentAt] = now()
                        it[GuestsTable.marketingConsentVersion] = marketingConsentVersion
                        it[GuestsTable.marketingUnsubscribeToken] = Uuid.random()
                    }
                }

            result.firstOrNull()?.let { row ->
                touchBooking(bookingId)
                Guest.from(row)
            }
        }

    suspend fun getGuestByMarketingUnsubscribeToken(token: Uuid): Guest? =
        dbQuery {
            GuestsTable
                .selectAll()
                .where { GuestsTable.marketingUnsubscribeToken eq token }
                .firstNotNullOfOrNull { row -> Guest.from(row) }
        }

    suspend fun updateGuest(
        guestId: Uuid,
        firstName: String? = null,
        lastName: String? = null,
        nationalityCountryId: Uuid? = null,
        dateOfBirth: LocalDate? = null,
        sex: Gender? = null,
        residenceCountryId: Uuid? = null,
        residenceProvinceId: Uuid? = null,
        residenceCityId: Uuid? = null,
        residenceCity: String? = null,
        residenceAddress: String? = null,
        residencePostalCode: String? = null,
        documentType: DocumentType? = null,
        documentNumber: String? = null,
        email: String? = null,
        phone: String? = null,
        phonePrefix: String? = null,
        relationshipCode: String? = null,
    ): Guest? =
        dbQuery {
            GuestsTable.update({ GuestsTable.id eq guestId }) {
                firstName?.let { value -> it[GuestsTable.firstName] = value }
                lastName?.let { value -> it[GuestsTable.lastName] = value }
                nationalityCountryId?.let { value -> it[GuestsTable.nationalityCountryId] = value }
                dateOfBirth?.let { value -> it[GuestsTable.dateOfBirth] = value }
                sex?.let { value -> it[GuestsTable.sex] = value }
                residenceCountryId?.let { value -> it[GuestsTable.residenceCountryId] = value }
                residenceProvinceId?.let { value -> it[GuestsTable.residenceProvinceId] = value }
                residenceCityId?.let { value -> it[GuestsTable.residenceCityId] = value }
                residenceCity?.let { value -> it[GuestsTable.residenceCity] = value }
                residenceAddress?.let { value -> it[GuestsTable.residenceAddress] = value }
                residencePostalCode?.let { value -> it[GuestsTable.residencePostalCode] = value }
                documentType?.let { value -> it[GuestsTable.documentType] = value }
                documentNumber?.let { value -> it[GuestsTable.documentNumber] = value }
                email?.let { value -> it[GuestsTable.email] = value }
                phone?.let { value -> it[GuestsTable.phone] = value }
                phonePrefix?.let { value -> it[GuestsTable.phonePrefix] = value }
                relationshipCode?.let { value -> it[GuestsTable.relationshipCode] = value }
                it[GuestsTable.updatedAt] = now()
            }

            bookingIdOf(guestId)?.let { touchBooking(it) }
            // Return the updated guest
            getGuest(guestId)
        }

    /**
     * Replace all editable fields of a guest. Unlike [updateGuest], a `null`
     * argument clears the column rather than skipping it. Use when the caller
     * is sending the full guest record (e.g. owner-side edit drawer that
     * always submits every field).
     */
    suspend fun replaceGuest(
        guestId: Uuid,
        firstName: String,
        lastName: String,
        secondLastName: String?,
        nationalityCountryId: Uuid?,
        dateOfBirth: LocalDate?,
        sex: Gender?,
        residenceCountryId: Uuid?,
        residenceProvinceId: Uuid?,
        residenceCityId: Uuid?,
        residenceCity: String?,
        residenceAddress: String?,
        residencePostalCode: String?,
        documentType: DocumentType?,
        documentNumber: String?,
        documentSupportNumber: String?,
        email: String?,
        phone: String?,
        phonePrefix: String?,
        relationshipCode: String?,
        // When the edit changes the email, the marketing opt-in recorded for the old
        // address no longer applies to the new one, so the consent stamp/version/token
        // are cleared (GDPR: consent is tied to the address that gave it).
        clearMarketingConsent: Boolean = false,
    ): Guest? =
        dbQuery {
            val updatedRows =
                GuestsTable.update({ GuestsTable.id eq guestId }) {
                    it[GuestsTable.firstName] = firstName
                    it[GuestsTable.lastName] = lastName
                    it[GuestsTable.secondLastName] = secondLastName
                    it[GuestsTable.nationalityCountryId] = nationalityCountryId
                    it[GuestsTable.dateOfBirth] = dateOfBirth
                    it[GuestsTable.sex] = sex
                    it[GuestsTable.residenceCountryId] = residenceCountryId
                    it[GuestsTable.residenceProvinceId] = residenceProvinceId
                    it[GuestsTable.residenceCityId] = residenceCityId
                    it[GuestsTable.residenceCity] = residenceCity
                    it[GuestsTable.residenceAddress] = residenceAddress
                    it[GuestsTable.residencePostalCode] = residencePostalCode
                    it[GuestsTable.documentType] = documentType
                    it[GuestsTable.documentNumber] = documentNumber
                    it[GuestsTable.documentSupportNumber] = documentSupportNumber
                    it[GuestsTable.email] = email
                    it[GuestsTable.phone] = phone
                    it[GuestsTable.phonePrefix] = phonePrefix
                    it[GuestsTable.relationshipCode] = relationshipCode
                    if (clearMarketingConsent) {
                        it[GuestsTable.marketingConsentAt] = null
                        it[GuestsTable.marketingConsentVersion] = null
                        it[GuestsTable.marketingUnsubscribeToken] = null
                    }
                    it[GuestsTable.updatedAt] = now()
                }
            if (updatedRows == 0) return@dbQuery null
            bookingIdOf(guestId)?.let { touchBooking(it) }
            GuestsTable
                .selectAll()
                .where { GuestsTable.id eq guestId }
                .firstNotNullOfOrNull { row -> Guest.from(row) }
        }

    suspend fun deleteGuest(guestId: Uuid): Boolean =
        dbQuery {
            // Resolve the parent booking before the row is gone so we can bump its watermark.
            val bookingId = bookingIdOf(guestId)
            val deleted = GuestsTable.deleteWhere { id eq guestId } > 0
            if (deleted && bookingId != null) touchBooking(bookingId)
            deleted
        }

    suspend fun deleteGuestsByBooking(bookingId: Uuid): Int =
        dbQuery {
            val deleted = GuestsTable.deleteWhere { GuestsTable.bookingId eq bookingId }
            if (deleted > 0) touchBooking(bookingId)
            deleted
        }
}
