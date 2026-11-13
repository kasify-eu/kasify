package eu.kasify.extensions

import java.time.LocalDate
import java.time.ZoneId

fun today(zoneId: String = "Europe/Madrid"): LocalDate {
    val zone = ZoneId.of(zoneId)
    return LocalDate.now(zone)
}

fun tomorrow(zoneId: String = "Europe/Madrid"): LocalDate {
    val zone = ZoneId.of(zoneId)
    return LocalDate.now(zone).plusDays(1)
}

fun yesterday(zoneId: String = "Europe/Madrid"): LocalDate {
    val zone = ZoneId.of(zoneId)
    return LocalDate.now(zone).minusDays(1)
}

fun LocalDate.addDays(days: Int): LocalDate = this.plusDays(days.toLong())
