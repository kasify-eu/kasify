package eu.kasify.model.api.responses

import eu.kasify.model.api.BookingAccessCode
import eu.kasify.model.api.SmartLockInfo
import kotlinx.serialization.Serializable

@Serializable
data class KeysResponse(
    val accessCode: String,
    val accessCodes: List<BookingAccessCode>,
    val failedSmartlocks: List<SmartLockInfo>,
)
