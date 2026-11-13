package eu.kasify.plugins

import eu.kasify.model.BookingAccessCode
import eu.kasify.model.PropertySmartlock
import java.time.LocalDateTime

interface SmartLockPlugin {
    suspend fun createAccessCode(
        guestFirstName: String,
        guestLastName: String,
        startTimeUTC: LocalDateTime,
        endTimeUTC: LocalDateTime,
        pinCode: String,
        smartLock: PropertySmartlock,
    ): String?

    suspend fun deleteAccessCode(
        accessCode: BookingAccessCode,
        smartLock: PropertySmartlock,
    )

    suspend fun updateAccessCodeDates(
        accessCode: BookingAccessCode,
        smartLock: PropertySmartlock,
        from: LocalDateTime,
        to: LocalDateTime,
    )
}
