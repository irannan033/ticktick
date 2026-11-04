package com.ppm.momentum.sync

import com.ppm.momentum.data.CategoryEntity
import com.ppm.momentum.data.PomodoroEntity
import com.ppm.momentum.data.TaskEntity
import com.ppm.momentum.domain.PomodoroStatus
import com.ppm.momentum.domain.Priority
import com.ppm.momentum.domain.Recurrence
import com.ppm.momentum.domain.TaskStatus
import org.json.JSONArray
import org.json.JSONObject

private fun JSONObject.long(key: String): Long? = if (!has(key) || isNull(key)) null else getLong(key)
private fun JSONObject.int(key: String): Int? = if (!has(key) || isNull(key)) null else getInt(key)
private fun JSONObject.str(key: String): String? = if (!has(key) || isNull(key)) null else getString(key)
private fun nul(v: Any?): Any = v ?: JSONObject.NULL

fun TaskEntity.toJson(): JSONObject = JSONObject()
    .put("id", id).put("title", title).put("description", description).put("priority", priority.name)
    .put("important", important).put("urgent", urgent).put("categoryId", nul(categoryId))
    .put("startAt", nul(startAt)).put("dueAt", nul(dueAt)).put("reminderAt", nul(reminderAt))
    .put("estimatedMinutes", nul(estimatedMinutes)).put("status", status.name).put("completedAt", nul(completedAt))
    .put("postponeCount", postponeCount).put("recurrence", nul(recurrence?.name))
    .put("createdAt", createdAt).put("updatedAt", updatedAt).put("deletedAt", nul(deletedAt))

fun JSONObject.toTask(): TaskEntity = TaskEntity(
    id = getString("id"), title = getString("title"), description = str("description") ?: "",
    priority = runCatching { Priority.valueOf(getString("priority")) }.getOrDefault(Priority.NONE),
    important = optBoolean("important"), urgent = optBoolean("urgent"), categoryId = str("categoryId"),
    startAt = long("startAt"), dueAt = long("dueAt"), reminderAt = long("reminderAt"),
    estimatedMinutes = int("estimatedMinutes"),
    status = runCatching { TaskStatus.valueOf(getString("status")) }.getOrDefault(TaskStatus.UPCOMING)
        .let { if (it == TaskStatus.OVERDUE) TaskStatus.UPCOMING else it },
    completedAt = long("completedAt"), postponeCount = optInt("postponeCount"),
    recurrence = str("recurrence")?.let { r -> runCatching { Recurrence.valueOf(r) }.getOrNull() },
    createdAt = long("createdAt") ?: 0L, updatedAt = long("updatedAt") ?: 0L, deletedAt = long("deletedAt"),
)

fun CategoryEntity.toJson(): JSONObject = JSONObject()
    .put("id", id).put("name", name).put("colorArgb", colorArgb).put("updatedAt", updatedAt).put("deletedAt", nul(deletedAt))

fun JSONObject.toCategory() = CategoryEntity(
    id = getString("id"), name = getString("name"), colorArgb = long("colorArgb") ?: 0L,
    updatedAt = long("updatedAt") ?: 0L, deletedAt = long("deletedAt"),
)

fun PomodoroEntity.toJson(): JSONObject = JSONObject()
    .put("id", id).put("taskId", nul(taskId)).put("plannedMinutes", plannedMinutes).put("completedSeconds", completedSeconds)
    .put("startedAt", startedAt).put("endedAt", nul(endedAt)).put("status", status.name)

fun JSONObject.toSession() = PomodoroEntity(
    id = getString("id"), taskId = str("taskId"), plannedMinutes = getInt("plannedMinutes"),
    completedSeconds = getInt("completedSeconds"), startedAt = getLong("startedAt"), endedAt = long("endedAt"),
    status = runCatching { PomodoroStatus.valueOf(getString("status")) }.getOrDefault(PomodoroStatus.COMPLETED),
)

fun <T> JSONArray.mapObjects(f: (JSONObject) -> T): List<T> = (0 until length()).map { f(getJSONObject(it)) }
