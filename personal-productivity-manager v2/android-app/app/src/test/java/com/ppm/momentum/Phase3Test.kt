package com.ppm.momentum

import com.ppm.momentum.domain.Priority
import com.ppm.momentum.domain.Recurrence
import com.ppm.momentum.domain.nextOccurrence
import com.ppm.momentum.domain.parseQuickAdd
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class Phase3Test {
    private val zone = ZoneId.of("Asia/Dhaka")
    private fun ms(y: Int, mo: Int, d: Int, h: Int, mi: Int = 0) = LocalDateTime.of(y, mo, d, h, mi).atZone(zone).toInstant().toEpochMilli()
    private val now = ms(2026, 11, 4, 10) // Wednesday 10:00

    @Test fun tomorrowWithTime() {
        val q = parseQuickAdd("Call Sam tomorrow 5pm", now, zone)
        assertEquals("Call Sam", q.title)
        assertEquals(ms(2026, 11, 5, 17), q.dueAt)
    }

    @Test fun flagsPriorityAndRecurrenceAreExtracted() {
        val q = parseQuickAdd("Pay rent !high #urgent #important every month", now, zone)
        assertEquals("Pay rent", q.title)
        assertEquals(Priority.HIGH, q.priority)
        assertEquals(true, q.important); assertEquals(true, q.urgent)
        assertEquals(Recurrence.MONTHLY, q.recurrence)
        assertNull(q.dueAt)
    }

    @Test fun weekdayAndTwentyFourHour() {
        val q = parseQuickAdd("Report monday 17:30", now, zone)
        assertEquals("Report", q.title)
        assertEquals(ms(2026, 11, 9, 17, 30), q.dueAt)
    }

    @Test fun relativeTimes() {
        assertEquals(ms(2026, 11, 4, 12), parseQuickAdd("stretch in 2 hours", now, zone).dueAt)
        assertEquals(ms(2026, 11, 7, 10), parseQuickAdd("renew in 3 days", now, zone).dueAt)
    }

    @Test fun bareTimeInThePastRollsToTomorrow() {
        assertEquals(ms(2026, 11, 5, 9), parseQuickAdd("gym at 9am", now, zone).dueAt)
        assertEquals(ms(2026, 11, 4, 18), parseQuickAdd("gym 6pm", now, zone).dueAt)
    }

    @Test fun midnightNoonAndTonight() {
        assertEquals(ms(2026, 11, 5, 0), parseQuickAdd("x tomorrow 12am", now, zone).dueAt)
        assertEquals(ms(2026, 11, 5, 12), parseQuickAdd("x tomorrow 12pm", now, zone).dueAt)
        assertEquals(ms(2026, 11, 4, 20), parseQuickAdd("dinner tonight", now, zone).dueAt)
    }

    @Test fun plainTextStaysUntouched() {
        val q = parseQuickAdd("Buy milk", now, zone)
        assertEquals("Buy milk", q.title); assertNull(q.dueAt); assertNull(q.recurrence)
    }

    @Test fun recurrenceCalculation() {
        assertEquals(ms(2026, 2, 28, 9), nextOccurrence(ms(2026, 1, 31, 9), Recurrence.MONTHLY, zone))
        assertEquals(ms(2027, 1, 15, 9), nextOccurrence(ms(2026, 12, 15, 9), Recurrence.MONTHLY, zone))
        assertEquals(ms(2026, 11, 11, 9), nextOccurrence(ms(2026, 11, 4, 9), Recurrence.WEEKLY, zone))
        assertEquals(ms(2026, 12, 1, 9), nextOccurrence(ms(2026, 11, 30, 9), Recurrence.DAILY, zone))
    }
}
