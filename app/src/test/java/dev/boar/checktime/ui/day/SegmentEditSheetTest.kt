package dev.boar.checktime.ui.day

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.boar.checktime.data.Category
import dev.boar.checktime.data.Group
import dev.boar.checktime.data.GroupWithCategories
import dev.boar.checktime.data.Segment
import dev.boar.checktime.domain.TimeMath.MINUTE_MS
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SegmentEditSheetTest {
    @get:Rule val compose = createComposeRule()

    private val zone = ZoneId.of("UTC")
    private val h = 60 * MINUTE_MS
    private val sleep = Category(id = 1, groupId = 1, name = "Сон", color = 0, sortOrder = 0)
    private val food = Category(id = 2, groupId = 1, name = "Еда", color = 0, sortOrder = 1)
    private val groups = listOf(GroupWithCategories(Group(1, "Личное", 0, 0), listOf(sleep, food)))
    private val prev = Segment(id = 10, startAt = 8 * h, endAt = 9 * h, categoryId = 1)
    private val cur = Segment(id = 11, startAt = 9 * h, endAt = 10 * h, categoryId = 2)
    private val next = Segment(id = 12, startAt = 10 * h, endAt = 11 * h, categoryId = 1)

    private fun state(previous: Segment? = prev, nxt: Segment? = next) =
        EditorState(cur, previous, nxt, food, groups, stepMinutes = 5)

    @Test fun changeCategoryFlow() {
        var chosen = -1L
        compose.setContent {
            SegmentEditContent(state(), onChangeCategory = { chosen = it }, onSplit = {}, onMoveStart = {}, onMoveEnd = {}, onMergePrevious = {}, onMergeNext = {}, zone = zone)
        }
        compose.onNodeWithText("09:00 – 10:00 · 1 ч").assertExists()
        compose.onNodeWithText("Категория").performClick()
        compose.onNodeWithText("Сон").performClick()
        assertEquals(1L, chosen)
    }

    @Test fun splitDefaultsToMidpointAndConfirms() {
        var splitAt = -1L
        compose.setContent {
            SegmentEditContent(state(), onChangeCategory = {}, onSplit = { splitAt = it }, onMoveStart = {}, onMoveEnd = {}, onMergePrevious = {}, onMergeNext = {}, zone = zone)
        }
        compose.onNodeWithText("Разбить").performClick()
        compose.onNodeWithTag("time-value").assertExists()
        compose.onNodeWithText("09:30").assertExists()
        compose.onNodeWithTag("time-inc").performClick()
        compose.onNodeWithText("Готово").performClick()
        assertEquals(9 * h + 35 * MINUTE_MS, splitAt)
    }

    @Test fun moveEndStartsAtCurrentBoundary() {
        var moved = -1L
        compose.setContent {
            SegmentEditContent(state(), onChangeCategory = {}, onSplit = {}, onMoveStart = {}, onMoveEnd = { moved = it }, onMergePrevious = {}, onMergeNext = {}, zone = zone)
        }
        compose.onNodeWithText("Сдвинуть конец").performClick()
        compose.onNodeWithText("10:00").assertExists()
        compose.onNodeWithTag("time-dec").performClick()
        compose.onNodeWithText("Готово").performClick()
        assertEquals(10 * h - 5 * MINUTE_MS, moved)
    }

    @Test fun neighbourActionsDisabledWithoutNeighbours() {
        compose.setContent {
            SegmentEditContent(state(previous = null, nxt = null), onChangeCategory = {}, onSplit = {}, onMoveStart = {}, onMoveEnd = {}, onMergePrevious = {}, onMergeNext = {}, zone = zone)
        }
        compose.onNodeWithText("Сдвинуть начало").assertIsNotEnabled()
        compose.onNodeWithText("Сдвинуть конец").assertIsNotEnabled()
        compose.onNodeWithText("Слить с предыдущей").assertIsNotEnabled()
        compose.onNodeWithText("Слить со следующей").assertIsNotEnabled()
    }

    @Test fun splitDisabledForOneMinuteSegment() {
        val tiny = Segment(id = 11, startAt = 9 * h, endAt = 9 * h + MINUTE_MS, categoryId = 2)
        compose.setContent {
            SegmentEditContent(
                EditorState(tiny, prev, next, food, groups, stepMinutes = 5),
                onChangeCategory = {}, onSplit = {}, onMoveStart = {}, onMoveEnd = {}, onMergePrevious = {}, onMergeNext = {}, zone = zone,
            )
        }
        compose.onNodeWithText("Разбить").assertIsNotEnabled()
    }

    @Test fun mergeNextCallsBack() {
        var merged = false
        compose.setContent {
            SegmentEditContent(state(), onChangeCategory = {}, onSplit = {}, onMoveStart = {}, onMoveEnd = {}, onMergePrevious = {}, onMergeNext = { merged = true }, zone = zone)
        }
        compose.onNodeWithText("Слить со следующей").performClick()
        assertEquals(true, merged)
    }
}
