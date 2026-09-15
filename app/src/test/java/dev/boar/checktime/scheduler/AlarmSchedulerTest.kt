package dev.boar.checktime.scheduler

import android.app.AlarmManager
import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class AlarmSchedulerTest {
    private val context: Application = ApplicationProvider.getApplicationContext()
    private val alarmManager = context.getSystemService(AlarmManager::class.java)
    private val scheduler = AlarmScheduler(context)

    @Test fun scheduleReplacesPreviousAlarm() {
        scheduler.schedule(1_000)
        scheduler.schedule(2_000)
        val shadow = shadowOf(alarmManager)
        assertEquals(1, shadow.scheduledAlarms.size)
        assertEquals(2_000L, shadow.peekNextScheduledAlarm()!!.triggerAtTime)
        assertEquals(AlarmManager.RTC_WAKEUP, shadow.peekNextScheduledAlarm()!!.type)
    }

    @Test fun cancelRemovesAlarm() {
        scheduler.schedule(1_000)
        scheduler.cancel()
        assertNull(shadowOf(alarmManager).peekNextScheduledAlarm())
    }
}
