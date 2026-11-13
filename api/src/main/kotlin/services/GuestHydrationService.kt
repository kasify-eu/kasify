package eu.kasify.services

import eu.kasify.db.GeoRepository
import eu.kasify.db.MarketingSuppressionsRepository
import eu.kasify.model.GuestBookingContext
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.time.OffsetDateTime
import kotlin.uuid.Uuid
import eu.kasify.model.Guest as DomainGuest
import eu.kasify.model.api.City as ApiCity
import eu.kasify.model.api.Country as ApiCountry
import eu.kasify.model.api.Guest as ApiGuest
import eu.kasify.model.api.Province as ApiProvince

class GuestHydrationService(
    private val geoRepository: GeoRepository,
    private val marketingSuppressionsRepository: MarketingSuppressionsRepository,
) {
    /**
     * Hydrate guests with geo names and, when [bookingContext] is supplied, their booking's
     * channel reference. `would_host_again` is owner-private: it is only carried through when
     * [viewerAccountId] owns the booking's property; managers of shared properties get null.
     */
    suspend fun hydrate(
        guests: List<DomainGuest>,
        bookingContext: Map<Uuid, GuestBookingContext> = emptyMap(),
        viewerAccountId: Uuid? = null,
    ): List<ApiGuest> =
        coroutineScope {
            if (guests.isEmpty()) return@coroutineScope emptyList()

            val countryIds =
                guests.flatMapTo(mutableSetOf()) { guest ->
                    listOfNotNull(guest.nationalityCountryId, guest.residenceCountryId)
                }
            val provinceIds = guests.mapNotNullTo(mutableSetOf()) { it.residenceProvinceId }
            val cityIds = guests.mapNotNullTo(mutableSetOf()) { it.residenceCityId }

            // Effective marketing consent needs each consented address's latest unsubscribe
            // time (see hasEffectiveMarketingConsent). Only consented guests' emails need the
            // lookup, and it depends on none of the geo queries — run it concurrently instead
            // of adding a fourth serial round-trip.
            val consentedEmails =
                guests.mapNotNullTo(mutableSetOf()) { guest ->
                    guest.email.takeIf { guest.marketingConsentAt != null }
                }
            val suppressedAtDeferred =
                async { marketingSuppressionsRepository.suppressedAt(consentedEmails) }

            val countries = geoRepository.getCountriesByIds(countryIds)
            val provinces = geoRepository.getProvincesByIds(provinceIds)
            val cities = geoRepository.getCitiesByIds(cityIds)
            val suppressedAt = suppressedAtDeferred.await()

            guests.map { guest ->
                val context = bookingContext[guest.bookingId]
                val ownsProperty = viewerAccountId != null && context?.propertyAccountId == viewerAccountId
                ApiGuest.from(
                    domainGuest = guest,
                    nationality = guest.nationalityCountryId?.let { countries[it]?.let(ApiCountry::from) },
                    residenceCountry = guest.residenceCountryId?.let { countries[it]?.let(ApiCountry::from) },
                    residenceProvince = guest.residenceProvinceId?.let { provinces[it]?.let(ApiProvince::from) },
                    residenceCity = guest.residenceCityId?.let { cities[it]?.let(ApiCity::from) },
                    channel = context?.channel,
                    channelId = context?.channelId,
                    wouldHostAgain = if (ownsProperty) context?.wouldHostAgain else null,
                    marketingConsent = hasEffectiveMarketingConsent(guest, suppressedAt),
                )
            }
        }

    suspend fun hydrate(guest: DomainGuest): ApiGuest = hydrate(listOf(guest)).first()

    /**
     * Effective marketing consent of one guest row: the row recorded a consent, and its
     * address has no unsubscribe at or after that consent. An unsubscribe therefore withdraws
     * every consent given up to that moment (in every account), while a consent recorded
     * later counts for this row only — it never lifts the suppression for other rows, and
     * other accounts' unsubscribes that predate it don't affect it.
     *
     * A tie counts as unsubscribed (fail safe). Both timestamps are SQL NOW() values stored
     * as TIMESTAMPTZ, so they share one clock and microsecond precision. `isBefore` compares
     * instants, independent of the offsets the values were read with.
     */
    private fun hasEffectiveMarketingConsent(
        guest: DomainGuest,
        suppressedAt: Map<String, OffsetDateTime>,
    ): Boolean {
        val consentAt = guest.marketingConsentAt ?: return false
        val email = guest.email ?: return false
        val lastUnsubscribe = suppressedAt[email.lowercase()] ?: return true
        return lastUnsubscribe.isBefore(consentAt)
    }
}
