package eu.kasify.db
import eu.kasify.db.model.BookingChannel
import eu.kasify.db.model.BookingStatus
import eu.kasify.db.model.BookingsTable
import eu.kasify.db.model.CalendarEventsTable
import eu.kasify.db.model.EventType
import eu.kasify.db.model.PaymentType
import eu.kasify.db.model.PropertiesTable
import eu.kasify.db.model.PropertyAccessLevel
import eu.kasify.db.model.PropertyAccessTable
import eu.kasify.db.model.SesCommunicationsTable
import eu.kasify.model.Booking
import eu.kasify.model.SesCommunicationOperation
import eu.kasify.model.SesCommunicationStatus
import eu.kasify.model.SesCommunicationType
import eu.kasify.model.api.Visibility
import eu.kasify.model.api.requests.BookingData
import eu.kasify.model.api.requests.BookingsFilters
import eu.kasify.security.KasifyUserPrincipal
import io.perracodex.exposed.pagination.Page
import io.perracodex.exposed.pagination.Pageable
import io.perracodex.exposed.pagination.paginate
import kotlinx.datetime.TimeZone
import org.jetbrains.exposed.v1.core.CustomFunction
import org.jetbrains.exposed.v1.core.Exists
import org.jetbrains.exposed.v1.core.NotExists
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.inSubQuery
import org.jetbrains.exposed.v1.core.innerJoin
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.core.lowerCase
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.datetime.date
import org.jetbrains.exposed.v1.jdbc.insertReturning
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.uuid.Uuid

class BookingsRepository {
    suspend fun cancelBooking(bookingId: Uuid) {
        dbQuery {
            BookingsTable.update({ BookingsTable.id eq bookingId }) {
                it[BookingsTable.status] = BookingStatus.Cancelled
                it[BookingsTable.cancelledAt] = now()
                it[BookingsTable.updatedAt] = now()
            }
        }
    }

    suspend fun clearCheckinCompletedAt(bookingId: Uuid) {
        dbQuery {
            BookingsTable.update({ BookingsTable.id eq bookingId }) {
                it[BookingsTable.checkinCompletedAt] = null
                it[BookingsTable.updatedAt] = now()
            }
        }
    }

    /*
     * SELECT COUNT(*)
FROM reservas
WHERE apartamento_id = $1
  AND estado != 'cancelada'
  AND (
    (fecha_inicio <= $2 AND fecha_fin > $2) OR  -- La nueva reserva empieza durante una existente
    (fecha_inicio < $3 AND fecha_fin >= $3) OR  -- La nueva reserva termina durante una existente
    (fecha_inicio >= $2 AND fecha_fin <= $3)    -- Una reserva existente está completamente dentro de la nueva
  );
     * */
    suspend fun count(
        propertyId: Uuid,
        from: LocalDate,
        to: LocalDate,
    ): Long =
        dbQuery {
            BookingsTable
                .innerJoin(CalendarEventsTable, { BookingsTable.calendarEventId }, { CalendarEventsTable.id })
                .innerJoin(PropertiesTable, { BookingsTable.propertyId }, { PropertiesTable.id })
                .selectAll()
                .where {
                    (BookingsTable.propertyId eq propertyId) and
                        (BookingsTable.status eq BookingStatus.Confirmed) and
                        (PropertiesTable.deletedAt.isNull()) and
                        (
                            ((CalendarEventsTable.from.date() lessEq from.toKotlinLocalDate()) and (CalendarEventsTable.to.date() greater from.toKotlinLocalDate())) or
                                ((CalendarEventsTable.from.date() less to.toKotlinLocalDate()) and (CalendarEventsTable.to.date() greaterEq to.toKotlinLocalDate())) or
                                ((CalendarEventsTable.from.date() greaterEq from.toKotlinLocalDate()) and (CalendarEventsTable.to.date() lessEq to.toKotlinLocalDate())) or
                                ((CalendarEventsTable.from.date() lessEq from.toKotlinLocalDate()) and (CalendarEventsTable.to.date() greaterEq to.toKotlinLocalDate()))
                        )
                }.count()
        }

    suspend fun createBooking(
        bookingData: BookingData,
        propertyId: Uuid,
    ): Booking {
        require(bookingData.from != null) { "Booking date is required" }
        require(bookingData.to != null) { "Booking date is required" }

        val bookingStatus = bookingData.status ?: BookingStatus.Confirmed
        var bookingChannel = bookingData.channel
        var bookingChannelId = bookingData.channelId
        if (bookingChannel == null) {
            bookingChannel = BookingChannel.Direct
        }

        if (bookingChannel != BookingChannel.Direct) {
            require(bookingChannelId != null) { "CBooking channelId is required" }
        } else {
            if (bookingChannelId == null) {
                bookingChannelId = generateBookingChannelId()
            }
        }

        // Get property-specific timezone and configured stay times
        val stay = getPropertyStayConfig(propertyId)
        val propertyZoneId = stay.zoneId

        lateinit var newBookingResult: Booking

        val fromWithCheckinTime =
            bookingData.from
                .atTime(stay.checkinTime)
                .atZone(propertyZoneId)
                .toOffsetDateTime()

        val toWithCheckoutTime =
            bookingData.to
                .atTime(stay.checkoutTime)
                .atZone(propertyZoneId)
                .toOffsetDateTime()

        dbQuery {
            val newEventResult =
                CalendarEventsTable.insertReturning {
                    it[CalendarEventsTable.from] = fromWithCheckinTime
                    it[CalendarEventsTable.to] = toWithCheckoutTime
                    it[CalendarEventsTable.role] = EventType.booking
                    it[CalendarEventsTable.propertyId] = propertyId
                }

            newEventResult.firstOrNull()?.let { it ->
                val newEventId = it[CalendarEventsTable.id]

                val bookingInsertResult =
                    BookingsTable.insertReturning {
                        it[BookingsTable.calendarEventId] = newEventId
                        it[BookingsTable.propertyId] = propertyId
                        it[BookingsTable.channel] = bookingChannel
                        it[BookingsTable.channelId] = bookingChannelId
                        it[BookingsTable.status] = bookingStatus
                        it[BookingsTable.paymentType] = bookingData.paymentType ?: PaymentType.Platform
                        it[BookingsTable.notes] = bookingData.notes
                        it[BookingsTable.guestFirstName] = bookingData.guestFirstName
                        it[BookingsTable.guestLastName] = bookingData.guestLastName
                        it[BookingsTable.guestEmail] = bookingData.guestEmail
                        it[BookingsTable.guestPhone] = bookingData.guestPhone
                        it[BookingsTable.adultsCount] = bookingData.adultsCount
                        it[BookingsTable.childrenCount] = bookingData.childrenCount
                        it[BookingsTable.infantsCount] = bookingData.infantsCount
                        it[BookingsTable.specialRequests] = bookingData.specialRequests
                        it[BookingsTable.arrivalTime] = bookingData.arrivalTime
                        it[BookingsTable.departureTime] = bookingData.departureTime
                    }

                bookingInsertResult.firstOrNull()?.let { newBooking ->
                    // Convert to kotlinx TimeZone for the model
                    val kotlinxPropertyZone = TimeZone.of(propertyZoneId.id)
                    newBookingResult = Booking.fromResultRow(it, newBooking, kotlinxPropertyZone)
                }
            }
        }

        return newBookingResult
    }

    suspend fun getBooking(id: Uuid): Booking? =
        dbQuery {
            BookingsTable
                .innerJoin(CalendarEventsTable, { BookingsTable.calendarEventId }, { CalendarEventsTable.id })
                .innerJoin(PropertiesTable, { BookingsTable.propertyId }, { PropertiesTable.id })
                .selectAll()
                .where {
                    (BookingsTable.id eq id) and
                        (PropertiesTable.deletedAt.isNull())
                }.firstNotNullOfOrNull { row ->
                    Booking.from(row)
                }
        }

    suspend fun getBookingByCheckinCode(checkinCode: String): Booking? =
        dbQuery {
            BookingsTable
                .innerJoin(CalendarEventsTable, { BookingsTable.calendarEventId }, { CalendarEventsTable.id })
                .innerJoin(PropertiesTable, { BookingsTable.propertyId }, { PropertiesTable.id })
                .selectAll()
                .where {
                    (BookingsTable.checkinCode eq checkinCode) and
                        (PropertiesTable.deletedAt.isNull())
                }.firstNotNullOfOrNull { row ->
                    Booking.from(row)
                }
        }

    suspend fun getBookingByPropertyAndChannelId(
        propertyId: Uuid,
        channelId: String,
    ): Booking? =
        dbQuery {
            BookingsTable
                .innerJoin(CalendarEventsTable, { BookingsTable.calendarEventId }, { CalendarEventsTable.id })
                .innerJoin(PropertiesTable, { BookingsTable.propertyId }, { PropertiesTable.id })
                .selectAll()
                .where {
                    (BookingsTable.propertyId eq propertyId) and
                        (BookingsTable.channelId eq channelId) and
                        (PropertiesTable.deletedAt.isNull())
                }.firstNotNullOfOrNull { row ->
                    Booking.Companion.from(row)
                }
        }

    /**
     * Cross-account bookings list with no `property_access` enforcement. Backs the
     * admin bookings view. Filters (`from`/`to`/`propertyId`/`query`/`channel`) are
     * applied via the shared `addBookingsFilters` helper.
     */
    suspend fun getBookings(
        filters: BookingsFilters,
        pageable: Pageable,
    ): Page<Booking> =
        dbQuery {
            var where: Op<Boolean> = PropertiesTable.deletedAt.isNull()
            where = addBookingsFilters(filters, where, managerFieldsVisible = Op.TRUE)

            BookingsTable
                .innerJoin(CalendarEventsTable, { BookingsTable.calendarEventId }, { CalendarEventsTable.id })
                .innerJoin(PropertiesTable, { BookingsTable.propertyId }, { PropertiesTable.id })
                .selectAll()
                .where(where)
                .orderBy(CalendarEventsTable.from to SortOrder.ASC)
                .paginate(
                    pageable = pageable,
                    map = Booking.Companion,
                )
        }

    /**
     * Bookings of a single property. [callerVisibility] is the caller's visibility on that
     * property; below MANAGER the free-text `query` only matches fields the caller can see.
     */
    suspend fun getBookingsByProperty(
        propertyId: Uuid,
        callerVisibility: Visibility,
        filters: BookingsFilters? = null,
        pageable: Pageable? = null,
    ): Page<Booking> =
        dbQuery {
            val query =
                BookingsTable
                    .innerJoin(CalendarEventsTable, { BookingsTable.calendarEventId }, { CalendarEventsTable.id })
                    .innerJoin(PropertiesTable, { BookingsTable.propertyId }, { PropertiesTable.id })
                    .selectAll()

            var whereCondition =
                (BookingsTable.propertyId eq propertyId) and
                    (PropertiesTable.deletedAt.isNull())

            val managerFieldsVisible: Op<Boolean> =
                if (callerVisibility >= Visibility.MANAGER) Op.TRUE else Op.FALSE
            whereCondition = addBookingsFilters(filters, whereCondition, managerFieldsVisible)

            query
                .where(whereCondition)
                .orderBy(CalendarEventsTable.from to SortOrder.ASC)
                .paginate(
                    pageable = pageable,
                    map = Booking.Companion,
                )
        }

    /**
     * Get bookings for every property the user can see: properties owned through
     * their account, plus properties granted via property_access (excluding
     * contractors, who are blocked from booking data).
     *
     * The free-text `query` only matches MANAGER-level fields on properties where the
     * user is an account member or a manager (same rule as `AccessInfo.toVisibility()`).
     */
    suspend fun getBookingsForUser(
        user: KasifyUserPrincipal,
        filters: BookingsFilters? = null,
        pageable: Pageable? = null,
    ): Page<Booking> =
        dbQuery {
            val crewPropertyIds =
                PropertyAccessTable
                    .select(PropertyAccessTable.propertyId)
                    .where {
                        (PropertyAccessTable.userId eq user.userId) and
                            (PropertyAccessTable.accessLevel neq PropertyAccessLevel.Contractor)
                    }

            val query =
                BookingsTable
                    .innerJoin(PropertiesTable, { BookingsTable.propertyId }, { PropertiesTable.id })
                    .innerJoin(CalendarEventsTable, { BookingsTable.calendarEventId }, { CalendarEventsTable.id })
                    .selectAll()

            var whereCondition =
                PropertiesTable.deletedAt.isNull() and
                    (
                        (PropertiesTable.accountId eq user.accountId) or
                            (PropertiesTable.id inSubQuery crewPropertyIds)
                    )

            val managerPropertyIds =
                PropertyAccessTable
                    .select(PropertyAccessTable.propertyId)
                    .where {
                        (PropertyAccessTable.userId eq user.userId) and
                            (PropertyAccessTable.accessLevel eq PropertyAccessLevel.Manager)
                    }
            val managerFieldsVisible =
                (PropertiesTable.accountId eq user.accountId) or
                    (PropertiesTable.id inSubQuery managerPropertyIds)

            whereCondition = addBookingsFilters(filters, whereCondition, managerFieldsVisible)

            query
                .where(whereCondition)
                .orderBy(CalendarEventsTable.from to SortOrder.ASC)
                .paginate(
                    pageable = pageable,
                    map = Booking.Companion,
                )
        }

    /**
     * Pre-filter for PV alta candidates (submission job work-set (a)): the same check-in /
     * grace-period / not-cancelled / SES-configured gate as the legacy query, but "not yet filed"
     * is now keyed on `ses_communication` (no active PV alta) rather than the `bookings.ses_*`
     * columns. This is only an efficiency pre-filter — `SesHospedajesService.submitAlta` re-checks
     * eligibility against the comm rows authoritatively.
     */
    suspend fun getBookingsPendingPvAlta(): List<Booking> =
        dbQuery {
            BookingsTable
                .innerJoin(CalendarEventsTable, { BookingsTable.calendarEventId }, { CalendarEventsTable.id })
                .innerJoin(PropertiesTable, { BookingsTable.propertyId }, { PropertiesTable.id })
                .selectAll()
                .where { pvAltaPendingCondition() }
                .map { row -> Booking.Companion.from(row) }
        }

    /**
     * Pre-filter for RH alta candidates (submission job work-set (b)): confirmed + direct-channel +
     * SES-configured booking with no active RH alta yet. The per-account `rhDirectBookingsEnabled`
     * opt-out is enforced authoritatively in `SesHospedajesService.checkRhEligibility`, so it is not
     * re-encoded in this pre-filter (which would require joining the encrypted integrations table).
     */
    suspend fun getBookingsPendingRhAlta(): List<Booking> =
        dbQuery {
            BookingsTable
                .innerJoin(CalendarEventsTable, { BookingsTable.calendarEventId }, { CalendarEventsTable.id })
                .innerJoin(PropertiesTable, { BookingsTable.propertyId }, { PropertiesTable.id })
                .selectAll()
                .where { rhAltaPendingCondition() }
                .map { row -> Booking.Companion.from(row) }
        }

    // No *unchanged* rejected alta of [type] is blocking auto-retry. A `rejected` alta is a SES
    // validation/business rejection: re-filing the identical data would just be rejected again, so we
    // hold off until the booking or its guests change. `bookings.updated_at` is the watermark — it is
    // bumped on every booking edit and, via GuestsRepository.touchBooking, on every guest add/edit/delete.
    // The alta is "unchanged" (and therefore blocking) when its last attempt is at or after that
    // watermark. Note this only gates `rejected`; a `failed` alta (transient SOAP/transport error) is
    // never matched here, so those keep retrying every tick as before.
    private fun noUnchangedRejectedAltaExists(type: SesCommunicationType): Op<Boolean> =
        NotExists(
            SesCommunicationsTable
                .selectAll()
                .where {
                    (SesCommunicationsTable.bookingId eq BookingsTable.id) and
                        (SesCommunicationsTable.communicationType eq type) and
                        (SesCommunicationsTable.operation eq SesCommunicationOperation.Alta) and
                        (SesCommunicationsTable.status eq SesCommunicationStatus.Rejected) and
                        (SesCommunicationsTable.lastAttemptAt.isNotNull()) and
                        (BookingsTable.updatedAt lessEq SesCommunicationsTable.lastAttemptAt)
                },
        )

    // A `failed` alta of [type] exists for the booking. A failed attempt means the booking was
    // discovered while inside its submission window and the filing is still owed to SES, so it
    // bypasses the PV/RH time windows: otherwise a booking whose window expires during an outage
    // (SOAP transport failure or the account-level credential-error pause) would silently never be
    // re-picked once the problem clears.
    private fun failedAltaExists(type: SesCommunicationType): Op<Boolean> =
        Exists(
            SesCommunicationsTable
                .selectAll()
                .where {
                    (SesCommunicationsTable.bookingId eq BookingsTable.id) and
                        (SesCommunicationsTable.communicationType eq type) and
                        (SesCommunicationsTable.operation eq SesCommunicationOperation.Alta) and
                        (SesCommunicationsTable.status eq SesCommunicationStatus.Failed)
                },
        )

    // No active (submitted/processing/accepted) alta of [type] exists for the booking yet.
    private fun noActiveAltaExists(type: SesCommunicationType): Op<Boolean> =
        NotExists(
            SesCommunicationsTable
                .selectAll()
                .where {
                    (SesCommunicationsTable.bookingId eq BookingsTable.id) and
                        (SesCommunicationsTable.communicationType eq type) and
                        (SesCommunicationsTable.operation eq SesCommunicationOperation.Alta) and
                        (
                            SesCommunicationsTable.status inList
                                listOf(
                                    SesCommunicationStatus.Submitted,
                                    SesCommunicationStatus.Processing,
                                    SesCommunicationStatus.Accepted,
                                )
                        )
                },
        )

    private fun pvAltaPendingCondition(): Op<Boolean> {
        val today = OffsetDateTime.now(ZoneOffset.UTC).toLocalDate()
        val checkinThreshold = OffsetDateTime.now().minusHours(SES_SUBMISSION_CHECKIN_MARGIN_HOURS)
        // PV is only picked up while the booking is active or up to one day after check-out:
        // the checkout date must be no earlier than yesterday (today - 1 day). Booking that ended
        // more than a day ago is past its PV window.
        val earliestCheckout = today.minusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC)
        // The window applies to first discovery only: a booking with a `failed` alta was already
        // inside its window once and the filing is still owed, so it stays eligible after the
        // window closes (see failedAltaExists).
        val withinPvWindow =
            (CalendarEventsTable.from lessEq checkinThreshold) and
                (CalendarEventsTable.to greaterEq earliestCheckout)
        return (BookingsTable.checkinCompletedAt.isNotNull()) and
            (withinPvWindow or failedAltaExists(SesCommunicationType.PV)) and
            (BookingsTable.cancelledAt.isNull()) and
            (BookingsTable.status eq BookingStatus.Confirmed) and
            (PropertiesTable.sesEstablishmentCode.isNotNull()) and
            (PropertiesTable.deletedAt.isNull()) and
            // Strict SES submission gate (assignment + enabled + unflagged integration + unflagged
            // property config) — shared, see SesCredentialsErrorGate.kt.
            sesPropertySubmittable() and
            noActiveAltaExists(SesCommunicationType.PV) and
            noUnchangedRejectedAltaExists(SesCommunicationType.PV)
    }

    private fun rhAltaPendingCondition(): Op<Boolean> {
        // RH is only picked up until the day the booking starts (inclusive). Once the start day has
        // passed, the reservation window is over: the check-in date must be today or later.
        val startOfToday =
            OffsetDateTime
                .now(ZoneOffset.UTC)
                .toLocalDate()
                .atStartOfDay()
                .atOffset(ZoneOffset.UTC)
        return (BookingsTable.channel eq BookingChannel.Direct) and
            // Same first-discovery-only window semantics as PV: a failed RH alta stays eligible
            // past the check-in day so an outage can't permanently swallow the filing.
            ((CalendarEventsTable.from greaterEq startOfToday) or failedAltaExists(SesCommunicationType.RH)) and
            (BookingsTable.cancelledAt.isNull()) and
            (BookingsTable.status eq BookingStatus.Confirmed) and
            (PropertiesTable.sesEstablishmentCode.isNotNull()) and
            (PropertiesTable.deletedAt.isNull()) and
            // Strict SES submission gate (assignment + enabled + unflagged integration + unflagged
            // property config) — shared, see SesCredentialsErrorGate.kt.
            sesPropertySubmittable() and
            noActiveAltaExists(SesCommunicationType.RH) and
            noUnchangedRejectedAltaExists(SesCommunicationType.RH)
    }

    suspend fun getCurrentBooking(propertyId: Uuid): Booking? {
        // Get property-specific timezone for accurate "current" time
        val propertyZoneId = getPropertyTimezone(propertyId)

        return dbQuery {
            val now = Instant.now()
            val currentDateTime = now.atZone(propertyZoneId).toOffsetDateTime()

            BookingsTable
                .innerJoin(CalendarEventsTable, { BookingsTable.calendarEventId }, { CalendarEventsTable.id })
                .innerJoin(PropertiesTable, { BookingsTable.propertyId }, { PropertiesTable.id })
                .selectAll()
                .where {
                    (BookingsTable.propertyId eq propertyId) and
                        (CalendarEventsTable.from lessEq currentDateTime) and
                        (CalendarEventsTable.to greaterEq currentDateTime) and
                        (PropertiesTable.deletedAt.isNull())
                }.firstNotNullOfOrNull { row ->
                    Booking.Companion.from(row)
                }
        }
    }

    suspend fun getFutureConfirmedBookingsByPropertyAndChannel(
        propertyId: Uuid,
        channel: BookingChannel,
    ): List<Booking> =
        dbQuery {
            val now = Instant.now()
            val utcZone = ZoneId.of("UTC")
            val currentDateTime = now.atZone(utcZone).toOffsetDateTime()

            BookingsTable
                .innerJoin(CalendarEventsTable, { BookingsTable.calendarEventId }, { CalendarEventsTable.id })
                .innerJoin(PropertiesTable, { BookingsTable.propertyId }, { PropertiesTable.id })
                .selectAll()
                .where {
                    (BookingsTable.propertyId eq propertyId) and
                        (BookingsTable.channel eq channel) and
                        (BookingsTable.status eq BookingStatus.Confirmed) and
                        (PropertiesTable.deletedAt.isNull()) and
                        (CalendarEventsTable.from greaterEq currentDateTime)
                }.map { row ->
                    Booking.Companion.from(row)
                }
        }

    suspend fun getNextBooking(
        propertyId: Uuid,
        fromDate: LocalDate,
    ): Booking? =
        dbQuery {
            BookingsTable
                .innerJoin(CalendarEventsTable, { BookingsTable.calendarEventId }, { CalendarEventsTable.id })
                .innerJoin(PropertiesTable, { BookingsTable.propertyId }, { PropertiesTable.id })
                .selectAll()
                .where {
                    (BookingsTable.propertyId eq propertyId) and
                        (BookingsTable.status eq BookingStatus.Confirmed) and
                        (PropertiesTable.deletedAt.isNull()) and
                        (CalendarEventsTable.from.date() greaterEq fromDate.toKotlinLocalDate())
                }.orderBy(CalendarEventsTable.from to SortOrder.ASC)
                .limit(1)
                .firstNotNullOfOrNull { row ->
                    Booking.Companion.from(row)
                }
        }

    suspend fun markCheckinCompletedAt(bookingId: Uuid) {
        dbQuery {
            BookingsTable.update({ BookingsTable.id eq bookingId }) {
                it[BookingsTable.checkinCompletedAt] = now()
                it[BookingsTable.updatedAt] = now()
            }
        }
    }

    suspend fun updateBooking(
        id: Uuid,
        data: BookingData,
    ): Booking? {
        // Get the property ID and timezone for this booking
        val propertyId =
            dbQuery {
                BookingsTable
                    .select(BookingsTable.propertyId)
                    .where { BookingsTable.id eq id }
                    .firstOrNull()
                    ?.get(BookingsTable.propertyId)
                    ?.let { Uuid.parse(it.toString()) }
            } ?: return null

        val stay = getPropertyStayConfig(propertyId)
        val propertyZoneId = stay.zoneId

        return dbQuery {
            // Get the calendar event ID for this booking
            val calendarEventRow =
                BookingsTable
                    .innerJoin(CalendarEventsTable, { BookingsTable.calendarEventId }, { CalendarEventsTable.id })
                    .select(CalendarEventsTable.id, CalendarEventsTable.from, CalendarEventsTable.to)
                    .where { BookingsTable.id eq id }
                    .firstOrNull()
            val calendarEventId = calendarEventRow?.get(CalendarEventsTable.id)

            if (calendarEventId != null) {
                // Resolve the new from/to datetimes for the calendar event.
                // Date comes from the request when provided, otherwise from the existing event.
                // Time-of-day comes from the request when provided, otherwise from the property default
                // (only when the date changed) or the existing event time-of-day.
                val existingFrom = calendarEventRow[CalendarEventsTable.from]
                val existingTo = calendarEventRow[CalendarEventsTable.to]

                val newFromDate = data.from ?: existingFrom.atZoneSameInstant(propertyZoneId).toLocalDate()
                val newToDate = data.to ?: existingTo.atZoneSameInstant(propertyZoneId).toLocalDate()

                val newCheckinTime =
                    data.checkinTime
                        ?: if (data.from != null) stay.checkinTime else existingFrom.atZoneSameInstant(propertyZoneId).toLocalTime()
                val newCheckoutTime =
                    data.checkoutTime
                        ?: if (data.to != null) stay.checkoutTime else existingTo.atZoneSameInstant(propertyZoneId).toLocalTime()

                val fromChanged = data.from != null || data.checkinTime != null
                val toChanged = data.to != null || data.checkoutTime != null

                if (fromChanged || toChanged) {
                    val newFromOdt = newFromDate.atTime(newCheckinTime).atZone(propertyZoneId).toOffsetDateTime()
                    val newToOdt = newToDate.atTime(newCheckoutTime).atZone(propertyZoneId).toOffsetDateTime()
                    CalendarEventsTable.update({ CalendarEventsTable.id eq calendarEventId }) {
                        if (fromChanged) it[CalendarEventsTable.from] = newFromOdt
                        if (toChanged) it[CalendarEventsTable.to] = newToOdt
                    }
                }

                // Update the booking
                BookingsTable.update({ BookingsTable.id eq id }) {
                    data.notes?.let { notes ->
                        it[BookingsTable.notes] = notes
                    }
                    data.channel?.let { channel ->
                        it[BookingsTable.channel] = channel
                    }
                    data.channelId?.let { channelId ->
                        it[BookingsTable.channelId] = channelId
                    }
                    data.status?.let { status ->
                        it[BookingsTable.status] = status
                    }
                    data.paymentType?.let { paymentType ->
                        it[BookingsTable.paymentType] = paymentType
                    }
                    data.guestFirstName?.let { guestFirstName ->
                        it[BookingsTable.guestFirstName] = guestFirstName
                    }
                    data.guestLastName?.let { guestLastName ->
                        it[BookingsTable.guestLastName] = guestLastName
                    }
                    data.guestEmail?.let { guestEmail ->
                        it[BookingsTable.guestEmail] = guestEmail
                    }
                    data.guestPhone?.let { guestPhone ->
                        it[BookingsTable.guestPhone] = guestPhone
                    }
                    data.adultsCount?.let { adultsCount ->
                        it[BookingsTable.adultsCount] = adultsCount
                    }
                    data.childrenCount?.let { childrenCount ->
                        it[BookingsTable.childrenCount] = childrenCount
                    }
                    data.infantsCount?.let { infantsCount ->
                        it[BookingsTable.infantsCount] = infantsCount
                    }
                    data.specialRequests?.let { specialRequests ->
                        it[BookingsTable.specialRequests] = specialRequests
                    }
                    data.wouldHostAgain?.let { wouldHostAgain ->
                        it[BookingsTable.wouldHostAgain] = wouldHostAgain
                    }
                    data.guestReviewNotes?.let { guestReviewNotes ->
                        it[BookingsTable.guestReviewNotes] = guestReviewNotes
                    }
                    data.arrivalTime?.let { arrivalTime ->
                        it[BookingsTable.arrivalTime] = arrivalTime
                    }
                    data.departureTime?.let { departureTime ->
                        it[BookingsTable.departureTime] = departureTime
                    }
                    it[BookingsTable.updatedAt] = now()
                }

                // Return the updated booking
                BookingsTable
                    .innerJoin(CalendarEventsTable, { BookingsTable.calendarEventId }, { CalendarEventsTable.id })
                    .innerJoin(PropertiesTable, { BookingsTable.propertyId }, { PropertiesTable.id })
                    .selectAll()
                    .where {
                        (BookingsTable.id eq id) and
                            (PropertiesTable.deletedAt.isNull())
                    }.firstNotNullOfOrNull { row ->
                        Booking.Companion.from(row)
                    }
            } else {
                null
            }
        }
    }

    private data class PropertyStayConfig(
        val zoneId: ZoneId,
        val checkinTime: java.time.LocalTime,
        val checkoutTime: java.time.LocalTime,
    )

    /**
     * Applies [filters] to [whereCondition]. [managerFieldsVisible] is true for rows whose
     * MANAGER-level fields the caller may see; the free-text `query` matches guest last name,
     * channel id and SES lote only on those rows, so a lower-visibility caller can't use match /
     * no-match as an oracle on fields `SecuritySerializer` strips from the response.
     */
    private fun addBookingsFilters(
        filters: BookingsFilters?,
        whereCondition: Op<Boolean>,
        managerFieldsVisible: Op<Boolean>,
    ): Op<Boolean> {
        var condition = whereCondition

        filters?.let { filter ->
            // Overlap semantics: booking overlaps [from, to] when checkin <= to AND checkout >= from
            filter.from?.let { from ->
                condition = condition and (CalendarEventsTable.to.date() greaterEq from)
            }

            filter.to?.let { to ->
                condition = condition and (CalendarEventsTable.from.date() lessEq to)
            }

            filter.propertyId?.let { propertyId ->
                condition = condition and (BookingsTable.propertyId eq propertyId)
            }

            filter.query?.let { query ->
                val pattern = "%${escapeLike(query.lowercase())}%"
                condition = condition and (
                    (BookingsTable.guestFirstName.lowerCase() like pattern) or
                        (
                            managerFieldsVisible and (
                                (BookingsTable.guestLastName.lowerCase() like pattern) or
                                    (BookingsTable.channelId.lowerCase() like pattern) or
                                    sesLoteMatches(pattern)
                            )
                        )
                )
            }

            filter.channel?.let { channel ->
                condition = condition and (BookingsTable.channel eq channel)
            }
        }

        return condition
    }

    private suspend fun channelIdExists(channelId: String): Boolean =
        dbQuery {
            BookingsTable
                .select(BookingsTable.channelId)
                .where { BookingsTable.channelId eq channelId }
                .count() > 0
        }

    private suspend fun generateBookingChannelId(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        var candidate: String
        do {
            candidate = (1..10).map { chars.random() }.joinToString("")
        } while (channelIdExists(candidate))
        return candidate
    }

    private suspend fun getPropertyStayConfig(propertyId: Uuid): PropertyStayConfig =
        dbQuery {
            PropertiesTable
                .select(PropertiesTable.tzone, PropertiesTable.checkinTime, PropertiesTable.checkoutTime)
                .where { PropertiesTable.id eq propertyId }
                .firstOrNull()
                ?.let {
                    PropertyStayConfig(
                        zoneId = ZoneId.of(it[PropertiesTable.tzone]),
                        checkinTime = it[PropertiesTable.checkinTime],
                        checkoutTime = it[PropertiesTable.checkoutTime],
                    )
                }
                ?: PropertyStayConfig(
                    zoneId = ZoneId.of("UTC"),
                    checkinTime = java.time.LocalTime.of(17, 0),
                    checkoutTime = java.time.LocalTime.of(11, 0),
                )
        }

    /**
     * Helper method to get the timezone for a specific property
     * Falls back to UTC if property not found or timezone not set
     */
    private suspend fun getPropertyTimezone(propertyId: Uuid): ZoneId =
        dbQuery {
            PropertiesTable
                .select(PropertiesTable.tzone)
                .where { PropertiesTable.id eq propertyId }
                .firstOrNull()
                ?.get(PropertiesTable.tzone)
                ?.let { ZoneId.of(it) }
                ?: ZoneId.of("UTC") // Fallback to UTC if not found
        }

    // Custom function to get current timestamp with timezone from database
    private fun now() = CustomFunction<OffsetDateTime>("NOW", BookingsTable.updatedAt.columnType)

    // EXISTS over any SES communication of the booking whose lote matches the search pattern.
    private fun sesLoteMatches(pattern: String): Op<Boolean> =
        Exists(
            SesCommunicationsTable
                .selectAll()
                .where {
                    (SesCommunicationsTable.bookingId eq BookingsTable.id) and
                        (SesCommunicationsTable.lote.lowerCase() like pattern)
                },
        )

    private companion object {
        // Grace period after the scheduled check-in datetime before SES Hospedajes submission
        // is attempted. Gives the guest time to actually arrive at the property. Overridable via
        // the SES_SUBMISSION_CHECKIN_MARGIN_HOURS env var (set 0 in development to submit
        // immediately when testing the pipeline end-to-end).
        val SES_SUBMISSION_CHECKIN_MARGIN_HOURS: Long =
            System.getenv("SES_SUBMISSION_CHECKIN_MARGIN_HOURS")?.toLongOrNull() ?: 3L
    }
}
