package dev.boar.checktime.ui.allocation

import android.app.AlarmManager
import android.app.Application
import androidx.test.core.app.ApplicationProvider
import dev.boar.checktime.AppContainer
import dev.boar.checktime.MainDispatcherRule
import dev.boar.checktime.domain.Allocation
import dev.boar.checktime.domain.TimeMath.MINUTE_MS
import dev.boar.checktime.scheduler.PendingNotification
import dev.boar.checktime.scheduler.UnlockWatcherService
import dev.boar.checktime.testContainer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class AllocationViewModelTest {
    @get:Rule val mainDispatcher = MainDispatcherRule()

    private val context: Application = ApplicationProvider.getApplicationContext()
    private val alarmManager = context.getSystemService(AlarmManager::class.java)
    private var now = 0L
    private lateinit var container: AppContainer
    private var sleep = 0L
    private var food = 0L

    @Before fun setUp() = runTest {
        container = testContainer(context) { now }
        PendingNotification.createChannels(context)
        val g = container.categories.addGroup("Личное", 0)
        sleep = container.categories.addCategory(g, "Сон", 0)
        food = container.categories.addCategory(g, "Еда", 0)
        val archived = container.categories.addCategory(g, "Архив", 0)
        container.categories.updateCategory(container.categories.observeTree().first()[0].categories.first { it.id == archived }.copy(archived = true))
    }

    @After fun tearDown() = container.db.close()

    private fun vm() = AllocationViewModel(context, container)
    private suspend fun AllocationViewModel.ready() = state.first { it !is AllocationUiState.Loading } as AllocationUiState.Ready

    @Test fun emptyWhenTrackingNotStarted() = runTest {
        assertEquals(AllocationUiState.Empty, vm().state.first { it !is AllocationUiState.Loading })
    }

    @Test fun emptyWhenTailUnderMinute() = runTest {
        container.timeline.startTracking(0)
        now = 59_000
        assertEquals(AllocationUiState.Empty, vm().state.first { it !is AllocationUiState.Loading })
    }

    @Test fun readyStateHasTailAndActiveCategoriesOnly() = runTest {
        container.timeline.startTracking(0)
        now = 47 * MINUTE_MS + 30_000
        val s = vm().ready()
        assertEquals(0L, s.start)
        assertEquals(47 * MINUTE_MS, s.end)
        assertEquals(47, s.draft.totalMinutes)
        assertEquals(5, s.draft.stepMinutes)
        assertEquals(listOf(sleep, food), s.draft.order)
        assertEquals(listOf("Сон", "Еда"), s.groups[0].categories.map { it.name })
    }

    @Test fun saveWritesSegmentsSchedulesNextAlarmAndEmitsSaved() = runTest {
        container.timeline.startTracking(0)
        now = 30 * MINUTE_MS
        val vm = vm()
        vm.ready()
        vm.edit { it.increment(sleep).assignRest(food) }
        vm.save()
        assertEquals(AllocationEvent.Saved, vm.events.first())

        val segs = container.db.segmentDao().all()
        assertEquals(listOf(sleep to 5 * MINUTE_MS, food to 25 * MINUTE_MS), segs.map { it.categoryId to (it.endAt - it.startAt) })
        assertEquals(30 * MINUTE_MS, container.timeline.trackingState()!!.accountedUntil)
        // следующий будильник: новая граница + interval (30 мин по умолчанию)
        assertEquals(60 * MINUTE_MS, shadowOf(alarmManager).peekNextScheduledAlarm()!!.triggerAtTime)
        assertEquals(UnlockWatcherService::class.java.name, shadowOf(context).nextStoppedService.component!!.className)
    }

    @Test fun saveIsIgnoredWhileIncomplete() = runTest {
        container.timeline.startTracking(0)
        now = 30 * MINUTE_MS
        val vm = vm()
        vm.ready()
        vm.edit { it.increment(sleep) }
        vm.save()
        assertTrue(container.db.segmentDao().all().isEmpty())
    }

    @Test fun staleSaveEmitsStaleAndReloads() = runTest {
        container.timeline.startTracking(0)
        now = 30 * MINUTE_MS
        val vm = vm()
        vm.ready()
        vm.edit { it.assignRest(sleep) }
        // кто-то распределил хвост параллельно
        container.timeline.allocate(0, listOf(Allocation(food, 10)))
        now = 40 * MINUTE_MS
        vm.save()
        assertEquals(AllocationEvent.Stale, vm.events.first())
        val reloaded = vm.state.first { it is AllocationUiState.Ready && it.start == 10 * MINUTE_MS } as AllocationUiState.Ready
        assertEquals(30, reloaded.draft.totalMinutes)
    }

    @Test fun postponeArmsSnoozeAndEmitsPostponed() = runTest {
        container.timeline.startTracking(0)
        now = 30 * MINUTE_MS
        val vm = vm()
        vm.ready()
        vm.postpone()
        assertEquals(AllocationEvent.Postponed, vm.events.first())
        assertEquals(now + 10 * MINUTE_MS, shadowOf(alarmManager).peekNextScheduledAlarm()!!.triggerAtTime)
        assertEquals(UnlockWatcherService::class.java.name, shadowOf(context).nextStoppedService.component!!.className)
    }
}
