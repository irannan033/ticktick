package com.ppm.momentum.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.ppm.momentum.domain.Priority
import com.ppm.momentum.domain.TaskStatus
import java.util.UUID

/**
 * String UUID primary keys (not auto-increment ints) so records created offline on
 * several devices can later be merged without id collisions (Phase 3 sync).
 */
@Entity(tableName = "tasks", indices = [Index("dueAt"), Index("status"), Index("categoryId")])
data class TaskEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String,
    val description: String = "",
    val priority: Priority = Priority.NONE,
    val important: Boolean = false,
    val urgent: Boolean = false,
    val categoryId: String? = null,
    val startAt: Long? = null,
    val dueAt: Long? = null,
    val reminderAt: Long? = null,
    val estimatedMinutes: Int? = null,
    val status: TaskStatus = TaskStatus.UPCOMING,
    val completedAt: Long? = null,
    val postponeCount: Int = 0,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val colorArgb: Long,
)

@Entity(
    tableName = "task_activity",
    foreignKeys = [ForeignKey(TaskEntity::class, ["id"], ["taskId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("taskId")],
)
data class ActivityEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: String,
    val action: String,
    val previousStatus: TaskStatus?,
    val newStatus: TaskStatus?,
    val note: String?,
    val timestamp: Long,
)
