package com.ppm.momentum.util

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

val zone: ZoneId get() = ZoneId.systemDefault()

fun Long.toDateTime(): LocalDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(this), zone)
fun Long.toDate(): LocalDate = toDateTime().toLocalDate()
fun LocalDate.startMillis(): Long = atStartOfDay(zone).toInstant().toEpochMilli()
fun LocalDate.endMillis(): Long = plusDays(1).startMillis()
fun LocalDateTime.toMillis(): Long = atZone(zone).toInstant().toEpochMilli()

private fun fmt(p: String): DateTimeFormatter = DateTimeFormatter.ofPattern(p, Locale.getDefault())

fun formatTime(ms: Long): String = ms.toDateTime().format(fmt("HH:mm"))
fun formatDate(ms: Long): String = ms.toDateTime().format(fmt("EEE, d MMM yyyy"))
fun formatDateTime(ms: Long): String = formatDate(ms) + " · " + formatTime(ms)
fun LocalDate.format(pattern: String): String = format(fmt(pattern))

fun friendlyDue(ms: Long, now: Long): String {
    val d = ms.toDate()
    val today = now.toDate()
    val label = when (d) {
        today -> "Today"
        today.plusDays(1) -> "Tomorrow"
        today.minusDays(1) -> "Yesterday"
        else -> d.format("d MMM")
    }
    return label + ", " + formatTime(ms)
}

fun formatMinutes(min: Int): String = when {
    min < 60 -> "${min}m"
    min % 60 == 0 -> "${min / 60}h"
    else -> "${min / 60}h ${min % 60}m"
}

/** Default due time when adding a task from a calendar day. */
fun defaultDueFor(date: LocalDate, now: Long): Long =
    if (date == now.toDate()) now.toDateTime().plusHours(1).withMinute(0).withSecond(0).withNano(0).toMillis()
    else date.atTime(9, 0).toMillis()
