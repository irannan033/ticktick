package com.ppm.momentum.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.ppm.momentum.domain.PomodoroStatus
import com.ppm.momentum.domain.Priority
import com.ppm.momentum.domain.Recurrence
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
    val recurrence: Recurrence? = null,
    /** Tombstone: deleted rows are kept so the deletion can sync to other devices. */
    val deletedAt: Long? = null,
)

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val colorArgb: Long,
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = 0,
    val deletedAt: Long? = null,
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

@Entity(
    tableName = "pomodoro_sessions",
    foreignKeys = [ForeignKey(TaskEntity::class, ["id"], ["taskId"], onDelete = ForeignKey.SET_NULL)],
    indices = [Index("taskId")],
)
data class PomodoroEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val taskId: String?,
    val plannedMinutes: Int,
    val completedSeconds: Int,
    val startedAt: Long,
    val endedAt: Long?,
    val status: PomodoroStatus,
)
