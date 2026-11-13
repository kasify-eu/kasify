package eu.kasify.plugins.nuki

import eu.kasify.model.BookingAccessCode
import eu.kasify.model.PropertySmartlock
import eu.kasify.plugins.SmartLockPlugin
import io.github.oshai.kotlinlogging.KotlinLogging
import java.time.LocalDateTime

open class NukiSmartLockPlugin(
    apiToken: String,
) : SmartLockPlugin {
    val nukiPlugin = NukiPlugin(apiToken)
    private val logger = KotlinLogging.logger {}

    open override suspend fun createAccessCode(
        guestFirstName: String,
        guestLastName: String,
        startTimeUTC: LocalDateTime,
        endTimeUTC: LocalDateTime,
        pinCode: String,
        smartLock: PropertySmartlock,
    ): String? {
        nukiPlugin.createAccessCode(
            name = "$guestFirstName $guestLastName",
            code = pinCode,
            from = startTimeUTC,
            to = endTimeUTC,
            smartLockId = smartLock.nukiSmartlock!!.nukiSmartlockId,
        )
        return null
    }

    open override suspend fun deleteAccessCode(
        accessCode: BookingAccessCode,
        smartLock: PropertySmartlock,
    ) {
        if (smartLock.nukiSmartlock == null) return
        val nukiSmartLock = smartLock.nukiSmartlock.nukiSmartlockId

        val codes = nukiPlugin.getAccessCodes(nukiSmartLock)
        val codeToDelete = codes.firstOrNull { code -> code.code?.toString() == accessCode.accessCode }
        if (codeToDelete == null) {
            logger.warn { "Access code ${accessCode.id} not found on Nuki Web for property ${smartLock.propertyId}, smartlock ${smartLock.id} — it may have already been deleted. Skipping deletion." }
        } else {
            nukiPlugin.deleteAccessCode(codeToDelete.id)
        }
    }

    open override suspend fun updateAccessCodeDates(
        accessCode: BookingAccessCode,
        smartLock: PropertySmartlock,
        from: LocalDateTime,
        to: LocalDateTime,
    ) {
        if (smartLock.nukiSmartlock == null) return
        val nukiSmartLock = smartLock.nukiSmartlock.nukiSmartlockId

        val codes = nukiPlugin.getAccessCodes(nukiSmartLock)
        val codeToUpdate = codes.firstOrNull { code -> code.code?.toString() == accessCode.accessCode }
        if (codeToUpdate == null) {
            logger.warn { "Access code ${accessCode.id} not found on Nuki Web for property ${smartLock.propertyId}, smartlock ${smartLock.id} — skipping date update." }
        } else {
            nukiPlugin.updateAccessCode(
                smartLockId = nukiSmartLock,
                authId = codeToUpdate.id,
                from = from,
                to = to,
            )
        }
    }
}
