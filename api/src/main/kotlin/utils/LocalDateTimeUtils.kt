package eu.kasify.utils

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

val utcZone: ZoneId = ZoneId.of("UTC")

fun createLocalTimeUtc(
    date: LocalDate,
    time: LocalTime,
    atZone: String,
): LocalDateTime {
    val zone = ZoneId.of(atZone)
    val localDateTime = LocalDateTime.of(date, time)

    return localDateTime.atZone(zone).withZoneSameInstant(utcZone).toLocalDateTime()
}
