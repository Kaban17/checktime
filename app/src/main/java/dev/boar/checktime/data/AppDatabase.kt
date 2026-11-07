package dev.boar.checktime.data

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [Group::class, Category::class, Segment::class, TrackingState::class, WeeklyGoal::class],
    version = 2,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2)], // + weekly_goals
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun categoryDao(): CategoryDao
    abstract fun segmentDao(): SegmentDao
    abstract fun trackingStateDao(): TrackingStateDao
    abstract fun goalDao(): GoalDao
}
