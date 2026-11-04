package com.ppm.momentum

import com.ppm.momentum.data.PomodoroEntity
import com.ppm.momentum.data.TaskEntity
import com.ppm.momentum.domain.PomodoroStatus
import com.ppm.momentum.domain.TaskStatus
import com.ppm.momentum.domain.dailyCompletions
import com.ppm.momentum.domain.focusSeconds
import com.ppm.momentum.domain.monthlyCompletions
import com.ppm.momentum.domain.weeklyCompletions
import com.ppm.momentum.domain.weeklyOverdue
import com.ppm.momentum.export.EXPORT_HEADER
import com.ppm.momentum.export.csvEscape
import com.ppm.momentum.export.exportRows
import com.ppm.momentum.export.writeCsv
import com.ppm.momentum.util.startMillis
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.time.LocalDate

class Phase2Test {
    private val today = LocalDate.of(2026, 11, 4) // Wednesday
    private val hour = 3_600_000L

    private fun done(on: LocalDate, due: Long? = null) = TaskEntity(
        title = "d", status = TaskStatus.COMPLETED, dueAt = due,
        completedAt = on.startMillis() + 10 * hour, createdAt = 0, updatedAt = 0,
    )

    @Test fun dailySeriesCountsByCompletionDay() {
        val tasks = listOf(done(today), done(today), done(today.minusDays(1)), done(today.minusDays(10)))
        val s = dailyCompletions(tasks, today, 7)
        assertEquals(7, s.size)
        assertEquals(2, s.last().value)
        assertEquals(1, s[5].value)
        assertEquals(3, s.sumOf { it.value })
    }

    @Test fun weeklySeriesGroupsMondayToSunday() {
        val tasks = listOf(done(today), done(LocalDate.of(2026, 11, 2)), done(LocalDate.of(2026, 10, 30)))
        val s = weeklyCompletions(tasks, today, 3)
        assertEquals(listOf(0, 1, 2), s.map { it.value })
    }

    @Test fun monthlySeriesGroupsByMonth() {
        val tasks = listOf(done(today), done(LocalDate.of(2026, 10, 15)), done(LocalDate.of(2026, 10, 1)))
        assertEquals(listOf(2, 1), monthlyCompletions(tasks, today, 2).map { it.value })
    }

    @Test fun overdueSeriesCountsLateAndStillOpen() {
        val due = today.startMillis() + 8 * hour
        val late = done(today.plusDays(1), due = due)          // finished after deadline
        val onTime = done(today, due = due + 20 * hour)        // finished before deadline
        val open = TaskEntity(title = "o", dueAt = due, createdAt = 0, updatedAt = 0)
        val now = today.startMillis() + 20 * hour
        val s = weeklyOverdue(listOf(late, onTime, open), now, today, 1)
        assertEquals(2, s.last().value)
    }

    @Test fun focusTimeSumsSessionsInRange() {
        val a = PomodoroEntity(taskId = null, plannedMinutes = 25, completedSeconds = 1500, startedAt = 1000, endedAt = 2000, status = PomodoroStatus.COMPLETED)
        val b = PomodoroEntity(taskId = null, plannedMinutes = 25, completedSeconds = 600, startedAt = 5000, endedAt = 6000, status = PomodoroStatus.ABANDONED)
        assertEquals(2100L, focusSeconds(listOf(a, b), null))
        assertEquals(600L, focusSeconds(listOf(a, b), 4000L..9000L))
    }

    @Test fun csvQuotesSpecialCharacters() {
        assertEquals("plain", csvEscape("plain"))
        assertEquals("\"a,b\"", csvEscape("a,b"))
        assertEquals("\"say \"\"hi\"\"\"", csvEscape("say \"hi\""))
    }

    @Test fun exportRowsMatchHeaderWidthAndLinkPomodoro() {
        val t = TaskEntity(title = "Write, report", status = TaskStatus.COMPLETED, completedAt = 10, dueAt = 100, createdAt = 0, updatedAt = 0)
        val ses = PomodoroEntity(taskId = t.id, plannedMinutes = 25, completedSeconds = 1500, startedAt = 1, endedAt = 2, status = PomodoroStatus.COMPLETED)
        val rows = exportRows(listOf(t), emptyList(), listOf(ses), 1000)
        assertEquals(EXPORT_HEADER.size, rows[0].size)
        assertEquals("On time", rows[0][11])
        assertEquals("1", rows[0][13])
        assertEquals("25", rows[0][14])
        val out = ByteArrayOutputStream()
        writeCsv(out, listOf(EXPORT_HEADER) + rows)
        assertEquals(true, out.toString("UTF-8").contains("\"Write, report\""))
    }
}
