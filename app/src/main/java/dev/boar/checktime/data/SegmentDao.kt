package dev.boar.checktime.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SegmentDao {
    /** Сегменты, пересекающие [from, to). */
    @Query("SELECT * FROM segments WHERE endAt > :from AND startAt < :to ORDER BY startAt")
    fun observeOverlapping(from: Long, to: Long): Flow<List<Segment>>

    @Query("SELECT * FROM segments WHERE endAt > :from AND startAt < :to ORDER BY startAt")
    suspend fun overlapping(from: Long, to: Long): List<Segment>

    @Query("SELECT * FROM segments ORDER BY startAt")
    suspend fun all(): List<Segment>

    @Insert suspend fun insertAll(segments: List<Segment>)
}
