package dev.boar.checktime.ui.allocation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.boar.checktime.data.Category
import dev.boar.checktime.data.Group
import dev.boar.checktime.data.GroupWithCategories
import dev.boar.checktime.domain.AllocationDraft
import dev.boar.checktime.domain.TimeMath.MINUTE_MS
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AllocationScreenTest {
    @get:Rule val compose = createComposeRule()

    private val groups = listOf(
        GroupWithCategories(
            Group(id = 1, name = "Личное", color = 0xFF00FF00.toInt(), sortOrder = 0),
            listOf(
                Category(id = 1, groupId = 1, name = "Сон", color = 0xFF0000FF.toInt(), sortOrder = 0),
                Category(id = 2, groupId = 1, name = "Еда", color = 0xFFFF0000.toInt(), sortOrder = 1),
            ),
        ),
    )

    private fun setContent(initial: AllocationDraft) {
        var draft by mutableStateOf(initial)
        compose.setContent {
            AllocationScreen(
                state = AllocationUiState.Ready(start = 0, end = initial.totalMinutes * MINUTE_MS, groups = groups, draft = draft),
                onIncrement = { draft = draft.increment(it) },
                onDecrement = { draft = draft.decrement(it) },
                onAssignRest = { draft = draft.assignRest(it) },
                onSave = {},
                onPostpone = {},
            )
        }
    }

    @Test fun tappingNameAssignsRestAndEnablesDone() {
        setContent(AllocationDraft(totalMinutes = 47, stepMinutes = 5, order = listOf(1L, 2L)))
        compose.onNodeWithText("Готово").assertIsNotEnabled()
        compose.onNodeWithText("Сон").performClick()
        compose.onNodeWithTag("minutes-1").assertTextEquals("47")
        compose.onNodeWithText("Осталось: 0 мин").assertExists()
        compose.onNodeWithText("Готово").assertIsEnabled()
    }

    @Test fun plusAndMinusChangeByStep() {
        setContent(AllocationDraft(totalMinutes = 47, stepMinutes = 5, order = listOf(1L, 2L)))
        compose.onNodeWithTag("inc-2").performClick()
        compose.onNodeWithTag("inc-2").performClick()
        compose.onNodeWithTag("minutes-2").assertTextEquals("10")
        compose.onNodeWithText("Осталось: 37 мин").assertExists()
        compose.onNodeWithTag("dec-2").performClick()
        compose.onNodeWithTag("minutes-2").assertTextEquals("5")
    }
}
