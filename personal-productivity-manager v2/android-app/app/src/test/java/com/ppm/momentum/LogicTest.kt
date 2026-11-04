package com.ppm.momentum

import com.ppm.momentum.data.TaskEntity
import com.ppm.momentum.domain.Period
import com.ppm.momentum.domain.Priority
import com.ppm.momentum.domain.Quadrant
import com.ppm.momentum.domain.TaskFilter
import com.ppm.momentum.domain.TaskStatus
import com.ppm.momentum.domain.Timeliness
import com.ppm.momentum.domain.computeStats
import com.ppm.momentum.domain.effectiveStatus
import com.ppm.momentum.domain.quadrantOf
import com.ppm.momentum.domain.timeliness
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LogicTest {
    private val now = 1_000_000_000_000L
    private val hour = 3_600_000L

    private fun task(
        status: TaskStatus = TaskStatus.UPCOMING, due: Long? = null, completed: Long? = null,
        priority: Priority = Priority.NONE, created: Long = now - 24 * hour, postponed: Int = 0,
    ) = TaskEntity(
        title = "t", status = status, dueAt = due, completedAt = completed, priority = priority,
        postponeCount = postponed, createdAt = created, updatedAt = created,
    )

    @Test fun quadrantsFollowImportanceAndUrgency() {
        assertEquals(Quadrant.DO_FIRST, quadrantOf(true, true))
        assertEquals(Quadrant.SCHEDULE, quadrantOf(true, false))
        assertEquals(Quadrant.DELEGATE, quadrantOf(false, true))
        assertEquals(Quadrant.ELIMINATE, quadrantOf(false, false))
    }

    @Test fun priorityDoesNotAffectQuadrant() {
        assertEquals(Quadrant.ELIMINATE, quadrantOf(false, false))
        assertEquals(Priority.HIGH, task(priority = Priority.HIGH).priority)
    }

    @Test fun openTaskPastDeadlineIsOverdue() {
        assertEquals(TaskStatus.OVERDUE, task(due = now - hour).effectiveStatus(now))
        assertEquals(TaskStatus.UPCOMING, task(due = now + hour).effectiveStatus(now))
    }

    @Test fun completedOrCancelledNeverOverdue() {
        assertEquals(TaskStatus.COMPLETED, task(TaskStatus.COMPLETED, due = now - hour, completed = now).effectiveStatus(now))
        assertEquals(TaskStatus.CANCELLED, task(TaskStatus.CANCELLED, due = now - hour).effectiveStatus(now))
    }

    @Test fun timelinessComparesCompletionWithDeadline() {
        assertEquals(Timeliness.ON_TIME, task(TaskStatus.COMPLETED, due = now, completed = now - hour).timeliness())
        assertEquals(Timeliness.LATE, task(TaskStatus.COMPLETED, due = now - hour, completed = now).timeliness())
        assertEquals(Timeliness.ON_TIME, task(TaskStatus.COMPLETED, due = null, completed = now).timeliness())
        assertEquals(Timeliness.NOT_APPLICABLE, task().timeliness())
    }

    @Test fun statsAreComputedFromRecords() {
        val tasks = listOf(
            task(TaskStatus.COMPLETED, due = now, completed = now - hour),
            task(TaskStatus.COMPLETED, due = now - 2 * hour, completed = now),
            task(due = now - hour),
            task(due = now + hour),
            task(TaskStatus.CANCELLED),
        )
        val s = computeStats(tasks, now, null)
        assertEquals(5, s.total)
        assertEquals(2, s.completed)
        assertEquals(1, s.onTime)
        assertEquals(1, s.late)
        assertEquals(1, s.overdue)
        assertEquals(1, s.pending)
        assertEquals(1, s.cancelled)
        assertEquals(40.0, s.completionRate, 0.001)
    }

    @Test fun emptyStatsHaveNoAverage() {
        val s = computeStats(emptyList(), now, null)
        assertEquals(0.0, s.completionRate, 0.0)
        assertNull(s.avgCompletionHours)
    }

    @Test fun filterMatchesPriorityAndEffectiveStatus() {
        val t = task(due = now - hour, priority = Priority.HIGH)
        assertEquals(true, TaskFilter(priority = Priority.HIGH, status = TaskStatus.OVERDUE).matches(t, now))
        assertEquals(false, TaskFilter(priority = Priority.LOW).matches(t, now))
    }

    @Test fun allTimePeriodHasNoRange() {
        assertNull(Period.ALL.range(java.time.LocalDate.of(2026, 11, 1)))
    }
}
