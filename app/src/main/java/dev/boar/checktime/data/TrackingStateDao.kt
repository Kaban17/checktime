package dev.boar.checktime.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackingStateDao {
    @Query("SELECT * FROM tracking_state WHERE id = 1")
    fun observe(): Flow<TrackingState?>

    @Query("SELECT * FROM tracking_state WHERE id = 1")
    suspend fun get(): TrackingState?

    @Upsert suspend fun upsert(state: TrackingState)
}
