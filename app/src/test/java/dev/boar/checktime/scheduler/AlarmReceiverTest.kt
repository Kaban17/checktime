package dev.boar.checktime.scheduler

import android.app.AlarmManager
import android.app.Application
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import dev.boar.checktime.AppContainer
import dev.boar.checktime.domain.TimeMath.MINUTE_MS
import dev.boar.checktime.testContainer
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowSettings

@RunWith(RobolectricTestRunner::class)
class AlarmReceiverTest {
    private val context: Application = ApplicationProvider.getApplicationContext()
    private val notifications = context.getSystemService(NotificationManager::class.java)
    private val alarmManager = context.getSystemService(AlarmManager::class.java)
    private var now = 0L
    private lateinit var container: AppContainer

    @Before fun setUp() {
        container = testContainer(context) { now }
        PendingNotification.createChannels(context)
        ShadowSettings.setCanDrawOverlays(true)
    }

    @After fun tearDown() = container.db.close()

    @Test fun beforeTrackingStartDoesNothing() = runTest {
        AlarmReceiver.handle(context, container)
        assertNull(shadowOf(notifications).getNotification(PendingNotification.ID))
        assertNull(shadowOf(alarmManager).peekNextScheduledAlarm())
        assertNull(shadowOf(context).nextStartedActivity)
        assertNull(shadowOf(context).nextStartedService)
    }

    @Test fun withTailNotifiesStartsWatcherAndArmsNextInterval() = runTest {
        container.timeline.startTracking(now = 0)
        now = 47 * MINUTE_MS
        AlarmReceiver.handle(context, container)

        assertNotNull(shadowOf(notifications).getNotification(PendingNotification.ID))
        // попап по будильнику не запускается
        assertNull(shadowOf(context).nextStartedActivity)
        assertEquals(UnlockWatcherService::class.java.name, shadowOf(context).nextStartedService.component!!.className)
        // следующий будильник — через interval (30 мин по умолчанию), а не snooze
        assertEquals(now + 30 * MINUTE_MS, shadowOf(alarmManager).peekNextScheduledAlarm()!!.triggerAtTime)
    }

    @Test fun withoutOverlayPermissionStillNotifiesAndStartsWatcher() = runTest {
        ShadowSettings.setCanDrawOverlays(false)
        container.timeline.startTracking(now = 0)
        now = 5 * MINUTE_MS
        AlarmReceiver.handle(context, container)
        assertNotNull(shadowOf(notifications).getNotification(PendingNotification.ID))
        assertNull(shadowOf(context).nextStartedActivity)
        assertNotNull(shadowOf(context).nextStartedService)
    }

    @Test fun withoutTailCancelsNotificationAndArmsNextInterval() = runTest {
        container.timeline.startTracking(now = 0)
        now = 30_000
        PendingNotification.show(context, 5, urgent = false)
        AlarmReceiver.handle(context, container)
        assertNull(shadowOf(notifications).getNotification(PendingNotification.ID))
        assertNull(shadowOf(context).nextStartedActivity)
        assertNull(shadowOf(context).nextStartedService)
        // interval по умолчанию 30 мин, accountedUntil = 0
        assertEquals(30 * MINUTE_MS, shadowOf(alarmManager).peekNextScheduledAlarm()!!.triggerAtTime)
    }

    @Test fun tailNotifierSyncShowsAndHides() = runTest {
        container.timeline.startTracking(now = 0)
        now = 3 * MINUTE_MS
        TailNotifier.sync(context, container)
        assertNotNull(shadowOf(notifications).getNotification(PendingNotification.ID))
        now = 30_000
        TailNotifier.sync(context, container)
        assertNull(shadowOf(notifications).getNotification(PendingNotification.ID))
    }

    @Test fun tailNotifierReArmsMissingAlarm() = runTest {
        container.timeline.startTracking(now = 0)
        now = 3 * MINUTE_MS
        assertNull(shadowOf(alarmManager).peekNextScheduledAlarm())
        TailNotifier.sync(context, container)
        // accountedUntil + 30 мин в будущем — ставим именно его
        assertEquals(30 * MINUTE_MS, shadowOf(alarmManager).peekNextScheduledAlarm()!!.triggerAtTime)
    }

    @Test fun tailNotifierDoesNotArmInThePast() = runTest {
        container.timeline.startTracking(now = 0)
        now = 45 * MINUTE_MS
        TailNotifier.sync(context, container)
        assertEquals(now + MINUTE_MS, shadowOf(alarmManager).peekNextScheduledAlarm()!!.triggerAtTime)
    }

    @Test fun tailNotifierKeepsExistingAlarm() = runTest {
        container.timeline.startTracking(now = 0)
        container.alarms.schedule(7 * MINUTE_MS)
        now = 3 * MINUTE_MS
        TailNotifier.sync(context, container)
        assertEquals(7 * MINUTE_MS, shadowOf(alarmManager).peekNextScheduledAlarm()!!.triggerAtTime)
    }
}
