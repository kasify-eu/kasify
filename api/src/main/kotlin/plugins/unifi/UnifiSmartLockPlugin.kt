package eu.kasify.plugins.unifi

import eu.kasify.model.BookingAccessCode
import eu.kasify.model.PropertySmartlock
import eu.kasify.plugins.SmartLockPlugin
import eu.kasify.services.UnifiSessionManager
import java.time.LocalDateTime
import kotlin.uuid.Uuid

/**
 * Wrapper on top of the Unifi plugin to create access codes with session management.
 *
 * Uses UnifiSessionManager for automatic session caching and retry on auth failure.
 */
class UnifiSmartLockPlugin(
    private val accountId: Uuid,
    private val sessionManager: UnifiSessionManager,
) : SmartLockPlugin {
    override suspend fun createAccessCode(
        guestFirstName: String,
        guestLastName: String,
        startTimeUTC: LocalDateTime,
        endTimeUTC: LocalDateTime,
        pinCode: String,
        smartLock: PropertySmartlock,
    ): String? {
        if (smartLock.unifiSmartlock == null) return null

        return sessionManager.executeWithRetry(accountId) { plugin ->
            val visitor =
                plugin.createVisitorSimplified(
                    firstName = guestFirstName,
                    lastName = guestLastName,
                    startTimeUTC = startTimeUTC,
                    endTimeUTC = endTimeUTC,
                    floorId = smartLock.unifiSmartlock.floorId,
                    doorId = smartLock.unifiSmartlock.doorId,
                    pinCode = pinCode,
                )
            visitor.uniqueId
        }
    }

    override suspend fun deleteAccessCode(
        accessCode: BookingAccessCode,
        smartLock: PropertySmartlock,
    ) {
        accessCode.vendorCodeId?.let { vendorCodeId ->
            sessionManager.executeWithRetry(accountId) { plugin ->
                plugin.deleteVisitor(vendorCodeId)
            }
        }
    }

    override suspend fun updateAccessCodeDates(
        accessCode: BookingAccessCode,
        smartLock: PropertySmartlock,
        from: LocalDateTime,
        to: LocalDateTime,
    ) {
        accessCode.vendorCodeId?.let { vendorCodeId ->
            sessionManager.executeWithRetry(accountId) { plugin ->
                plugin.updateVisitor(vendorCodeId, from, to)
            }
        }
    }
}
