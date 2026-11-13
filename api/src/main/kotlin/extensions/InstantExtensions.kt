package eu.kasify.extensions

import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

fun LocalDateTime.formatLocalDateTime(timeZone: String = "Europe/Madrid"): String {
    val zone = ZoneId.of(timeZone)

    val zonedDateTime =
        this
            .atZone(ZoneId.systemDefault())
            .withZoneSameInstant(zone)

    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
    return zonedDateTime.format(formatter)
}

fun LocalDateTime.formatLocalDate(timeZone: String = "Europe/Madrid"): String {
    val zone = ZoneId.of(timeZone)

    val zonedDateTime =
        this
            .atZone(ZoneId.systemDefault())
            .withZoneSameInstant(zone)

    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    return zonedDateTime.format(formatter)
}
