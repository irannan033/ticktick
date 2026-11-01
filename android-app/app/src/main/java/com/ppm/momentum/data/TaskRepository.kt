package com.ppm.momentum.data

import androidx.room.withTransaction
import com.ppm.momentum.domain.Timeliness
import com.ppm.momentum.domain.TaskStatus
import com.ppm.momentum.domain.timeliness
import com.ppm.momentum.util.formatDateTime
import java.util.UUID

class TaskRepository(private val db: AppDatabase) {
    private val tasks = db.taskDao()
    private val cats = db.categoryDao()
    private val acts = db.activityDao()

    val allTasks = tasks.observeAll()
    val categories = cats.observeAll()
    fun observeTask(id: String) = tasks.observe(id)
    fun observeActivity(id: String) = acts.observe(id)
    suspend fun getTask(id: String): TaskEntity? = tasks.get(id)

    suspend fun seedDefaults() {
        if (cats.count() == 0) {
            listOf("Work" to 0xFF4F5BD5, "Personal" to 0xFF0F9D8A, "Study" to 0xFFF5A524)
                .forEach { (n, c) -> cats.upsert(CategoryEntity(name = n, colorArgb = c)) }
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
        return task.id
    }

    private suspend fun changeStatus(id: String, new: TaskStatus, action: String) {
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
                val note = when (updated.timeliness()) {
                    Timeliness.ON_TIME -> "Completed on time"
                    Timeliness.LATE -> "Completed late"
                    Timeliness.NOT_APPLICABLE -> null
                }
                log(id, action, t.status, new, note)
            }
        }
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
        return newId
    }

    suspend fun delete(id: String) = tasks.delete(id)

    suspend fun addCategory(name: String, colorArgb: Long) {
        if (name.isNotBlank()) cats.upsert(CategoryEntity(name = name.trim(), colorArgb = colorArgb))
    }

    suspend fun deleteCategory(id: String) {
        db.withTransaction {
            tasks.clearCategory(id)
            cats.delete(id)
        }
    }
}
