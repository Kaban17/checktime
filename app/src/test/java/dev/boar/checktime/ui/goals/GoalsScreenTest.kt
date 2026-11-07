package dev.boar.checktime.ui.goals

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.boar.checktime.domain.GoalProgress
import dev.boar.checktime.domain.GoalTarget
import dev.boar.checktime.domain.StatsPeriod
import dev.boar.checktime.domain.TimeMath.MINUTE_MS
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GoalsScreenTest {
    @get:Rule val compose = createComposeRule()

    private val week = StatsPeriod.Week(LocalDate.of(2026, 9, 14))
    private val group = GoalProgress(GoalTarget.OfGroup(1), "Личное", 1, 300, 200 * MINUTE_MS)
    private val sport = GoalProgress(GoalTarget.OfCategory(2), "Спорт", 2, 60, 90 * MINUTE_MS)
    private val candidates = listOf(GoalCandidate(GoalTarget.OfCategory(3), "Еда", 3))

    private fun state(isCurrent: Boolean = true, goals: List<GoalProgress> = listOf(group, sport)) =
        GoalsUiState(week, isCurrent, goals, if (isCurrent) candidates else emptyList())

    private var saved: Pair<GoalTarget, Int>? = null
    private var deleted: GoalTarget? = null

    private fun show(state: GoalsUiState) = compose.setContent {
        GoalsScreen(state, onPrevious = {}, onNext = {}, onSave = { t, m -> saved = t to m }, onDelete = { deleted = it })
    }

    @Test fun showsProgressRemainingAndReached() {
        show(state())
        compose.onNodeWithText("3 ч 20 мин из 5 ч").assertExists()
        compose.onNodeWithText("осталось 1 ч 40 мин").assertExists()
        compose.onNodeWithText("1 ч 30 мин из 1 ч").assertExists()
        compose.onNodeWithContentDescription("Цель достигнута").assertExists()
        compose.onNodeWithTag("goals-next").assertIsNotEnabled()
    }

    @Test fun pastWeekIsReadOnly() {
        show(state(isCurrent = false))
        compose.onNodeWithText("осталось 1 ч 40 мин").assertDoesNotExist()
        compose.onNodeWithTag("goals-add").assertDoesNotExist()
        compose.onNodeWithText("Личное").performClick()
        compose.onNodeWithText("Удалить").assertDoesNotExist()
    }

    @Test fun emptyCurrentWeekShowsHint() {
        show(state(goals = emptyList()))
        compose.onNodeWithText("Целей на эту неделю нет").assertExists()
        compose.onNodeWithText("Нажмите «+», чтобы поставить цель на группу или категорию").assertExists()
    }

    @Test fun addDialogSavesCandidateWithSteppedMinutes() {
        show(state())
        compose.onNodeWithTag("goals-add").performClick()
        compose.onNodeWithText("Еда").assertExists()
        compose.onNodeWithTag("goal-minutes").assertTextEquals("5 ч")
        compose.onNodeWithTag("goal-more").performClick()
        compose.onNodeWithTag("goal-minutes").assertTextEquals("5 ч 30 мин")
        compose.onNodeWithText("Сохранить").performClick()
        assertEquals(GoalTarget.OfCategory(3) to 330, saved)
    }

    @Test fun editDialogClampsAndDeletes() {
        show(state())
        compose.onNodeWithText("Спорт").performClick()
        compose.onNodeWithTag("goal-less").performClick()
        compose.onNodeWithTag("goal-minutes").assertTextEquals("30 мин")
        compose.onNodeWithTag("goal-less").performClick()
        compose.onNodeWithTag("goal-minutes").assertTextEquals("30 мин")
        compose.onNodeWithText("Удалить").performClick()
        assertEquals(GoalTarget.OfCategory(2), deleted)
    }

    @Test fun dialogClosesWhenWeekStopsBeingCurrent() {
        var current by mutableStateOf(state())
        compose.setContent {
            GoalsScreen(current, onPrevious = {}, onNext = {}, onSave = { t, m -> saved = t to m }, onDelete = { deleted = it })
        }
        compose.onNodeWithTag("goals-add").performClick()
        compose.onNodeWithText("Сохранить").assertExists()
        current = state(isCurrent = false)
        compose.onNodeWithText("Сохранить").assertDoesNotExist()
    }
}
