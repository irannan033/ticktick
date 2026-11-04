package com.ppm.momentum.data

import androidx.room.withTransaction
import com.ppm.momentum.domain.Timeliness
import com.ppm.momentum.domain.TaskStatus
import com.ppm.momentum.domain.nextOccurrence
import com.ppm.momentum.domain.timeliness
import com.ppm.momentum.util.formatDateTime
import kotlinx.coroutines.flow.first
import java.util.UUID

class TaskRepository(private val db: AppDatabase, private val scheduler: com.ppm.momentum.notify.ReminderScheduler) {
    private val tasks = db.taskDao()
    private val cats = db.categoryDao()
    private val acts = db.activityDao()
    private val pomodoro = db.pomodoroDao()

    val allTasks = tasks.observeAll()
    val categories = cats.observeAll()
    fun observeTask(id: String) = tasks.observe(id)
    fun observeActivity(id: String) = acts.observe(id)
    suspend fun getTask(id: String): TaskEntity? = tasks.get(id)

    val sessions = pomodoro.observeAll()
    fun observeSessions(taskId: String) = pomodoro.observeForTask(taskId)
    suspend fun recordSession(s: PomodoroEntity) = pomodoro.insert(s)

    private suspend fun resync(id: String) {
        val t = tasks.get(id)
        if (t == null) scheduler.cancel(id) else scheduler.sync(t)
    }

    /** Re-applies reminder alarms to every open task (after settings change or reboot). */
    suspend fun resyncAll() {
        allTasks.first().forEach { scheduler.sync(it) }
    }

    suspend fun seedDefaults() {
        if (cats.count() == 0) {
            // Fixed ids so two devices that both seed defaults end up with the same rows after sync.
            listOf(Triple("default-work", "Work", 0xFF4F5BD5), Triple("default-personal", "Personal", 0xFF0F9D8A), Triple("default-study", "Study", 0xFFF5A524))
                .forEach { (id, n, c) -> cats.upsert(CategoryEntity(id = id, name = n, colorArgb = c)) }
        }
    }

    private suspend fun log(taskId: String, action: String, prev: TaskStatus?, new: TaskStatus?, note: String? = null) {
        acts.insert(ActivityEntity(taskId = taskId, action = action, previousStatus = prev, newStatus = new, note = note, timestamp = System.currentTimeMillis()))
    }

    /** Creates or edits a task. Status/completion are never changed here, only via dedicated actions. */
    suspend fun save(task: TaskEntity): String {
        val now = System.currentTimeMillis()
        db.withTransaction {
            val old = tasks.get(task.id)
            if (old == null) {
                tasks.upsert(task.copy(createdAt = now, updatedAt = now))
                log(task.id, "Created", null, task.status)
            } else {
                var postponed = old.postponeCount
                val oldDue = old.dueAt
                val newDue = task.dueAt
                val dueChanged = oldDue != newDue
                if (dueChanged && oldDue != null && newDue != null && newDue > oldDue) postponed++
                tasks.upsert(
                    task.copy(
                        createdAt = old.createdAt, updatedAt = now, postponeCount = postponed,
                        status = old.status, completedAt = old.completedAt,
                    )
                )
                val note = if (dueChanged) "Deadline: " + (oldDue?.let { formatDateTime(it) } ?: "none") + " → " + (newDue?.let { formatDateTime(it) } ?: "none") else null
                log(task.id, "Edited", old.status, old.status, note)
            }
        }
        resync(task.id)
        return task.id
    }

    private suspend fun changeStatus(id: String, new: TaskStatus, action: String) {
        var nextId: String? = null
        db.withTransaction {
            val t = tasks.get(id)
            if (t != null && t.status != new) {
                val now = System.currentTimeMillis()
                val updated = t.copy(
                    status = new,
                    completedAt = if (new == TaskStatus.COMPLETED) now else null,
                    updatedAt = now,
                )
                tasks.upsert(updated)
                val due = t.dueAt
                val rec = t.recurrence
                if (new == TaskStatus.COMPLETED && rec != null && due != null) {
                    val delta = nextOccurrence(due, rec, java.time.ZoneId.systemDefault()) - due
                    val next = t.copy(
                        id = UUID.randomUUID().toString(), status = TaskStatus.UPCOMING, completedAt = null, postponeCount = 0,
                        startAt = t.startAt?.plus(delta), dueAt = due + delta, reminderAt = t.reminderAt?.plus(delta),
                        createdAt = now, updatedAt = now,
                    )
                    tasks.upsert(next)
                    log(next.id, "Created", null, next.status, "Next occurrence of a recurring task")
                    nextId = next.id
                }
                val note = when (updated.timeliness()) {
                    Timeliness.ON_TIME -> "Completed on time"
                    Timeliness.LATE -> "Completed late"
                    Timeliness.NOT_APPLICABLE -> null
                }
                log(id, action, t.status, new, note)
            }
        }
        resync(id)
        nextId?.let { resync(it) }
    }

    suspend fun complete(id: String) = changeStatus(id, TaskStatus.COMPLETED, "Completed")
    suspend fun reopen(id: String) = changeStatus(id, TaskStatus.UPCOMING, "Reopened")
    suspend fun cancel(id: String) = changeStatus(id, TaskStatus.CANCELLED, "Cancelled")
    suspend fun start(id: String) = changeStatus(id, TaskStatus.IN_PROGRESS, "Started")

    suspend fun reschedule(id: String, newDue: Long) {
        db.withTransaction {
            val t = tasks.get(id) ?: return@withTransaction
            val oldDue = t.dueAt
            val delta = if (oldDue != null) newDue - oldDue else 0L
            val postponed = if (oldDue != null && newDue > oldDue) t.postponeCount + 1 else t.postponeCount
            tasks.upsert(
                t.copy(
                    dueAt = newDue, postponeCount = postponed, updatedAt = System.currentTimeMillis(),
                    reminderAt = t.reminderAt?.plus(delta),
                )
            )
            log(id, "Rescheduled", t.status, t.status, (oldDue?.let { formatDateTime(it) } ?: "none") + " → " + formatDateTime(newDue))
        }
        resync(id)
    }

    suspend fun setMatrix(id: String, important: Boolean, urgent: Boolean) {
        db.withTransaction {
            val t = tasks.get(id) ?: return@withTransaction
            if (t.important == important && t.urgent == urgent) return@withTransaction
            tasks.upsert(t.copy(important = important, urgent = urgent, updatedAt = System.currentTimeMillis()))
            log(id, "Matrix updated", t.status, t.status,
                (if (important) "Important" else "Not important") + ", " + (if (urgent) "urgent" else "not urgent"))
        }
    }

    suspend fun duplicate(id: String): String? {
        var newId: String? = null
        db.withTransaction {
            val t = tasks.get(id) ?: return@withTransaction
            val now = System.currentTimeMillis()
            val copy = t.copy(
                id = UUID.randomUUID().toString(), title = t.title + " (copy)",
                status = TaskStatus.UPCOMING, completedAt = null, postponeCount = 0,
                createdAt = now, updatedAt = now,
            )
            tasks.upsert(copy)
            log(copy.id, "Created", null, copy.status, "Duplicated from \"" + t.title + "\"")
            newId = copy.id
        }
        newId?.let { resync(it) }
        return newId
    }

    /** Soft delete: the tombstone lets other devices learn about the deletion on their next sync. */
    suspend fun delete(id: String) {
        scheduler.cancel(id)
        val t = tasks.get(id) ?: return
        val now = System.currentTimeMillis()
        tasks.upsert(t.copy(deletedAt = now, updatedAt = now))
    }

    suspend fun addCategory(name: String, colorArgb: Long) {
        if (name.isNotBlank()) cats.upsert(CategoryEntity(name = name.trim(), colorArgb = colorArgb, updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteCategory(id: String) {
        db.withTransaction {
            val now = System.currentTimeMillis()
            tasks.clearCategory(id, now)
            cats.get(id)?.let { cats.upsert(it.copy(deletedAt = now, updatedAt = now)) }
        }
    }

    // ---- cloud sync support ------------------------------------------------------------------

    suspend fun changedTasks(since: Long) = tasks.changedSince(since)
    suspend fun changedCategories(since: Long) = cats.changedSince(since)
    suspend fun allSessions() = pomodoro.all()

    /** Applies server rows with last-write-wins on updatedAt. Returns how many local rows changed. */
    suspend fun applyRemote(rt: List<TaskEntity>, rc: List<CategoryEntity>, rs: List<PomodoroEntity>): Int {
        var changed = 0
        val touched = mutableListOf<String>()
        db.withTransaction {
            for (c in rc) {
                val local = cats.get(c.id)
                if (local == null || c.updatedAt > local.updatedAt) { cats.upsert(c); changed++ }
            }
            for (t in rt) {
                val local = tasks.get(t.id)
                if (local == null || t.updatedAt > local.updatedAt) { tasks.upsert(t); touched += t.id; changed++ }
            }
            for (s in rs) {
                val safe = if (s.taskId != null && tasks.get(s.taskId) == null) s.copy(taskId = null) else s
                pomodoro.insert(safe)
            }
        }
        touched.forEach { resync(it) }
        return changed
    }
}
