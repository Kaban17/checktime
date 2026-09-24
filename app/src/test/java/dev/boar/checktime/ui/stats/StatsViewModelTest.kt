package dev.boar.checktime.ui.stats

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import dev.boar.checktime.AppContainer
import dev.boar.checktime.MainDispatcherRule
import dev.boar.checktime.domain.Allocation
import dev.boar.checktime.domain.PeriodKind
import dev.boar.checktime.domain.StatsPeriod
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
class StatsViewModelTest {
    @get:Rule val mainDispatcher = MainDispatcherRule()

    private val context: Application = ApplicationProvider.getApplicationContext()
    private val zone = ZoneId.of("Europe/Moscow")
    private val today = LocalDate.of(2026, 9, 16) // среда
    private val dayStart = dayRange(today, zone).start
    private var now = dayStart + 12 * 60 * MINUTE_MS
    private lateinit var container: AppContainer
    private var sleep = 0L
    private var work = 0L

    @Before fun setUp() = runTest {
        container = testContainer(context) { now }
        val personal = container.categories.addGroup("Личное", 1)
        sleep = container.categories.addCategory(personal, "Сон", 2)
        val job = container.categories.addGroup("Работа", 3)
        work = container.categories.addCategory(job, "Работа", 4)
    }

    @After fun tearDown() = container.db.close()

    private fun vm() = StatsViewModel(container, zone)

    /** 60 мин сна и 30 мин работы сегодня, плюс 20 мин работы вчера. */
    private suspend fun seed() {
        val yesterday = dayRange(today.minusDays(1), zone).start + 10 * 60 * MINUTE_MS
        container.timeline.startTracking(yesterday)
        container.timeline.allocate(yesterday, listOf(Allocation(work, 20)))
        val todayStart = dayStart + 8 * 60 * MINUTE_MS
        container.db.segmentDao().insertAll(
            listOf(
                dev.boar.checktime.data.Segment(startAt = todayStart, endAt = todayStart + 60 * MINUTE_MS, categoryId = sleep),
                dev.boar.checktime.data.Segment(startAt = todayStart + 60 * MINUTE_MS, endAt = todayStart + 90 * MINUTE_MS, categoryId = work),
            ),
        )
    }

    @Test fun defaultPeriodIsToday() = runTest {
        seed()
        val s = vm().state.first { it.result.totalMillis > 0 }
        assertEquals(StatsPeriod.Day(today), s.period)
        assertEquals(PeriodKind.DAY, s.kind)
        assertEquals(90 * MINUTE_MS, s.result.totalMillis)
        assertEquals(listOf("Сон", "Работа"), s.result.categories.map { it.category.name })
        assertFalse(s.canGoForward)
        assertEquals(1, s.dayCount)
    }

    @Test fun weekIncludesYesterday() = runTest {
        seed()
        val vm = vm()
        vm.state.first { it.result.totalMillis > 0 }
        vm.selectKind(PeriodKind.WEEK)
        val s = vm.state.first { it.kind == PeriodKind.WEEK && it.result.totalMillis > 0 }
        assertEquals(StatsPeriod.Week(LocalDate.of(2026, 9, 14)), s.period)
        assertEquals(110 * MINUTE_MS, s.result.totalMillis)
        assertEquals(7, s.dayCount)
    }

    @Test fun previousDayShowsItsOwnDataAndAllowsGoingForward() = runTest {
        seed()
        val vm = vm()
        vm.state.first { it.result.totalMillis > 0 }
        vm.previous()
        val s = vm.state.first { it.period == StatsPeriod.Day(today.minusDays(1)) }
        assertEquals(20 * MINUTE_MS, s.result.totalMillis)
        assertTrue(s.canGoForward)
        vm.next()
        assertEquals(StatsPeriod.Day(today), vm.state.first { it.period == StatsPeriod.Day(today) }.period)
    }

    @Test fun nextIsIgnoredWhenPeriodAlreadyCoversToday() = runTest {
        seed()
        val vm = vm()
        vm.state.first { it.result.totalMillis > 0 }
        vm.next()
        assertEquals(StatsPeriod.Day(today), vm.state.value.period)
    }

    @Test fun customRangeAggregatesBothDays() = runTest {
        seed()
        val vm = vm()
        vm.state.first { it.result.totalMillis > 0 }
        vm.selectCustom(today.minusDays(1), today)
        val s = vm.state.first { it.kind == null }
        assertEquals(StatsPeriod.Custom(today.minusDays(1), today), s.period)
        assertEquals(110 * MINUTE_MS, s.result.totalMillis)
        assertEquals(2, s.dayCount)
    }
}
