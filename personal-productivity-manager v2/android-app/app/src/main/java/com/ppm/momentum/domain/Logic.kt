package com.ppm.momentum.domain

import com.ppm.momentum.data.TaskEntity
import com.ppm.momentum.data.PomodoroEntity
import com.ppm.momentum.util.endMillis
import com.ppm.momentum.util.format
import com.ppm.momentum.util.startMillis
import com.ppm.momentum.util.toDate
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

val TaskEntity.isActive: Boolean
    get() = status == TaskStatus.UPCOMING || status == TaskStatus.IN_PROGRESS

fun TaskEntity.isOverdue(now: Long): Boolean {
    val due = dueAt ?: return false
    return isActive && due < now
}

/** Status shown to the user: stored status, with automatic OVERDUE detection. */
fun TaskEntity.effectiveStatus(now: Long): TaskStatus =
    if (isOverdue(now)) TaskStatus.OVERDUE else status

/** Compares the recorded completion timestamp with the deadline. */
fun TaskEntity.timeliness(): Timeliness {
    val done = completedAt
    if (status != TaskStatus.COMPLETED || done == null) return Timeliness.NOT_APPLICABLE
    val due = dueAt ?: return Timeliness.ON_TIME
    return if (done <= due) Timeliness.ON_TIME else Timeliness.LATE
}

val TaskEntity.quadrant: Quadrant get() = quadrantOf(important, urgent)

fun TaskEntity.scheduleDate(): LocalDate? = (dueAt ?: startAt)?.toDate()

data class TaskFilter(
    val priority: Priority? = null,
    val categoryId: String? = null,
    val status: TaskStatus? = null,
) {
    val isEmpty get() = priority == null && categoryId == null && status == null
    fun matches(t: TaskEntity, now: Long): Boolean =
        (priority == null || t.priority == priority) &&
            (categoryId == null || t.categoryId == categoryId) &&
            (status == null || t.effectiveStatus(now) == status)
}

enum class Period(val label: String) {
    TODAY("Today"), WEEK("This week"), MONTH("This month"), YEAR("This year"), ALL("All time"), CUSTOM("Custom");

    /** Half-open [start, end) range in epoch millis, or null for ALL. */
    fun range(today: LocalDate): LongRange? = when (this) {
        TODAY -> today.startMillis() until today.endMillis()
        WEEK -> {
            val s = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            s.startMillis() until s.plusDays(7).startMillis()
        }
        MONTH -> {
            val s = today.withDayOfMonth(1)
            s.startMillis() until s.plusMonths(1).startMillis()
        }
        YEAR -> {
            val s = today.withDayOfYear(1)
            s.startMillis() until s.plusYears(1).startMillis()
        }
        ALL, CUSTOM -> null
    }
}

data class TaskStats(
    val total: Int,
    val completed: Int,
    val onTime: Int,
    val late: Int,
    val overdue: Int,
    val pending: Int,
    val cancelled: Int,
    val avgCompletionHours: Double?,
    val postponements: Int,
    val postponedTasks: Int,
) {
    val incomplete get() = overdue + pending
    val completionRate: Double get() = if (total == 0) 0.0 else completed * 100.0 / total
}

/** A task belongs to a period by its deadline (or creation date when it has none). */
fun TaskEntity.referenceTime(): Long = dueAt ?: createdAt

fun computeStats(all: List<TaskEntity>, now: Long, range: LongRange?): TaskStats {
    val tasks = if (range == null) all else all.filter { it.referenceTime() in range }
    val done = tasks.filter { it.status == TaskStatus.COMPLETED }
    val hours = done.mapNotNull { t -> t.completedAt?.let { (it - t.createdAt) / 3_600_000.0 } }
    return TaskStats(
        total = tasks.size,
        completed = done.size,
        onTime = done.count { it.timeliness() == Timeliness.ON_TIME },
        late = done.count { it.timeliness() == Timeliness.LATE },
        overdue = tasks.count { it.isOverdue(now) },
        pending = tasks.count { it.isActive && !it.isOverdue(now) },
        cancelled = tasks.count { it.status == TaskStatus.CANCELLED },
        avgCompletionHours = if (hours.isEmpty()) null else hours.average(),
        postponements = tasks.sumOf { it.postponeCount },
        postponedTasks = tasks.count { it.postponeCount > 0 },
    )
}


// ---- Chart series (all derived from real rows) -------------------------------------------

data class ChartPoint(val label: String, val value: Int)

private fun Long?.within(r: LongRange): Boolean = this != null && this in r

private fun TaskEntity.completedWithin(r: LongRange) = status == TaskStatus.COMPLETED && completedAt.within(r)

fun dailyCompletions(tasks: List<TaskEntity>, end: LocalDate, days: Int): List<ChartPoint> =
    (days - 1 downTo 0).map { off ->
        val d = end.minusDays(off.toLong())
        val r = d.startMillis() until d.endMillis()
        ChartPoint(d.format("d/M"), tasks.count { it.completedWithin(r) })
    }

private fun weekRange(start: LocalDate) = start.startMillis() until start.plusDays(7).startMillis()

fun weeklyCompletions(tasks: List<TaskEntity>, end: LocalDate, weeks: Int): List<ChartPoint> {
    val thisWeek = end.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    return (weeks - 1 downTo 0).map { off ->
        val s = thisWeek.minusWeeks(off.toLong())
        ChartPoint(s.format("d MMM"), tasks.count { it.completedWithin(weekRange(s)) })
    }
}

fun monthlyCompletions(tasks: List<TaskEntity>, end: LocalDate, months: Int): List<ChartPoint> =
    (months - 1 downTo 0).map { off ->
        val m = YearMonth.from(end).minusMonths(off.toLong())
        val r = m.atDay(1).startMillis() until m.plusMonths(1).atDay(1).startMillis()
        ChartPoint(m.atDay(1).format("MMM"), tasks.count { it.completedWithin(r) })
    }

/** Per week: tasks whose deadline fell in that week and that finished late or are still overdue. */
fun weeklyOverdue(tasks: List<TaskEntity>, now: Long, end: LocalDate, weeks: Int): List<ChartPoint> {
    val thisWeek = end.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    return (weeks - 1 downTo 0).map { off ->
        val s = thisWeek.minusWeeks(off.toLong())
        val r = weekRange(s)
        ChartPoint(s.format("d MMM"), tasks.count { it.dueAt.within(r) && (it.isOverdue(now) || it.timeliness() == Timeliness.LATE) })
    }
}

/** Focus seconds in [range] (null = all time). Partial (abandoned) sessions count their elapsed time. */
fun focusSeconds(sessions: List<PomodoroEntity>, range: LongRange?): Long =
    sessions.filter { range == null || it.startedAt in range }.sumOf { it.completedSeconds.toLong() }
