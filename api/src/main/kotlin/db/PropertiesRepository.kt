package eu.kasify.db
import eu.kasify.db.model.PropertiesTable
import eu.kasify.db.model.PropertyAccessTable
import eu.kasify.db.model.PropertySmartlocksTable
import eu.kasify.db.model.SmartlockVendor
import eu.kasify.db.model.UsersTable
import eu.kasify.model.AdminPropertyListItem
import eu.kasify.model.Property
import eu.kasify.model.api.requests.NewPropertyRequest
import eu.kasify.model.api.requests.PropertyPatch
import eu.kasify.model.api.requests.normalizeSpanishTaxId
import eu.kasify.model.api.requests.parseTimeOfDay
import eu.kasify.security.KasifyUserPrincipal
import io.perracodex.exposed.pagination.Page
import io.perracodex.exposed.pagination.Pageable
import io.perracodex.exposed.pagination.paginate
import kotlinx.datetime.TimeZone
import org.jetbrains.exposed.v1.core.CustomFunction
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inSubQuery
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.core.lowerCase
import org.jetbrains.exposed.v1.core.notInSubQuery
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.updateReturning
import java.time.LocalTime
import java.time.OffsetDateTime
import kotlin.time.Clock
import kotlin.uuid.Uuid

open class PropertiesRepository {
    private val madridZone = TimeZone.Companion.of("Europe/Madrid")

    private fun now() = CustomFunction<OffsetDateTime>("NOW", PropertiesTable.updatedAt.columnType)

    private val defaultCheckinTime = LocalTime.of(17, 0)
    private val defaultCheckoutTime = LocalTime.of(11, 0)

    open suspend fun createProperty(
        user: KasifyUserPrincipal,
        property: NewPropertyRequest,
        countryId: Uuid,
        tzone: String,
        cityName: String,
        provinceId: Uuid?,
        cityId: Uuid?,
    ): Property {
        lateinit var newPropertyId: Uuid

        val checkinTime = property.checkinTime?.let { parseTimeOfDay(it) } ?: defaultCheckinTime
        val checkoutTime = property.checkoutTime?.let { parseTimeOfDay(it) } ?: defaultCheckoutTime

        // Code + assignment travel together (validated in the route: a non-blank code requires the
        // account-verified integration id). Derived once, used for both the insert and the DTO.
        val sesEstablishmentCode = property.sesEstablishmentCode?.takeIf { it.isNotBlank() }
        val sesIntegrationId = property.sesIntegrationId.takeIf { sesEstablishmentCode != null }

        dbQuery {
            newPropertyId =
                PropertiesTable.insert {
                    it[PropertiesTable.name] = property.name
                    it[PropertiesTable.accountId] = user.accountId
                    it[PropertiesTable.address] = property.address
                    it[PropertiesTable.city] = cityName
                    it[PropertiesTable.zipcode] = property.zipcode
                    it[PropertiesTable.countryId] = countryId
                    it[PropertiesTable.tzone] = tzone
                    it[PropertiesTable.createdById] = user.userId
                    it[PropertiesTable.defaultCleanerId] = user.userId
                    it[PropertiesTable.provinceId] = provinceId
                    it[PropertiesTable.cityId] = cityId
                    it[PropertiesTable.checkinTime] = checkinTime
                    it[PropertiesTable.checkoutTime] = checkoutTime
                    sesEstablishmentCode?.let { code ->
                        it[PropertiesTable.sesEstablishmentCode] = code
                        it[PropertiesTable.sesIntegrationId] = sesIntegrationId
                    }
                    property.sesEstablishmentType?.let { type ->
                        it[PropertiesTable.sesEstablishmentType] = type
                    }
                    property.arrendadorNif?.let { nif ->
                        it[PropertiesTable.arrendadorNif] = normalizeSpanishTaxId(nif).ifEmpty { null }
                    }
                    property.hasInternet?.let { internet ->
                        it[PropertiesTable.hasInternet] = internet
                    }
                    property.roomsCount?.let { rooms ->
                        it[PropertiesTable.roomsCount] = rooms
                    }
                }[PropertiesTable.id]
        }

        return Property(
            id = newPropertyId,
            accountId = user.accountId,
            name = property.name,
            address = property.address,
            city = cityName,
            zipcode = property.zipcode,
            countryId = countryId,
            tzone = tzone,
            createdById = user.userId,
            defaultCleanerId = user.userId,
            cleaningDurationMinutes = 120,
            checkinTime = checkinTime,
            checkoutTime = checkoutTime,
            sesEstablishmentCode = sesEstablishmentCode,
            sesIntegrationId = sesIntegrationId,
            sesEstablishmentType = property.sesEstablishmentType,
            arrendadorNif = property.arrendadorNif,
            hasInternet = property.hasInternet,
            roomsCount = property.roomsCount ?: 1,
            coverImageUrl = null,
            provinceId = provinceId,
            cityId = cityId,
        )
    }

    /**
     * Get all properties the user has access to:
     * - Account properties (user.accountId = property.accountId) - owner access
     * - Granted properties via property_access table - crew access
     */
    open suspend fun getProperties(
        user: KasifyUserPrincipal,
        pageable: Pageable?,
    ): Page<Property> =
        dbQuery {
            // Subquery to get property IDs the user has crew access to
            val crewPropertyIds =
                PropertyAccessTable
                    .select(PropertyAccessTable.propertyId)
                    .where { PropertyAccessTable.userId eq user.userId }

            PropertiesTable
                .selectAll()
                .where {
                    (PropertiesTable.deletedAt.isNull()) and
                        (
                            // Owner: account membership
                            (PropertiesTable.accountId eq user.accountId) or
                                // Crew: property_access grant
                                (PropertiesTable.id inSubQuery crewPropertyIds)
                        )
                }.paginate(
                    pageable = pageable,
                    map = Property.Companion,
                )
        }

    /**
     * Cross-account properties list with no `property_access` enforcement, joined to the
     * user that created each property (`created_by_id`). Backs the admin properties view.
     * Optional free-text `query` matches property name/city or the owner's name/email.
     * The tri-state `sesConfigured`/`nukiConfigured`/`unifiConfigured` filters keep (true),
     * exclude (false) or ignore (null) properties by integration state. Newest first.
     */
    open suspend fun getAllPropertiesWithOwner(
        query: String?,
        pageable: Pageable?,
        propertyId: Uuid? = null,
        sesConfigured: Boolean? = null,
        nukiConfigured: Boolean? = null,
        unifiConfigured: Boolean? = null,
    ): Page<AdminPropertyListItem> =
        dbQuery {
            var where: Op<Boolean> = PropertiesTable.deletedAt.isNull()

            if (propertyId != null) {
                where = where and (PropertiesTable.id eq propertyId)
            }

            if (sesConfigured != null) {
                where =
                    where and
                    if (sesConfigured) {
                        PropertiesTable.sesEstablishmentCode.isNotNull()
                    } else {
                        PropertiesTable.sesEstablishmentCode.isNull()
                    }
            }

            listOf(
                SmartlockVendor.Nuki to nukiConfigured,
                SmartlockVendor.Unifi to unifiConfigured,
            ).forEach { (vendor, configured) ->
                if (configured != null) {
                    val vendorPropertyIds =
                        PropertySmartlocksTable
                            .select(PropertySmartlocksTable.propertyId)
                            .where { PropertySmartlocksTable.vendor eq vendor }
                    where =
                        where and
                        if (configured) {
                            PropertiesTable.id inSubQuery vendorPropertyIds
                        } else {
                            PropertiesTable.id notInSubQuery vendorPropertyIds
                        }
                }
            }

            if (!query.isNullOrBlank()) {
                val pattern = "%${escapeLike(query.trim().lowercase())}%"
                where =
                    where and (
                        (PropertiesTable.name.lowerCase() like pattern) or
                            (PropertiesTable.city.lowerCase() like pattern) or
                            (UsersTable.name.lowerCase() like pattern) or
                            (UsersTable.lastName.lowerCase() like pattern) or
                            (UsersTable.email.lowerCase() like pattern)
                    )
            }

            PropertiesTable
                .join(
                    UsersTable,
                    JoinType.INNER,
                    onColumn = PropertiesTable.createdById,
                    otherColumn = UsersTable.id,
                ).selectAll()
                .where(where)
                .orderBy(PropertiesTable.createdAt to SortOrder.DESC)
                .paginate(
                    pageable = pageable,
                    map = AdminPropertyListItem.Companion,
                )
        }

    open suspend fun getProperty(id: Uuid): Property? =
        dbQuery {
            PropertiesTable
                .selectAll()
                .where {
                    (PropertiesTable.id eq id) and (PropertiesTable.deletedAt.isNull())
                }.singleOrNull()
                ?.let {
                    Property.Companion.from(it)
                }
        }

    open suspend fun updateProperty(
        id: Uuid,
        property: NewPropertyRequest,
        countryId: Uuid,
        cityName: String,
        provinceId: Uuid?,
        cityId: Uuid?,
    ): Property? =
        dbQuery {
            PropertiesTable
                .updateReturning(
                    where = {
                        (PropertiesTable.id eq id) and (PropertiesTable.deletedAt.isNull())
                    },
                ) {
                    it[PropertiesTable.name] = property.name
                    it[PropertiesTable.address] = property.address
                    it[PropertiesTable.city] = cityName
                    it[PropertiesTable.zipcode] = property.zipcode
                    it[PropertiesTable.countryId] = countryId
                    it[PropertiesTable.provinceId] = provinceId
                    it[PropertiesTable.cityId] = cityId
                    property.defaultCleanerId?.let { cleanerId ->
                        it[PropertiesTable.defaultCleanerId] = cleanerId
                    }
                    property.checkinTime?.let { value ->
                        parseTimeOfDay(value)?.let { time -> it[PropertiesTable.checkinTime] = time }
                    }
                    property.checkoutTime?.let { value ->
                        parseTimeOfDay(value)?.let { time -> it[PropertiesTable.checkoutTime] = time }
                    }
                    // Validated in the route: blank clears code + assignment; a non-blank code on
                    // an unassigned property carries the (account-verified) integration id.
                    property.sesEstablishmentCode?.let { code ->
                        if (code.isBlank()) {
                            it[PropertiesTable.sesEstablishmentCode] = null
                            it[PropertiesTable.sesIntegrationId] = null
                        } else {
                            it[PropertiesTable.sesEstablishmentCode] = code
                            property.sesIntegrationId?.let { integrationId ->
                                it[PropertiesTable.sesIntegrationId] = integrationId
                            }
                        }
                        // Any establishment-code save clears the config-error pause (optimistic,
                        // like the credential-flag clear on re-saved credentials): a still-wrong
                        // code re-flags and re-notifies on the next tick, bounded to one email per
                        // save attempt. A cleared code drops the property out of SES entirely, so
                        // the flag is meaningless either way.
                        it[PropertiesTable.sesConfigErrorMessage] = null
                        it[PropertiesTable.sesConfigErrorAt] = null
                    }
                    property.sesEstablishmentType?.let { type ->
                        it[PropertiesTable.sesEstablishmentType] = type
                    }
                    property.arrendadorNif?.let { nif ->
                        it[PropertiesTable.arrendadorNif] = normalizeSpanishTaxId(nif).ifEmpty { null }
                    }
                    property.hasInternet?.let { internet ->
                        it[PropertiesTable.hasInternet] = internet
                    }
                    property.roomsCount?.let { rooms ->
                        it[PropertiesTable.roomsCount] = rooms
                    }
                }.singleOrNull()
                ?.let { Property.from(it) }
        }

    open suspend fun patchProperty(
        id: Uuid,
        patch: PropertyPatch,
    ): Property? =
        dbQuery {
            PropertiesTable
                .updateReturning(
                    where = {
                        (PropertiesTable.id eq id) and (PropertiesTable.deletedAt.isNull())
                    },
                ) {
                    patch.name?.let { value -> it[PropertiesTable.name] = value }
                    patch.address?.let { value -> it[PropertiesTable.address] = value }
                    patch.zipcode?.let { value -> it[PropertiesTable.zipcode] = value }
                    patch.defaultCleanerId?.let { value -> it[PropertiesTable.defaultCleanerId] = value }
                    // Validated in the route: blank clears code + assignment (the "unconfigure"
                    // step of a reassignment); a non-blank code on an unassigned property carries
                    // the (account-verified) integration id.
                    patch.sesEstablishmentCode?.let { value ->
                        if (value.isBlank()) {
                            it[PropertiesTable.sesEstablishmentCode] = null
                            it[PropertiesTable.sesIntegrationId] = null
                        } else {
                            it[PropertiesTable.sesEstablishmentCode] = value
                            patch.sesIntegrationId?.let { integrationId ->
                                it[PropertiesTable.sesIntegrationId] = integrationId
                            }
                        }
                        // Optimistic clear of the config-error pause on any code save — same
                        // semantics as the update path above.
                        it[PropertiesTable.sesConfigErrorMessage] = null
                        it[PropertiesTable.sesConfigErrorAt] = null
                    }
                    patch.sesEstablishmentType?.let { value -> it[PropertiesTable.sesEstablishmentType] = value }
                    // Stored normalized (canonical form); an explicit "" clears the NIF to NULL.
                    patch.arrendadorNif?.let { value ->
                        it[PropertiesTable.arrendadorNif] = normalizeSpanishTaxId(value).ifEmpty { null }
                    }
                    patch.hasInternet?.let { value -> it[PropertiesTable.hasInternet] = value }
                    patch.roomsCount?.let { value -> it[PropertiesTable.roomsCount] = value }
                    patch.checkinTime?.let { value ->
                        parseTimeOfDay(value)?.let { time -> it[PropertiesTable.checkinTime] = time }
                    }
                    patch.checkoutTime?.let { value ->
                        parseTimeOfDay(value)?.let { time -> it[PropertiesTable.checkoutTime] = time }
                    }
                    it[PropertiesTable.updatedAt] = now()
                }.singleOrNull()
                ?.let { Property.from(it) }
        }

    /**
     * Flag a property's SES config as invalid (SES reported an unknown establishment code).
     * Only writes when the flag is not already set — first-writer-wins — and returns whether
     * *this* call made the null→error transition, so the caller can fire the one-time owner
     * notification without duplicates when several bookings of the property fail in the same tick.
     */
    open suspend fun markSesConfigError(
        propertyId: Uuid,
        message: String?,
    ): Boolean =
        dbQuery {
            val now = Clock.System.now()
            val currentDateTime = now.toJavaInstant().atZone(madridZone.toJavaZoneId()).toOffsetDateTime()
            PropertiesTable.update(
                {
                    (PropertiesTable.id eq propertyId) and
                        (PropertiesTable.deletedAt.isNull()) and
                        (PropertiesTable.sesConfigErrorMessage.isNull())
                },
            ) {
                it[PropertiesTable.sesConfigErrorMessage] = (message ?: "Establishment code rejected by SES").take(1000)
                it[PropertiesTable.sesConfigErrorAt] = currentDateTime
                it[PropertiesTable.updatedAt] = currentDateTime
            } > 0
        }

    /**
     * Clear a property's SES config-error flag (the config proved valid again — a SES filing for
     * the property succeeded, or the owner re-saved the establishment code). Returns whether a
     * flag was cleared.
     */
    open suspend fun clearSesConfigError(propertyId: Uuid): Boolean =
        dbQuery {
            val now = Clock.System.now()
            val currentDateTime = now.toJavaInstant().atZone(madridZone.toJavaZoneId()).toOffsetDateTime()
            PropertiesTable.update(
                {
                    (PropertiesTable.id eq propertyId) and
                        (PropertiesTable.sesConfigErrorMessage.isNotNull())
                },
            ) {
                it[PropertiesTable.sesConfigErrorMessage] = null
                it[PropertiesTable.sesConfigErrorAt] = null
                it[PropertiesTable.updatedAt] = currentDateTime
            } > 0
        }

    open suspend fun updateCoverImageUrl(
        id: Uuid,
        url: String?,
    ): Boolean =
        dbQuery {
            PropertiesTable.update({ (PropertiesTable.id eq id) and (PropertiesTable.deletedAt.isNull()) }) {
                it[coverImageUrl] = url
            } > 0
        }

    open suspend fun deleteProperty(id: Uuid): Boolean =
        dbQuery {
            val now = Clock.System.now()
            val currentDateTime = now.toJavaInstant().atZone(madridZone.toJavaZoneId()).toOffsetDateTime()

            PropertiesTable.update({ (PropertiesTable.id eq id) and (PropertiesTable.deletedAt.isNull()) }) {
                it[deletedAt] = currentDateTime
            } > 0
        }
}
