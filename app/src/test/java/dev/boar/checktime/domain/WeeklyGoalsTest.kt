package dev.boar.checktime.domain

import dev.boar.checktime.data.Category
import dev.boar.checktime.data.Group
import dev.boar.checktime.data.GroupWithCategories
import dev.boar.checktime.data.Segment
import dev.boar.checktime.data.WeeklyGoal
import dev.boar.checktime.domain.TimeMath.MINUTE_MS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class WeeklyGoalsTest {
    private val w1 = LocalDate.of(2026, 9, 14)
    private val w2 = w1.plusWeeks(1)
    private val w3 = w1.plusWeeks(2)

    private fun goal(categoryId: Long? = null, groupId: Long? = null, minutes: Int, week: LocalDate) =
        WeeklyGoal(categoryId = categoryId, groupId = groupId, minutes = minutes, validFrom = week.toEpochDay())

    @Test fun latestRecordNotAfterWeekWins() {
        val goals = listOf(goal(categoryId = 1, minutes = 300, week = w1), goal(categoryId = 1, minutes = 240, week = w3))
        assertTrue(activeGoals(goals, w1.minusWeeks(1)).isEmpty())
        assertEquals(mapOf(GoalTarget.OfCategory(1) to 300), activeGoals(goals, w2))
        assertEquals(mapOf(GoalTarget.OfCategory(1) to 240), activeGoals(goals, w3))
    }

    @Test fun tombstoneRemovesGoalFromItsWeekOn() {
        val goals = listOf(goal(groupId = 1, minutes = 600, week = w1), goal(groupId = 1, minutes = 0, week = w2))
        assertEquals(mapOf(GoalTarget.OfGroup(1) to 600), activeGoals(goals, w1))
        assertTrue(activeGoals(goals, w3).isEmpty())
    }

    @Test fun categoryAndGroupWithSameIdAreDifferentTargets() {
        val goals = listOf(goal(categoryId = 1, minutes = 60, week = w1), goal(groupId = 1, minutes = 120, week = w1))
        assertEquals(mapOf(GoalTarget.OfCategory(1) to 60, GoalTarget.OfGroup(1) to 120), activeGoals(goals, w1))
    }

    private val personal = Group(id = 1, name = "Личное", color = 10, sortOrder = 0)
    private val work = Group(id = 2, name = "Работа", color = 20, sortOrder = 1)
    private val sport = Category(id = 1, groupId = 1, name = "Спорт", color = 11, sortOrder = 0)
    private val old = Category(id = 2, groupId = 1, name = "Старое", color = 12, sortOrder = 1, archived = true)
    private val code = Category(id = 3, groupId = 2, name = "Код", color = 21, sortOrder = 0)
    private val tree = listOf(GroupWithCategories(personal, listOf(sport, old)), GroupWithCategories(work, listOf(code)))

    private fun seg(categoryId: Long, minutes: Long, at: Long = 0) =
        Segment(startAt = at, endAt = at + minutes * MINUTE_MS, categoryId = categoryId)

    @Test fun progressFollowsTreeOrderAndCountsArchivedInGroup() {
        val goals = mapOf(
            GoalTarget.OfCategory(3) to 60,
            GoalTarget.OfCategory(1) to 120,
            GoalTarget.OfGroup(1) to 180,
            GoalTarget.OfCategory(99) to 10,
        )
        val segments = listOf(seg(1, 90), seg(2, 30, at = 100 * MINUTE_MS), seg(3, 75, at = 200 * MINUTE_MS))
        val progress = goalProgress(goals, segments, tree)

        assertEquals(listOf("Личное", "Спорт", "Код"), progress.map { it.name })
        val (group, sportGoal, codeGoal) = progress
        assertTrue(group.isGroup)
        assertEquals(120 * MINUTE_MS, group.doneMillis)
        assertEquals(60 * MINUTE_MS, group.remainingMillis)
        assertFalse(group.reached)
        assertEquals(90 * MINUTE_MS, sportGoal.doneMillis)
        assertEquals(0.75f, sportGoal.fraction)
        assertTrue(codeGoal.reached)
        assertEquals(0L, codeGoal.remainingMillis)
        assertEquals(1f, codeGoal.fraction)
    }

    @Test fun goalWithoutTimeHasZeroProgress() {
        val progress = goalProgress(mapOf(GoalTarget.OfGroup(2) to 60), emptyList(), tree).single()
        assertEquals(0L, progress.doneMillis)
        assertEquals(0f, progress.fraction)
        assertEquals(20, progress.color)
    }
}
