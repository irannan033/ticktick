package com.ppm.momentum.domain

enum class Priority(val label: String) {
    HIGH("High"), MEDIUM("Medium"), LOW("Low"), NONE("None")
}

/** OVERDUE is derived at read time and never persisted. */
enum class TaskStatus(val label: String) {
    UPCOMING("Upcoming"), IN_PROGRESS("In progress"), COMPLETED("Completed"),
    OVERDUE("Overdue"), CANCELLED("Cancelled")
}

enum class Quadrant(val title: String, val subtitle: String) {
    DO_FIRST("Do first", "Important · Urgent"),
    SCHEDULE("Schedule", "Important · Not urgent"),
    DELEGATE("Delegate", "Not important · Urgent"),
    ELIMINATE("Eliminate", "Not important · Not urgent");

    val important get() = this == DO_FIRST || this == SCHEDULE
    val urgent get() = this == DO_FIRST || this == DELEGATE
}

fun quadrantOf(important: Boolean, urgent: Boolean): Quadrant = when {
    important && urgent -> Quadrant.DO_FIRST
    important -> Quadrant.SCHEDULE
    urgent -> Quadrant.DELEGATE
    else -> Quadrant.ELIMINATE
}

enum class Timeliness { ON_TIME, LATE, NOT_APPLICABLE }

enum class PomodoroStatus { COMPLETED, ABANDONED }

enum class Recurrence(val label: String) { DAILY("Daily"), WEEKLY("Weekly"), MONTHLY("Monthly") }
