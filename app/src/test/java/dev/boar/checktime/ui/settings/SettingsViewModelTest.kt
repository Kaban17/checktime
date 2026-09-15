package dev.boar.checktime.ui.settings

import android.app.AlarmManager
import android.app.Application
import androidx.test.core.app.ApplicationProvider
import dev.boar.checktime.AppContainer
import dev.boar.checktime.MainDispatcherRule
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
        assertEquals(now, s.tracking!!.accountedUntil)
        assertEquals(now + 30 * MINUTE_MS, shadowOf(alarmManager).peekNextScheduledAlarm()!!.triggerAtTime)
    }

    @Test fun changingIntervalReschedulesAlarm() = runTest {
        vm.startTracking().join()
        vm.setInterval(15).join()
        assertEquals(15, vm.state.first { it?.settings?.intervalMinutes == 15 }!!.settings.intervalMinutes)
        assertEquals(now + 15 * MINUTE_MS, shadowOf(alarmManager).peekNextScheduledAlarm()!!.triggerAtTime)
    }

    @Test fun changingIntervalBeforeStartDoesNotArm() = runTest {
        vm.setInterval(15).join()
        assertNull(shadowOf(alarmManager).peekNextScheduledAlarm())
    }
}
