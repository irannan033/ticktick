package com.ppm.momentum.domain

import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** Next due time for a recurring task, keeping the local wall-clock time. Month-end clamps (31 Jan -> 28 Feb). */
fun nextOccurrence(dueMs: Long, r: Recurrence, zone: ZoneId): Long {
    val d = ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(dueMs), zone)
    val n = when (r) {
        Recurrence.DAILY -> d.plusDays(1)
        Recurrence.WEEKLY -> d.plusWeeks(1)
        Recurrence.MONTHLY -> d.plusMonths(1)
    }
    return n.toInstant().toEpochMilli()
}

data class QuickTask(
    val title: String,
    val dueAt: Long? = null,
    val priority: Priority = Priority.NONE,
    val important: Boolean = false,
    val urgent: Boolean = false,
    val recurrence: Recurrence? = null,
)

private val WEEKDAYS = DayOfWeek.values().associateBy { it.name.lowercase() }
private val REC = Regex("""\b(?:every\s+(day|week|month)|(daily|weekly|monthly))\b""", RegexOption.IGNORE_CASE)
private val PRIO = Regex("""(?:^|\s)!(high|h|medium|med|m|low|l)\b""", RegexOption.IGNORE_CASE)
private val FLAG = Regex("""(?:^|\s)#(important|urgent)\b""", RegexOption.IGNORE_CASE)
private val IN = Regex("""\bin\s+(\d+)\s*(minutes?|mins?|hours?|hrs?|days?)\b""", RegexOption.IGNORE_CASE)
private val DAY = Regex("""\b(today|tonight|tomorrow|monday|tuesday|wednesday|thursday|friday|saturday|sunday)\b""", RegexOption.IGNORE_CASE)
private val TIME12 = Regex("""\b(?:at\s+)?(\d{1,2})(?::(\d{2}))?\s*(am|pm)\b""", RegexOption.IGNORE_CASE)
private val TIME24 = Regex("""\b(?:at\s+)?([01]?\d|2[0-3]):([0-5]\d)\b""")
private val TIMEAT = Regex("""\bat\s+(\d{1,2})\b""", RegexOption.IGNORE_CASE)

/**
 * Natural-language quick add, e.g. "Call Sam tomorrow 5pm !high #urgent every week".
 * Deterministic and offline. Whatever is not recognised stays in the title.
 */
fun parseQuickAdd(input: String, now: Long, zone: ZoneId = ZoneId.systemDefault()): QuickTask {
    var s = " " + input.trim()
    var priority = Priority.NONE
    var important = false
    var urgent = false
    var rec: Recurrence? = null

    PRIO.find(s)?.let {
        priority = when (it.groupValues[1].lowercase().first()) { 'h' -> Priority.HIGH; 'm' -> Priority.MEDIUM; else -> Priority.LOW }
        s = s.removeRange(it.range)
    }
    while (true) {
        val m = FLAG.find(s) ?: break
        if (m.groupValues[1].equals("important", true)) important = true else urgent = true
        s = s.removeRange(m.range)
    }
    REC.find(s)?.let {
        val w = (it.groupValues[1].ifEmpty { it.groupValues[2] }).lowercase()
        rec = when (w) { "day", "daily" -> Recurrence.DAILY; "week", "weekly" -> Recurrence.WEEKLY; else -> Recurrence.MONTHLY }
        s = s.removeRange(it.range)
    }

    val base = ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(now), zone)
    var due: LocalDateTime? = null

    val mIn = IN.find(s)
    if (mIn != null) {
        val n = mIn.groupValues[1].toLong()
        val u = mIn.groupValues[2].lowercase()
        due = base.toLocalDateTime().let { b -> if (u.startsWith("m")) b.plusMinutes(n) else if (u.startsWith("h")) b.plusHours(n) else b.plusDays(n) }
        s = s.removeRange(mIn.range)
    } else {
        var day: LocalDateTime? = null
        var tonight = false
        DAY.find(s)?.let {
            val w = it.groupValues[1].lowercase()
            val today = base.toLocalDate().atStartOfDay()
            day = when (w) {
                "today" -> today
                "tonight" -> { tonight = true; today }
                "tomorrow" -> today.plusDays(1)
                else -> {
                    val target = WEEKDAYS.getValue(w)
                    var ahead = (target.value - base.dayOfWeek.value + 7) % 7
                    if (ahead == 0) ahead = 7
                    today.plusDays(ahead.toLong())
                }
            }
            s = s.removeRange(it.range)
        }
        var hh: Int? = null
        var mm = 0
        val t12 = TIME12.find(s)
        val t24 = if (t12 == null) TIME24.find(s) else null
        val tAt = if (t12 == null && t24 == null) TIMEAT.find(s) else null
        when {
            t12 != null -> {
                var h = t12.groupValues[1].toInt()
                mm = t12.groupValues[2].ifEmpty { "0" }.toInt()
                if (h in 1..12 && mm in 0..59) {
                    if (h == 12) h = 0
                    hh = h + if (t12.groupValues[3].equals("pm", true)) 12 else 0
                    s = s.removeRange(t12.range)
                }
            }
            t24 != null -> { hh = t24.groupValues[1].toInt(); mm = t24.groupValues[2].toInt(); s = s.removeRange(t24.range) }
            tAt != null -> {
                val h = tAt.groupValues[1].toInt()
                if (h in 0..23) { hh = h; mm = 0; s = s.removeRange(tAt.range) }
            }
        }
        val d = day
        if (d != null) {
            val h = hh ?: if (tonight) 20 else 9
            due = d.withHour(h).withMinute(mm)
        } else if (hh != null) {
            var t = base.toLocalDate().atStartOfDay().withHour(hh).withMinute(mm)
            if (!t.atZone(zone).toInstant().isAfter(java.time.Instant.ofEpochMilli(now))) t = t.plusDays(1)
            due = t
        }
    }

    val title = s.replace(Regex("""\s+"""), " ").trim(' ', ',', '.', '-')
    return QuickTask(
        title = title.ifEmpty { input.trim() }, dueAt = due?.atZone(zone)?.toInstant()?.toEpochMilli(),
        priority = priority, important = important, urgent = urgent, recurrence = rec,
    )
}
