package dev.boar.checktime.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
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

    @Insert suspend fun insertAll(segments: List<Segment>): List<Long>

    @Query("SELECT * FROM segments WHERE id = :id")
    suspend fun byId(id: Long): Segment?

    /** Ближайший сегмент, заканчивающийся не позже startAt (смежный — если endAt == startAt). */
    @Query("SELECT * FROM segments WHERE endAt <= :startAt ORDER BY endAt DESC LIMIT 1")
    suspend fun previousOf(startAt: Long): Segment?

    /** Ближайший сегмент, начинающийся не раньше endAt (смежный — если startAt == endAt). */
    @Query("SELECT * FROM segments WHERE startAt >= :endAt ORDER BY startAt LIMIT 1")
    suspend fun nextOf(endAt: Long): Segment?

    @Update suspend fun update(segment: Segment)
    @Delete suspend fun delete(segment: Segment)
}
