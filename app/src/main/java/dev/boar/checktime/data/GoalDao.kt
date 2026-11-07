package dev.boar.checktime.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface GoalDao {
    @Query("SELECT * FROM weekly_goals ORDER BY validFrom, id")
    fun observeAll(): Flow<List<WeeklyGoal>>

    /** История одного объекта; `IS` — чтобы сравнение с NULL работало для второго столбца. */
    @Query("SELECT * FROM weekly_goals WHERE categoryId IS :categoryId AND groupId IS :groupId ORDER BY validFrom")
    suspend fun history(categoryId: Long?, groupId: Long?): List<WeeklyGoal>

    @Insert suspend fun insert(goal: WeeklyGoal): Long
    @Delete suspend fun delete(goal: WeeklyGoal)
}
