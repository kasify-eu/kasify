package eu.kasify.db

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.time.Instant

// Extensions removed in kotlinx-datetime 0.7.0

fun java.time.Instant.toKotlinInstant(): Instant = Instant.fromEpochSeconds(epochSecond, nano.toLong())

fun Instant.toJavaInstant(): java.time.Instant = java.time.Instant.ofEpochSecond(epochSeconds, nanosecondsOfSecond.toLong())

fun java.time.LocalDate.toKotlinLocalDate(): LocalDate = LocalDate(year, monthValue, dayOfMonth)

fun TimeZone.toJavaZoneId(): java.time.ZoneId = java.time.ZoneId.of(id)
