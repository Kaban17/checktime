package dev.boar.checktime.ui.stats

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.boar.checktime.data.Category
import dev.boar.checktime.data.Group
import dev.boar.checktime.domain.CategoryStat
import dev.boar.checktime.domain.GroupStat
import dev.boar.checktime.domain.PeriodKind
import dev.boar.checktime.domain.StatsPeriod
import dev.boar.checktime.domain.StatsResult
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
class StatsScreenTest {
    @get:Rule val compose = createComposeRule()

    private val personal = Group(id = 1, name = "Личное", color = 0xFF43A047.toInt(), sortOrder = 0)
    private val sleep = Category(id = 1, groupId = 1, name = "Сон", color = 0xFF00ACC1.toInt(), sortOrder = 0)
    private val food = Category(id = 2, groupId = 1, name = "Еда", color = 0xFF1E88E5.toInt(), sortOrder = 1)
    private val sleepStat = CategoryStat(sleep, personal, 60 * MINUTE_MS, 0.75f)
    private val foodStat = CategoryStat(food, personal, 20 * MINUTE_MS, 0.25f)
    private val result = StatsResult(
        totalMillis = 80 * MINUTE_MS,
        groups = listOf(GroupStat(personal, 80 * MINUTE_MS, 1f, listOf(sleepStat, foodStat))),
        categories = listOf(sleepStat, foodStat),
    )

    private fun state(result: StatsResult = this.result, canGoForward: Boolean = false) = StatsUiState(
        period = StatsPeriod.Day(LocalDate.of(2026, 9, 16)),
        kind = PeriodKind.DAY,
        result = result,
        canGoForward = canGoForward,
        dayCount = 1,
    )

    @Test fun showsTotalGroupsAndChart() {
        compose.setContent { StatsScreen(state(), onSelectKind = {}, onSelectCustom = { _, _ -> }, onPrevious = {}, onNext = {}) }
        compose.onNodeWithText("Всего: 1 ч 20 мин").assertExists()
        compose.onNodeWithText("Личное").assertExists()
        compose.onNodeWithText("100%").assertExists()
        compose.onNodeWithTag("stats-chart").assertExists()
    }

    @Test fun groupExpandsToCategories() {
        compose.setContent { StatsScreen(state(), onSelectKind = {}, onSelectCustom = { _, _ -> }, onPrevious = {}, onNext = {}) }
        // «Сон» уже есть один раз — как подпись столбика диаграммы; раскрытие группы добавляет строку таблицы.
        assertEquals(1, compose.onAllNodesWithText("Сон").fetchSemanticsNodes().size)
        compose.onNodeWithText("Личное").performClick()
        assertEquals(2, compose.onAllNodesWithText("Сон").fetchSemanticsNodes().size)
        compose.onNodeWithText("75%").assertExists()
    }

    @Test fun periodChipsAndArrowsCallBack() {
        var kind: PeriodKind? = null
        var prev = 0
        compose.setContent {
            StatsScreen(state(canGoForward = false), onSelectKind = { kind = it }, onSelectCustom = { _, _ -> }, onPrevious = { prev++ }, onNext = {})
        }
        compose.onNodeWithText("Неделя").performClick()
        assertEquals(PeriodKind.WEEK, kind)
        compose.onNodeWithTag("stats-prev").performClick()
        assertEquals(1, prev)
        compose.onNodeWithTag("stats-next").assertIsNotEnabled()
    }

    @Test fun forwardArrowEnabledForPastPeriods() {
        compose.setContent { StatsScreen(state(canGoForward = true), onSelectKind = {}, onSelectCustom = { _, _ -> }, onPrevious = {}, onNext = {}) }
        compose.onNodeWithTag("stats-next").assertIsEnabled()
    }

    @Test fun emptyPeriodShowsHint() {
        val empty = StatsResult(0L, emptyList(), emptyList())
        compose.setContent { StatsScreen(state(empty), onSelectKind = {}, onSelectCustom = { _, _ -> }, onPrevious = {}, onNext = {}) }
        compose.onNodeWithText("За этот период записей нет").assertExists()
        compose.onNodeWithTag("stats-chart").assertDoesNotExist()
    }
}
