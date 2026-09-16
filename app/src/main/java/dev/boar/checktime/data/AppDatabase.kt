package dev.boar.checktime.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [Group::class, Category::class, Segment::class, TrackingState::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun categoryDao(): CategoryDao
    abstract fun segmentDao(): SegmentDao
    abstract fun trackingStateDao(): TrackingStateDao
}
