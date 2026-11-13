package eu.kasify.plugins.nuki.model

import eu.kasify.serialization.JavaLocalDateTimeSerializer
import kotlinx.serialization.Serializable
import java.time.LocalDateTime

@Serializable
data class CreateVisitorCodeRequest(
    val remoteAllowed: Boolean = true,
    val name: String,
    val code: String,
    val allowedFromTime: Int = 0,
    val allowedUntilTime: Int = 0,
    @Serializable(with = JavaLocalDateTimeSerializer::class)
    val allowedFromDate: LocalDateTime,
    @Serializable(with = JavaLocalDateTimeSerializer::class)
    val allowedUntilDate: LocalDateTime,
    val type: Int = 13, // 13 = visitor code
    val allowedWeekDays: Int = 127, // 127 = all days
)
