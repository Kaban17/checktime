package dev.boar.checktime.domain

import androidx.room.withTransaction
import dev.boar.checktime.data.AppDatabase
import dev.boar.checktime.data.WeeklyGoal
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/** Единственное место записи целей (спека 2.1.1). */
class GoalRepository(private val db: AppDatabase) {
    private val dao = db.goalDao()

    fun observeAll(): Flow<List<WeeklyGoal>> = dao.observeAll()

    /**
     * Ставит, меняет или снимает (`minutes = 0`) цель [target] начиная с недели [monday].
     * Запись этой же недели заменяется; прошлые недели не трогаются. Если новое значение
     * совпадает с действовавшим до этой недели (в т.ч. снятие цели, которой не было), записи нет.
     */
    suspend fun set(target: GoalTarget, minutes: Int, monday: LocalDate) = db.withTransaction {
        require(minutes >= 0)
        val week = monday.toEpochDay()
        val categoryId = (target as? GoalTarget.OfCategory)?.id
        val groupId = (target as? GoalTarget.OfGroup)?.id
        val history = dao.history(categoryId, groupId)
        history.filter { it.validFrom == week }.forEach { dao.delete(it) }
        val before = history.filter { it.validFrom < week }.maxByOrNull { it.validFrom }?.minutes ?: 0
        if (minutes != before) {
            dao.insert(WeeklyGoal(categoryId = categoryId, groupId = groupId, minutes = minutes, validFrom = week))
        }
    }
}
