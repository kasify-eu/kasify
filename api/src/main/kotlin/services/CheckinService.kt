package eu.kasify.services

import eu.kasify.db.BookingsRepository
import eu.kasify.db.GeoRepository
import eu.kasify.db.GuestsRepository
import eu.kasify.db.PropertiesRepository
import eu.kasify.db.PropertyAccessRepository
import eu.kasify.db.UsersRepository
import eu.kasify.db.model.BookingStatus
import eu.kasify.db.model.PropertyAccessLevel
import eu.kasify.model.Booking
import eu.kasify.model.Guest
import eu.kasify.model.Property
import eu.kasify.model.api.CheckinGuest
import eu.kasify.model.api.requests.GUEST_MINOR_AGE
import eu.kasify.model.api.requests.GuestRegistrationRequest
import eu.kasify.model.api.requests.MARKETING_CONSENT_VERSION
import eu.kasify.model.api.responses.CheckinResponse
import eu.kasify.routes.Errors
import eu.kasify.routes.exceptions.BadParamRequestException
import eu.kasify.routes.exceptions.InternalServerException
import eu.kasify.routes.exceptions.NotFoundException
import eu.kasify.services.notifications.NotificationService
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import java.time.LocalDate
import java.time.Period
import java.time.ZoneId
import kotlin.uuid.Uuid

class CheckinService(
    private val bookingsRepository: BookingsRepository,
    private val propertiesRepository: PropertiesRepository,
    private val guestsRepository: GuestsRepository,
    private val geoRepository: GeoRepository,
    private val usersRepository: UsersRepository,
    private val propertyAccessRepository: PropertyAccessRepository,
    private val notificationService: NotificationService,
) {
    private val logger = KotlinLogging.logger {}

    suspend fun getCheckinSnapshot(checkinCode: String): CheckinResponse {
        val booking =
            bookingsRepository.getBookingByCheckinCode(checkinCode)
                ?: throw NotFoundException("Booking not found")
        val property =
            propertiesRepository.getProperty(booking.propertyId)
                ?: throw NotFoundException("Property not found")
        val guests = guestsRepository.getAllGuestsByBooking(booking.id)

        return buildResponse(booking, property, guests)
    }

    suspend fun registerGuest(
        checkinCode: String,
        request: GuestRegistrationRequest,
    ): CheckinResponse {
        val booking =
            bookingsRepository.getBookingByCheckinCode(checkinCode)
                ?: throw NotFoundException("Booking not found")
        val property =
            propertiesRepository.getProperty(booking.propertyId)
                ?: throw NotFoundException("Property not found")

        createGuestForBooking(booking, property, request)

        val updatedBooking =
            bookingsRepository.getBookingByCheckinCode(checkinCode)
                ?: throw NotFoundException("Booking not found")
        val updatedGuests = guestsRepository.getAllGuestsByBooking(booking.id)

        return buildResponse(updatedBooking, property, updatedGuests)
    }

    /**
     * Validate the request, enforce booking lifecycle (closed/full/duplicate)
     * and persist a new guest row. Returns the created [Guest]. Marks the
     * booking's check-in as completed once the registered guest count reaches
     * the expected total.
     *
     * Shared between the public check-in flow and the authenticated owner
     * endpoint, so the same SES Hospedajes rules apply to both surfaces.
     */
    suspend fun createGuestForBooking(
        booking: Booking,
        property: Property,
        request: GuestRegistrationRequest,
    ): Guest {
        if (isClosed(booking, property)) {
            throw BadParamRequestException(
                Errors.ERROR_CHECKIN_CLOSED,
                code = Errors.CODE_CHECKIN_CLOSED,
            )
        }

        val existing = guestsRepository.getAllGuestsByBooking(booking.id)
        val expected = expectedGuestCount(booking)

        if (expected > 0 && existing.size >= expected) {
            throw BadParamRequestException(
                Errors.ERROR_CHECKIN_BOOKING_FULL,
                code = Errors.CODE_CHECKIN_BOOKING_FULL,
            )
        }

        if (!request.documentNumber.isNullOrBlank()) {
            val isDuplicate =
                existing.any { guest ->
                    !guest.documentNumber.isNullOrBlank() &&
                        guest.documentNumber.equals(request.documentNumber, ignoreCase = true)
                }
            if (isDuplicate) {
                throw BadParamRequestException(
                    Errors.ERROR_CHECKIN_DUPLICATE_DOCUMENT,
                    code = Errors.CODE_CHECKIN_DUPLICATE_DOCUMENT,
                )
            }
        }

        requireRelationshipCodeIfNeeded(request)

        val (nationalityCountryId, residenceCountryId) = resolveCountryIds(request)

        // Marketing opt-in (GDPR Art. 6(1)(a) / LSSI Art. 21). The checkbox is
        // optional on the form, and consent is only stamped for adults (18+)
        // who supplied an email — enforced here regardless of what the client
        // sent, so a tampered request can't record consent for a minor.
        val guestAge = Period.between(request.dateOfBirth, LocalDate.now()).years
        val marketingConsentGranted =
            request.marketingConsent &&
                guestAge >= GUEST_MINOR_AGE &&
                !request.email.isNullOrBlank()

        val created =
            guestsRepository.createGuest(
                bookingId = booking.id,
                propertyId = property.id,
                firstName = request.firstName,
                lastName = request.lastName,
                secondLastName = request.secondLastName,
                nationalityCountryId = nationalityCountryId,
                dateOfBirth = request.dateOfBirth,
                sex = request.sex,
                residenceCountryId = residenceCountryId,
                residenceProvinceId = request.residenceProvinceId,
                residenceCityId = request.residenceCityId,
                residenceCity = request.residenceCity,
                residenceAddress = request.residenceAddress,
                residencePostalCode = request.residencePostalCode,
                documentType = request.documentType,
                documentNumber = request.documentNumber,
                documentSupportNumber = request.documentSupportNumber,
                phone = request.phone,
                phonePrefix = request.phonePrefix,
                email = request.email,
                relationshipCode = request.relationshipCode,
                marketingConsentVersion = if (marketingConsentGranted) MARKETING_CONSENT_VERSION else null,
            ) ?: throw InternalServerException(
                Errors.ERROR_CHECKIN_REGISTRATION_FAILED,
                code = Errors.CODE_CHECKIN_REGISTRATION_FAILED,
            )

        // The consent is recorded on this guest row only and never lifts a
        // suppression on the address: the email is self-asserted (anyone with a
        // check-in code can type someone else's), so it must not cancel an
        // opt-out recorded elsewhere. A fresh consent still supersedes an older
        // opt-out for this row, because GuestHydrationService only applies
        // unsubscribes recorded at or after the row's marketing_consent_at.

        val updatedGuests = guestsRepository.getAllGuestsByBooking(booking.id)
        if (expected > 0 && updatedGuests.size >= expected) {
            bookingsRepository.markCheckinCompletedAt(booking.id)
            notifyCheckinCompleted(booking, property)
        }

        return created
    }

    /**
     * Notify the property's owners and managers that guest check-in is complete. Best-effort: any
     * failure (recipient lookup or enqueue) is logged and swallowed so it can never break guest
     * registration or roll back the check-in completion. Staff and contractors are not notified.
     */
    private suspend fun notifyCheckinCompleted(
        booking: Booking,
        property: Property,
    ) {
        try {
            val ownerIds = usersRepository.getUserIdsByAccountId(property.accountId)
            val managerIds =
                propertyAccessRepository
                    .getPropertyMembersByPropertyId(property.id)
                    .filter { it.accessLevel == PropertyAccessLevel.Manager }
                    .map { it.id }
            val recipients = usersRepository.getUsersByIds((ownerIds + managerIds).distinct())
            notificationService.enqueueCheckinCompleted(booking, property, recipients)
        } catch (t: Throwable) {
            // Never swallow cancellation — let structured concurrency tear down the request cleanly.
            if (t is CancellationException) throw t
            logger.error(t) { "Failed to enqueue checkin_completed notification for booking=${booking.id}" }
        }
    }

    /**
     * Replace all editable fields of an existing guest with the values in
     * [request]. The same SES Hospedajes validation rules apply (handled by
     * RequestValidation on the route), plus a duplicate-document check that
     * excludes the guest being edited.
     */
    suspend fun replaceGuestForBooking(
        guest: Guest,
        booking: Booking,
        request: GuestRegistrationRequest,
    ): Guest {
        if (!request.documentNumber.isNullOrBlank()) {
            val others =
                guestsRepository
                    .getAllGuestsByBooking(booking.id)
                    .filter { it.id != guest.id }
            val isDuplicate =
                others.any { other ->
                    !other.documentNumber.isNullOrBlank() &&
                        other.documentNumber.equals(request.documentNumber, ignoreCase = true)
                }
            if (isDuplicate) {
                throw BadParamRequestException(
                    Errors.ERROR_CHECKIN_DUPLICATE_DOCUMENT,
                    code = Errors.CODE_CHECKIN_DUPLICATE_DOCUMENT,
                )
            }
        }

        requireRelationshipCodeIfNeeded(request, existing = guest.relationshipCode)

        val (nationalityCountryId, residenceCountryId) = resolveCountryIds(request)

        // If the address changes, the opt-in recorded for the old email can't carry over.
        val emailChanged =
            request.email?.trim()?.lowercase() != guest.email?.trim()?.lowercase()

        return guestsRepository.replaceGuest(
            guestId = guest.id,
            firstName = request.firstName,
            lastName = request.lastName,
            secondLastName = request.secondLastName,
            nationalityCountryId = nationalityCountryId,
            dateOfBirth = request.dateOfBirth,
            sex = request.sex,
            residenceCountryId = residenceCountryId,
            residenceProvinceId = request.residenceProvinceId,
            residenceCityId = request.residenceCityId,
            residenceCity = request.residenceCity,
            residenceAddress = request.residenceAddress,
            residencePostalCode = request.residencePostalCode,
            documentType = request.documentType,
            documentNumber = request.documentNumber,
            documentSupportNumber = request.documentSupportNumber,
            email = request.email,
            phone = request.phone,
            phonePrefix = request.phonePrefix,
            relationshipCode = request.relationshipCode ?: guest.relationshipCode,
            clearMarketingConsent = emailChanged,
        ) ?: throw NotFoundException(Errors.ERROR_GUEST_NOT_FOUND, code = Errors.CODE_GUEST_NOT_FOUND)
    }

    /**
     * SES Hospedajes asks for the "parentesco" (kinship) link between a minor
     * traveler and an accompanying adult in the booking. We store it on the
     * minor's record (matching chekin / autoregistro.es), so the field is
     * required only when the guest being registered is themselves a minor
     * (< 18). Adults may still supply a value and we keep it.
     *
     * [existing] is the value currently stored on the guest row when editing
     * an existing record — passing it lets owner-side PATCHes that don't
     * include `relationship_code` keep the prior value without re-prompting.
     */
    private fun requireRelationshipCodeIfNeeded(
        request: GuestRegistrationRequest,
        existing: String? = null,
    ) {
        val age = Period.between(request.dateOfBirth, LocalDate.now()).years
        val isMinor = age < GUEST_MINOR_AGE
        val effective = request.relationshipCode ?: existing
        if (isMinor && effective.isNullOrBlank()) {
            throw BadParamRequestException(
                Errors.ERROR_CHECKIN_RELATIONSHIP_REQUIRED,
                code = Errors.CODE_CHECKIN_RELATIONSHIP_REQUIRED,
            )
        }
    }

    private suspend fun resolveCountryIds(request: GuestRegistrationRequest): Pair<Uuid, Uuid?> {
        val nationalityCountry =
            geoRepository.getCountryByAlpha3(request.nationalityCountryCode)
                ?: throw BadParamRequestException("Unknown nationality_country_code: ${request.nationalityCountryCode}")
        val residenceCountry =
            request.residenceCountryCode?.let { code ->
                geoRepository.getCountryByAlpha3(code)
                    ?: throw BadParamRequestException("Unknown residence_country_code: $code")
            }
        return nationalityCountry.id to residenceCountry?.id
    }

    private fun isClosed(
        booking: Booking,
        property: Property,
    ): Boolean {
        if (booking.status == BookingStatus.Cancelled) return true
        if (expectedGuestCount(booking) == 0) return true
        val propertyZone = ZoneId.of(property.tzone)
        val today = LocalDate.now(propertyZone)
        return booking.to.isBefore(today)
    }

    private fun isCompleted(booking: Booking): Boolean = booking.checkinCompletedAt != null

    private fun expectedGuestCount(booking: Booking): Int =
        (booking.adultsCount ?: 0) +
            (booking.childrenCount ?: 0) +
            (booking.infantsCount ?: 0)

    private fun buildResponse(
        booking: Booking,
        property: Property,
        guests: List<Guest>,
    ): CheckinResponse {
        val closed = isClosed(booking, property)
        val visibleGuests = if (closed) emptyList() else guests
        return CheckinResponse.from(
            booking = booking,
            property = property,
            guests = visibleGuests.map { CheckinGuest.from(it) },
            checkinCompleted = isCompleted(booking),
            checkinClosed = closed,
            hasMinors = hasMinors(booking, guests),
        )
    }

    private fun hasMinors(
        booking: Booking,
        guests: List<Guest>,
    ): Boolean {
        if ((booking.childrenCount ?: 0) + (booking.infantsCount ?: 0) > 0) return true
        val today = LocalDate.now()
        return guests.any { guest ->
            val dob = guest.dateOfBirth ?: return@any false
            Period.between(dob, today).years < GUEST_MINOR_AGE
        }
    }
}
