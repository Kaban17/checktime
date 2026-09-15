package dev.boar.checktime.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Query("SELECT * FROM groups ORDER BY sortOrder, id")
    fun observeGroups(): Flow<List<Group>>

    @Query("SELECT * FROM categories ORDER BY sortOrder, id")
    fun observeCategories(): Flow<List<Category>>

    @Query("SELECT * FROM groups ORDER BY sortOrder, id")
    suspend fun groups(): List<Group>

    @Query("SELECT * FROM categories ORDER BY sortOrder, id")
    suspend fun categories(): List<Category>

    @Query("SELECT * FROM categories WHERE groupId = :groupId ORDER BY sortOrder, id")
    suspend fun categoriesOfGroup(groupId: Long): List<Category>

    @Query("SELECT COUNT(*) FROM groups")
    suspend fun groupCount(): Int

    @Query("SELECT COUNT(*) FROM categories WHERE groupId = :groupId")
    suspend fun categoryCount(groupId: Long): Int

    @Query("SELECT COUNT(*) FROM segments WHERE categoryId = :categoryId")
    suspend fun segmentCount(categoryId: Long): Int

    @Insert suspend fun insertGroup(group: Group): Long
    @Insert suspend fun insertCategory(category: Category): Long
    @Update suspend fun updateGroup(group: Group)
    @Update suspend fun updateGroups(groups: List<Group>)
    @Update suspend fun updateCategory(category: Category)
    @Update suspend fun updateCategories(categories: List<Category>)
    @Delete suspend fun deleteGroup(group: Group)
    @Delete suspend fun deleteCategory(category: Category)
}
