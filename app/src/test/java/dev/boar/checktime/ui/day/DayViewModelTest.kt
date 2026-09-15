package dev.boar.checktime.ui.day

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import dev.boar.checktime.AppContainer
import dev.boar.checktime.MainDispatcherRule
import dev.boar.checktime.domain.Allocation
import dev.boar.checktime.domain.TimeMath.MINUTE_MS
import dev.boar.checktime.domain.dayRange
import dev.boar.checktime.testContainer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
class DayViewModelTest {
    @get:Rule val mainDispatcher = MainDispatcherRule()

    private val context: Application = ApplicationProvider.getApplicationContext()
    private val zone = ZoneId.of("Europe/Moscow")
    private val today = LocalDate.of(2026, 9, 15)
    private val dayStart = dayRange(today, zone).start
    private var now = dayStart + 10 * 60 * MINUTE_MS // 10:00 местного
    private lateinit var container: AppContainer
    private var sleep = 0L
    private var food = 0L

    @Before fun setUp() = runTest {
        container = testContainer(context) { now }
        val g = container.categories.addGroup("Личное", 1)
        sleep = container.categories.addCategory(g, "Сон", 2)
        food = container.categories.addCategory(g, "Еда", 3)
    }

    @After fun tearDown() = container.db.close()

    private fun vm() = DayViewModel(container, zone)

    @Test fun notStartedShowsEmptyDay() = runTest {
        val s = vm().state.first { it.date == today }
        assertFalse(s.trackingStarted)
        assertTrue(s.isToday)
        assertEquals(0, s.tailMinutes)
        assertTrue(s.segments.isEmpty())
    }

    @Test fun totalsAndSegmentsForToday() = runTest {
        // учёт с 08:00, расписано 08:00–09:30: сон 60, еда 30
        container.timeline.startTracking(dayStart + 8 * 60 * MINUTE_MS)
        container.timeline.allocate(dayStart + 8 * 60 * MINUTE_MS, listOf(Allocation(sleep, 60), Allocation(food, 30)))

        val s = vm().state.first { it.segments.size == 2 }
        assertEquals(listOf("Сон" to 60 * MINUTE_MS, "Еда" to 30 * MINUTE_MS), s.totals.map { it.category.name to it.millis })
        assertEquals("Сон", s.segments[0].category!!.name)
        assertEquals(dayStart + 8 * 60 * MINUTE_MS, s.segments[0].startAt)
        assertTrue(s.trackingStarted)
        assertEquals(30, s.tailMinutes) // 09:30 -> 10:00
        assertFalse(s.clockWentBack)
    }

    @Test fun segmentCrossingMidnightIsClippedToDay() = runTest {
        val yesterday2300 = dayStart - 60 * MINUTE_MS
        container.timeline.startTracking(yesterday2300)
        container.timeline.allocate(yesterday2300, listOf(Allocation(sleep, 120))) // 23:00 -> 01:00

        val s = vm().state.first { it.segments.isNotEmpty() }
        assertEquals(dayStart, s.segments[0].startAt)
        assertEquals(60 * MINUTE_MS, s.totals[0].millis)
    }

    @Test fun navigationChangesDate() = runTest {
        val vm = vm()
        vm.state.first { it.date == today }
        vm.previousDay()
        val prev = vm.state.first { it.date == today.minusDays(1) }
        assertFalse(prev.isToday)
        vm.today()
        assertTrue(vm.state.first { it.date == today }.isToday)
    }

    @Test fun clockWentBackIsFlagged() = runTest {
        container.timeline.startTracking(now + 10 * MINUTE_MS)
        val s = vm().state.first { it.trackingStarted }
        assertTrue(s.clockWentBack)
        assertEquals(0, s.tailMinutes)
    }
}
