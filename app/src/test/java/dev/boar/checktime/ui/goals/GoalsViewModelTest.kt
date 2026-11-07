package dev.boar.checktime.ui.goals

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import dev.boar.checktime.AppContainer
import dev.boar.checktime.MainDispatcherRule
import dev.boar.checktime.data.Segment
import dev.boar.checktime.domain.GoalTarget
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
class GoalsViewModelTest {
    @get:Rule val mainDispatcher = MainDispatcherRule()

    private val context: Application = ApplicationProvider.getApplicationContext()
    private val zone = ZoneId.of("Europe/Moscow")
    private val today = LocalDate.of(2026, 9, 16) // среда
    private val monday = LocalDate.of(2026, 9, 14)
    private var now = dayRange(today, zone).start + 12 * 60 * MINUTE_MS
    private lateinit var container: AppContainer
    private var personal = 0L
    private var sport = 0L
    private var archived = 0L

    @Before fun setUp() = runTest {
        container = testContainer(context) { now }
        personal = container.categories.addGroup("Личное", 1)
        sport = container.categories.addCategory(personal, "Спорт", 2)
        archived = container.categories.addCategory(personal, "Старое", 3)
        val old = container.db.categoryDao().categoriesOfGroup(personal).first { it.id == archived }
        container.categories.updateCategory(old.copy(archived = true))
    }

    @After fun tearDown() = container.db.close()

    private fun vm() = GoalsViewModel(container, zone)

    private suspend fun insertSegment(date: LocalDate, minutes: Long, categoryId: Long) {
        val start = dayRange(date, zone).start + 8 * 60 * MINUTE_MS
        container.db.segmentDao().insertAll(listOf(Segment(startAt = start, endAt = start + minutes * MINUTE_MS, categoryId = categoryId)))
    }

    @Test fun currentWeekShowsProgressFromMonday() = runTest {
        container.goals.set(GoalTarget.OfCategory(sport), 120, monday)
        insertSegment(monday, 30, sport)
        insertSegment(today, 45, sport)
        insertSegment(monday.minusDays(1), 600, sport) // воскресенье прошлой недели
        val s = vm().state.first { it.goals.isNotEmpty() && it.goals.single().doneMillis > 0 }
        assertEquals(StatsPeriod.Week(monday), s.week)
        assertTrue(s.isCurrentWeek)
        assertEquals(75 * MINUTE_MS, s.goals.single().doneMillis)
    }

    @Test fun candidatesSkipArchivedAndTakenTargets() = runTest {
        container.goals.set(GoalTarget.OfCategory(sport), 120, monday)
        val s = vm().state.first { it.goals.isNotEmpty() }
        assertEquals(listOf(GoalTarget.OfGroup(personal)), s.candidates.map { it.target })
    }

    @Test fun pastWeekIsReadOnlyAndForwardStopsAtCurrent() = runTest {
        container.goals.set(GoalTarget.OfCategory(sport), 120, monday.minusWeeks(1))
        container.goals.set(GoalTarget.OfCategory(sport), 0, monday)
        val vm = vm()
        vm.state.first { it.isCurrentWeek && it.candidates.isNotEmpty() }
        vm.next()
        assertEquals(StatsPeriod.Week(monday), vm.state.first { it.candidates.isNotEmpty() }.week)

        vm.previous()
        val past = vm.state.first { it.week.monday == monday.minusWeeks(1) && it.goals.isNotEmpty() }
        assertFalse(past.isCurrentWeek)
        assertTrue(past.candidates.isEmpty())
        assertEquals(120, past.goals.single().goalMinutes)
    }

    @Test fun setAndRemoveGoalApplyToCurrentWeek() = runTest {
        val vm = vm()
        vm.state.first { it.candidates.isNotEmpty() }
        vm.setGoal(GoalTarget.OfGroup(personal), 600)
        assertEquals(600, vm.state.first { it.goals.isNotEmpty() }.goals.single().goalMinutes)
        assertEquals(monday.toEpochDay(), container.goals.observeAll().first().single().validFrom)
        vm.removeGoal(GoalTarget.OfGroup(personal))
        vm.state.first { it.goals.isEmpty() }
        assertTrue(container.goals.observeAll().first().isEmpty())
    }

    @Test fun currentWeekFollowsClockPastMondayMidnight() = runTest {
        now = dayRange(LocalDate.of(2026, 9, 20), zone).end - MINUTE_MS // воскресенье 23:59
        val vm = vm()
        assertEquals(StatsPeriod.Week(monday), vm.state.first { it.candidates.isNotEmpty() }.week)

        now += 2 * MINUTE_MS // понедельник 00:01
        val s = vm.state.first { it.week.monday == monday.plusWeeks(1) }
        assertTrue(s.isCurrentWeek)
        assertTrue(s.candidates.isNotEmpty())
    }

    @Test fun pastWeekStaysPastAfterMondayMidnight() = runTest {
        now = dayRange(LocalDate.of(2026, 9, 20), zone).end - MINUTE_MS
        val vm = vm()
        vm.state.first { it.candidates.isNotEmpty() }
        vm.previous()
        vm.state.first { it.week.monday == monday.minusWeeks(1) }

        now += 2 * MINUTE_MS
        val s = vm.state.first { it.week.monday == monday }
        assertFalse(s.isCurrentWeek)
    }
}
