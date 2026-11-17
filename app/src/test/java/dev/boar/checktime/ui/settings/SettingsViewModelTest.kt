package dev.boar.checktime.ui.settings

import android.app.AlarmManager
import android.app.Application
import androidx.test.core.app.ApplicationProvider
import dev.boar.checktime.AppContainer
import dev.boar.checktime.MainDispatcherRule
import dev.boar.checktime.data.Segment
import dev.boar.checktime.domain.TimeMath.MINUTE_MS
import dev.boar.checktime.testContainer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.io.ByteArrayOutputStream
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
class SettingsViewModelTest {
    @get:Rule val mainDispatcher = MainDispatcherRule()

    private val context: Application = ApplicationProvider.getApplicationContext()
    private val alarmManager = context.getSystemService(AlarmManager::class.java)
    private var now = 1_000_000L
    private lateinit var container: AppContainer
    private lateinit var vm: SettingsViewModel

    @Before fun setUp() {
        container = testContainer(context) { now }
        vm = SettingsViewModel(container)
    }

    @After fun tearDown() = container.db.close()

    @Test fun stateExposesDefaultsAndNoTracking() = runTest {
        val s = vm.state.first { it != null }!!
        assertEquals(30, s.settings.intervalMinutes)
        assertNull(s.tracking)
    }

    @Test fun startTrackingSetsStateAndArmsFirstAlarm() = runTest {
        vm.startTracking().join()
        val s = vm.state.first { it?.tracking != null }!!
        val aligned = now - now % MINUTE_MS
        assertEquals(aligned, s.tracking!!.accountedUntil)
        assertEquals(aligned + 30 * MINUTE_MS, shadowOf(alarmManager).peekNextScheduledAlarm()!!.triggerAtTime)
    }

    @Test fun changingIntervalReschedulesAlarm() = runTest {
        vm.startTracking().join()
        vm.setInterval(15).join()
        assertEquals(15, vm.state.first { it?.settings?.intervalMinutes == 15 }!!.settings.intervalMinutes)
        val aligned = now - now % MINUTE_MS
        assertEquals(aligned + 15 * MINUTE_MS, shadowOf(alarmManager).peekNextScheduledAlarm()!!.triggerAtTime)
    }

    @Test fun changingIntervalBeforeStartDoesNotArm() = runTest {
        vm.setInterval(15).join()
        assertNull(shadowOf(alarmManager).peekNextScheduledAlarm())
    }

    @Test fun changingIntervalNeverArmsInThePast() = runTest {
        vm.startTracking().join()
        now += 50 * MINUTE_MS
        vm.setInterval(15).join()
        assertEquals(now + MINUTE_MS, shadowOf(alarmManager).peekNextScheduledAlarm()!!.triggerAtTime)
    }

    @Test fun exportWritesAllSegmentsAsCsv() = runTest {
        val group = container.categories.addGroup("Личное", 1)
        val sleep = container.categories.addCategory(group, "Сон", 2)
        container.db.segmentDao().insertAll(
            listOf(Segment(startAt = 0, endAt = 30 * MINUTE_MS, categoryId = sleep), Segment(startAt = 30 * MINUTE_MS, endAt = 45 * MINUTE_MS, categoryId = sleep)),
        )
        val out = ByteArrayOutputStream()
        var result: Int? = -1
        SettingsViewModel(container, ZoneId.of("UTC")).exportCsv({ out }) { result = it }.join()
        assertEquals(2, result)
        val lines = out.toString(Charsets.UTF_8).removePrefix("\uFEFF").trimEnd().split("\r\n")
        assertEquals(listOf("date,start,end,minutes,group,category", "1970-01-01,00:00,00:30,30,Личное,Сон", "1970-01-01,00:30,00:45,15,Личное,Сон"), lines)
    }

    @Test fun exportReportsFailureWhenStreamDoesNotOpen() = runTest {
        var result: Int? = -1
        vm.exportCsv({ null }) { result = it }.join()
        assertNull(result)
    }

    @Test fun exportFileNameUsesToday() =
        assertEquals("checktime-1970-01-01.csv", SettingsViewModel(container, ZoneId.of("UTC")).exportFileName())
}
