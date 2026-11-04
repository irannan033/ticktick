package com.ppm.momentum.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.migration.Migration
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks WHERE deletedAt IS NULL ORDER BY COALESCE(dueAt, 9223372036854775807) ASC, createdAt DESC")
    fun observeAll(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE id = :id AND deletedAt IS NULL")
    fun observe(id: String): Flow<TaskEntity?>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun get(id: String): TaskEntity?

    // @Upsert updates in place. REPLACE would delete+insert and cascade-delete the activity log.
    @Upsert suspend fun upsert(task: TaskEntity)

    @Query("SELECT * FROM tasks WHERE updatedAt >= :since")
    suspend fun changedSince(since: Long): List<TaskEntity>

    @Query("UPDATE tasks SET categoryId = NULL, updatedAt = :now WHERE categoryId = :categoryId")
    suspend fun clearCategory(categoryId: String, now: Long)
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories WHERE deletedAt IS NULL ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT COUNT(*) FROM categories WHERE deletedAt IS NULL")
    suspend fun count(): Int

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun get(id: String): CategoryEntity?

    @Query("SELECT * FROM categories WHERE updatedAt >= :since")
    suspend fun changedSince(since: Long): List<CategoryEntity>

    @Upsert suspend fun upsert(category: CategoryEntity)
}

@Dao
interface ActivityDao {
    @Query("SELECT * FROM task_activity WHERE taskId = :taskId ORDER BY timestamp DESC, id DESC")
    fun observe(taskId: String): Flow<List<ActivityEntity>>

    @Insert suspend fun insert(activity: ActivityEntity)
}

@Dao
interface PomodoroDao {
    @Insert(onConflict = androidx.room.OnConflictStrategy.IGNORE) suspend fun insert(session: PomodoroEntity)

    @Query("SELECT * FROM pomodoro_sessions")
    suspend fun all(): List<PomodoroEntity>

    @Query("SELECT * FROM pomodoro_sessions ORDER BY startedAt DESC")
    fun observeAll(): Flow<List<PomodoroEntity>>

    @Query("SELECT * FROM pomodoro_sessions WHERE taskId = :taskId ORDER BY startedAt DESC")
    fun observeForTask(taskId: String): Flow<List<PomodoroEntity>>
}

/** v1 -> v2: adds Pomodoro sessions. Existing tables are untouched (no data loss). */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `pomodoro_sessions` (`id` TEXT NOT NULL, `taskId` TEXT, " +
                "`plannedMinutes` INTEGER NOT NULL, `completedSeconds` INTEGER NOT NULL, `startedAt` INTEGER NOT NULL, " +
                "`endedAt` INTEGER, `status` TEXT NOT NULL, PRIMARY KEY(`id`), " +
                "FOREIGN KEY(`taskId`) REFERENCES `tasks`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_pomodoro_sessions_taskId` ON `pomodoro_sessions` (`taskId`)")
    }
}

/** v2 -> v3: recurrence + soft-delete tombstones (needed for cloud sync). Additive only. */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `tasks` ADD COLUMN `recurrence` TEXT")
        db.execSQL("ALTER TABLE `tasks` ADD COLUMN `deletedAt` INTEGER")
        db.execSQL("ALTER TABLE `categories` ADD COLUMN `updatedAt` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `categories` ADD COLUMN `deletedAt` INTEGER")
    }
}

@Database(
    entities = [TaskEntity::class, CategoryEntity::class, ActivityEntity::class, PomodoroEntity::class],
    version = 3,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun categoryDao(): CategoryDao
    abstract fun activityDao(): ActivityDao
    abstract fun pomodoroDao(): PomodoroDao
}
