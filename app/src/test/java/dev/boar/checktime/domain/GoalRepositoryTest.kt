package dev.boar.checktime.domain

import dev.boar.checktime.data.AppDatabase
import dev.boar.checktime.data.CategoryRepository
import dev.boar.checktime.data.WeeklyGoal
import dev.boar.checktime.inMemoryDb
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class GoalRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: GoalRepository
    private lateinit var categories: CategoryRepository
    private val week1 = LocalDate.of(2026, 9, 14)
    private val week2 = week1.plusWeeks(1)
    private var group = 0L
    private var sport = 0L

    @Before fun setUp() = runTest {
        db = inMemoryDb()
        repo = GoalRepository(db)
        categories = CategoryRepository(db.categoryDao())
        group = categories.addGroup("Личное", 1)
        sport = categories.addCategory(group, "Спорт", 2)
    }

    @After fun tearDown() = db.close()

    private suspend fun rows(): List<WeeklyGoal> = repo.observeAll().first()

    @Test fun changeWithinWeekReplacesRow() = runTest {
        repo.set(GoalTarget.OfCategory(sport), 300, week1)
        repo.set(GoalTarget.OfCategory(sport), 240, week1)
        assertEquals(listOf(240 to week1.toEpochDay()), rows().map { it.minutes to it.validFrom })
    }

    @Test fun changeInLaterWeekKeepsHistory() = runTest {
        repo.set(GoalTarget.OfCategory(sport), 300, week1)
        repo.set(GoalTarget.OfCategory(sport), 240, week2)
        val goals = rows()
        assertEquals(mapOf(GoalTarget.OfCategory(sport) to 300), activeGoals(goals, week1))
        assertEquals(mapOf(GoalTarget.OfCategory(sport) to 240), activeGoals(goals, week2))
    }

    @Test fun removingOlderGoalWritesTombstone() = runTest {
        repo.set(GoalTarget.OfGroup(group), 600, week1)
        repo.set(GoalTarget.OfGroup(group), 0, week2)
        val goals = rows()
        assertEquals(2, goals.size)
        assertEquals(mapOf(GoalTarget.OfGroup(group) to 600), activeGoals(goals, week1))
        assertTrue(activeGoals(goals, week2).isEmpty())
    }

    @Test fun removingGoalCreatedThisWeekLeavesNothing() = runTest {
        repo.set(GoalTarget.OfCategory(sport), 300, week2)
        repo.set(GoalTarget.OfCategory(sport), 0, week2)
        assertTrue(rows().isEmpty())
    }

    @Test fun categoryAndGroupGoalsAreIndependent() = runTest {
        repo.set(GoalTarget.OfCategory(sport), 300, week1)
        repo.set(GoalTarget.OfGroup(group), 600, week1)
        repo.set(GoalTarget.OfGroup(group), 0, week1)
        assertEquals(mapOf(GoalTarget.OfCategory(sport) to 300), activeGoals(rows(), week1))
    }

    @Test fun deletingCategoryAndGroupCascadesGoals() = runTest {
        repo.set(GoalTarget.OfCategory(sport), 300, week1)
        repo.set(GoalTarget.OfGroup(group), 600, week1)
        val sportCategory = db.categoryDao().categoriesOfGroup(group).single()
        assertTrue(categories.deleteCategory(sportCategory))
        assertEquals(listOf(GoalTarget.OfGroup(group)), rows().map { it.target })
        assertTrue(categories.deleteGroup(db.categoryDao().groups().single()))
        assertTrue(rows().isEmpty())
    }

    @Test fun revertingToPreviousValueLeavesNoRow() = runTest {
        repo.set(GoalTarget.OfCategory(sport), 300, week1)
        repo.set(GoalTarget.OfCategory(sport), 240, week2)
        repo.set(GoalTarget.OfCategory(sport), 300, week2)
        assertEquals(listOf(week1.toEpochDay()), rows().map { it.validFrom })
    }
}
