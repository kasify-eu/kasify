package eu.kasify.services

import eu.kasify.db.BookingAccessCodesRepository
import eu.kasify.db.SmartlocksRepository
import eu.kasify.db.model.SmartlockVendor
import eu.kasify.model.Booking
import eu.kasify.model.BookingAccessCode
import eu.kasify.model.Property
import eu.kasify.model.PropertySmartlock
import eu.kasify.model.api.SmartLock
import eu.kasify.plugins.PluginFactory
import io.github.oshai.kotlinlogging.KotlinLogging
import java.time.LocalDateTime
import kotlin.uuid.Uuid

data class CreateAccessCodesResult(
    val accessCode: String,
    val accessCodes: List<BookingAccessCode>,
    val failedSmartlocks: List<PropertySmartlock>,
)

class SmartlocksService(
    private val bookingAccessCodesRepository: BookingAccessCodesRepository,
    private val smartLocksRepository: SmartlocksRepository,
    private val pluginFactory: PluginFactory,
) {
    private val logger = KotlinLogging.logger {}

    suspend fun getSmartLocksStatusByProperty(propertyId: Uuid): List<SmartLock> {
        val smartLocks = smartLocksRepository.getSmartlocksByProperty(propertyId)

        // Enhance smartLocks with status for Nuki devices
        return smartLocks.map { smartLock ->
            if (smartLock.vendor == SmartlockVendor.Nuki && smartLock.nukiSmartlock != null) {
                try {
                    val nukiPlugin = pluginFactory.getNukiPluginByIntegrationId(smartLock.nukiSmartlock.nukiIntegrationId)
                    val nukiSmartlockId = smartLock.nukiSmartlock.nukiSmartlockId.toLong()
                    val nukiSmartLock = nukiPlugin.nukiPlugin.getSmartLock(nukiSmartlockId)

                    val enhancedSmartlock =
                        PropertySmartlock(
                            id = smartLock.id,
                            propertyId = smartLock.propertyId,
                            vendor = smartLock.vendor,
                            name = smartLock.name,
                            nukiSmartlock = smartLock.nukiSmartlock,
                            unifiSmartlock = smartLock.unifiSmartlock,
                            lockState = nukiSmartLock.state.state,
                            batteryLevel = nukiSmartLock.state.batteryCharge,
                            batteryCharging = nukiSmartLock.state.batteryCharging,
                        )
                    SmartLock.from(enhancedSmartlock)
                } catch (e: Exception) {
                    logger.error(e) { "Failed to fetch Nuki smartLock status for ${smartLock.id}" }
                    SmartLock.from(smartLock).copy(offline = true)
                }
            } else {
                SmartLock.from(smartLock)
            }
        }
    }

    suspend fun deleteAccessCode(
        accessCode: BookingAccessCode,
        accountId: Uuid,
    ) {
        val smartLock = smartLocksRepository.getSmartlockById(accessCode.smartlockId)
        smartLock?.let {
            val plugin =
                if (smartLock.vendor == SmartlockVendor.Nuki && smartLock.nukiSmartlock != null) {
                    pluginFactory.getNukiPluginByIntegrationId(smartLock.nukiSmartlock.nukiIntegrationId)
                } else {
                    pluginFactory.getSmartLockPlugin(accountId, smartLock.vendor)
                }
            plugin.deleteAccessCode(accessCode, smartLock)
            bookingAccessCodesRepository.delete(accessCode.id)
        }
    }

    /**
     * Propagate new validity dates to every existing access code on a booking,
     * both at the smart-lock vendor and in our database.
     *
     * Vendor failures on individual codes are logged but do not abort the loop —
     * mirrors the best-effort semantics of [createAccessCodes].
     */
    suspend fun updateAccessCodeDatesForBooking(
        bookingId: Uuid,
        accountId: Uuid,
        fromUtc: LocalDateTime,
        toUtc: LocalDateTime,
    ) {
        val accessCodes = bookingAccessCodesRepository.getByBookingId(bookingId)
        for (accessCode in accessCodes) {
            val smartlock = accessCode.smartlock ?: continue
            try {
                val plugin =
                    if (smartlock.vendor == SmartlockVendor.Nuki && smartlock.nukiSmartlock != null) {
                        pluginFactory.getNukiPluginByIntegrationId(smartlock.nukiSmartlock.nukiIntegrationId)
                    } else {
                        pluginFactory.getSmartLockPlugin(accountId, smartlock.vendor)
                    }
                plugin.updateAccessCodeDates(accessCode, smartlock, fromUtc, toUtc)
                bookingAccessCodesRepository.updateDates(accessCode.id, fromUtc, toUtc)
            } catch (e: Exception) {
                logger.error(e) { "Error updating access code ${accessCode.id} on smartlock ${smartlock.name} (${smartlock.vendor})" }
            }
        }
    }

    suspend fun createAccessCodes(
        property: Property,
        booking: Booking,
        smartLocks: List<PropertySmartlock>,
        fromUtc: LocalDateTime,
        toUtc: LocalDateTime,
    ): CreateAccessCodesResult {
        val guestFirstName = booking.guestFirstName?.trim()
        val guestLastName = booking.guestLastName?.trim()
        val firstName =
            when {
                !guestFirstName.isNullOrEmpty() && !guestLastName.isNullOrEmpty() ->
                    "$guestFirstName ${guestLastName.first()}."
                !guestFirstName.isNullOrEmpty() -> guestFirstName
                else -> "Huesped"
            }
        val lastName = "#${booking.channelId}"

        // Generate unique access code for this property
        val accessCode = bookingAccessCodesRepository.generateUniqueAccessCode(booking.propertyId)

        // Create access codes for each smartlock, tracking successes and failed lock names
        val successes = mutableListOf<BookingAccessCode>()
        val failedSmartLocks = mutableListOf<PropertySmartlock>()

        for (smartlock in smartLocks) {
            try {
                val plugin =
                    if (smartlock.vendor == SmartlockVendor.Nuki && smartlock.nukiSmartlock != null) {
                        pluginFactory.getNukiPluginByIntegrationId(smartlock.nukiSmartlock.nukiIntegrationId)
                    } else {
                        pluginFactory.getSmartLockPlugin(property.accountId, smartlock.vendor)
                    }
                val vendorCodeId =
                    plugin.createAccessCode(
                        guestFirstName = firstName,
                        guestLastName = lastName,
                        startTimeUTC = fromUtc,
                        endTimeUTC = toUtc,
                        pinCode = accessCode,
                        smartLock = smartlock,
                    )

                val code =
                    bookingAccessCodesRepository.create(
                        bookingId = booking.id,
                        smartlockId = smartlock.id,
                        accessCode = accessCode,
                        codeStartDatetime = fromUtc,
                        codeEndDatetime = toUtc,
                        vendorCodeId = vendorCodeId,
                    )
                if (code != null) successes.add(code)
            } catch (e: Exception) {
                logger.error(e) { "Error creating access code for smartlock ${smartlock.name} (${smartlock.vendor})" }
                failedSmartLocks.add(smartlock)
            }
        }

        return CreateAccessCodesResult(
            accessCode = accessCode,
            accessCodes = successes,
            failedSmartlocks = failedSmartLocks,
        )
    }
}
