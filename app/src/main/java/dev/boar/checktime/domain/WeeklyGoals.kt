package dev.boar.checktime.domain

import dev.boar.checktime.data.GroupWithCategories
import dev.boar.checktime.data.Segment
import dev.boar.checktime.data.WeeklyGoal
import java.time.LocalDate

/** На что поставлена цель. */
sealed interface GoalTarget {
    data class OfCategory(val id: Long) : GoalTarget
    data class OfGroup(val id: Long) : GoalTarget
}

val WeeklyGoal.target: GoalTarget
    get() = categoryId?.let { GoalTarget.OfCategory(it) } ?: GoalTarget.OfGroup(requireNotNull(groupId))

/** Действующие на неделю [monday] цели: последняя запись объекта с validFrom ≤ monday, снятые отброшены. */
fun activeGoals(goals: List<WeeklyGoal>, monday: LocalDate): Map<GoalTarget, Int> {
    val week = monday.toEpochDay()
    return goals
        .filter { it.validFrom <= week }
        .groupBy { it.target }
        .mapValues { (_, history) -> history.maxBy { it.validFrom }.minutes }
        .filterValues { it > 0 }
}

data class GoalProgress(
    val target: GoalTarget,
    val name: String,
    val color: Int,
    val goalMinutes: Int,
    val doneMillis: Long,
) {
    val isGroup: Boolean get() = target is GoalTarget.OfGroup
    val goalMillis: Long get() = goalMinutes * 60_000L
    val reached: Boolean get() = doneMillis >= goalMillis
    val remainingMillis: Long get() = (goalMillis - doneMillis).coerceAtLeast(0)
    val fraction: Float get() = (doneMillis.toFloat() / goalMillis).coerceIn(0f, 1f)
}

/**
 * Прогресс по [goals] за неделю по уже обрезанным [segments]. Порядок — как в дереве:
 * цель группы, за ней цели её категорий. Группа считает все свои категории, включая архивные.
 * Цели на неизвестные объекты отбрасываются.
 */
fun goalProgress(goals: Map<GoalTarget, Int>, segments: List<Segment>, tree: List<GroupWithCategories>): List<GoalProgress> {
    val totals = totalsByCategory(segments)
    return tree.flatMap { g ->
        val groupGoal = goals[GoalTarget.OfGroup(g.group.id)]?.let { minutes ->
            GoalProgress(
                GoalTarget.OfGroup(g.group.id), g.group.name, g.group.color, minutes,
                g.categories.sumOf { totals[it.id] ?: 0L },
            )
        }
        val categoryGoals = g.categories.mapNotNull { c ->
            goals[GoalTarget.OfCategory(c.id)]?.let { minutes ->
                GoalProgress(GoalTarget.OfCategory(c.id), c.name, c.color, minutes, totals[c.id] ?: 0L)
            }
        }
        listOfNotNull(groupGoal) + categoryGoals
    }
}
