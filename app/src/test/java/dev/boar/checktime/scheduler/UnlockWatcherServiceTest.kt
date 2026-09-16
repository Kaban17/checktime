package dev.boar.checktime.scheduler

import android.app.Application
import android.app.NotificationManager
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import dev.boar.checktime.appContainer
import dev.boar.checktime.domain.TimeMath.MINUTE_MS
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController
import org.robolectric.shadows.ShadowLooper
import org.robolectric.shadows.ShadowSettings

/** Сервис берёт контейнер из Application — под Robolectric это TestCheckTimeApp с in-memory базой. */
@RunWith(RobolectricTestRunner::class)
class UnlockWatcherServiceTest {
    private val context: Application = ApplicationProvider.getApplicationContext()
    private val notifications = context.getSystemService(NotificationManager::class.java)
    private lateinit var controller: ServiceController<UnlockWatcherService>

    @Before fun setUp() {
        PendingNotification.createChannels(context)
        ShadowSettings.setCanDrawOverlays(true)
        controller = Robolectric.buildService(UnlockWatcherService::class.java, UnlockWatcherService.intent(context))
    }

    /**
     * onUnlocked() суспендится на реальном (не тестовом) IO-диспетчере Room, поэтому один
     * idleMainLooper() сразу после sendBroadcast может выполниться раньше, чем фоновый поток
     * положит продолжение обратно в очередь главного луппера — крутим с таймаутом.
     */
    private fun unlock() {
        context.sendBroadcast(Intent(Intent.ACTION_USER_PRESENT))
        awaitCondition { shadowOf(controller.get()).isStoppedBySelf }
    }

    /** Тот же приём, что и в unlock(): последующая работа может уйти на реальный IO-диспетчер. */
    private fun awaitCondition(timeoutMs: Long = 500, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            ShadowLooper.idleMainLooper()
            if (condition()) return
            Thread.sleep(2)
        }
    }

    @Test fun startsPopupOnUnlockWhenTailIsDueAndStops() = runTest {
        val container = context.appContainer
        container.timeline.startTracking(now = 0)
        // testContainer в TestCheckTimeApp использует System.currentTimeMillis → хвост огромный, ≥ 1 мин
        controller.create().startCommand(0, 1)
        assertNull(shadowOf(context).nextStartedActivity)

        unlock()

        assertEquals(PendingNotification.ACTION_ALLOCATE, shadowOf(context).nextStartedActivity.action)
        assertTrue(shadowOf(controller.get()).isStoppedBySelf)
    }

    @Test fun withoutTrackingUnlockJustStops() = runTest {
        controller.create().startCommand(0, 1)
        unlock()
        assertNull(shadowOf(context).nextStartedActivity)
        assertTrue(shadowOf(controller.get()).isStoppedBySelf)
    }

    @Test fun withoutOverlayUnlockJustStops() = runTest {
        ShadowSettings.setCanDrawOverlays(false)
        context.appContainer.timeline.startTracking(now = 0)
        controller.create().startCommand(0, 1)
        unlock()
        assertNull(shadowOf(context).nextStartedActivity)
        assertTrue(shadowOf(controller.get()).isStoppedBySelf)
    }

    @Test fun destroyUnregistersReceiver() = runTest {
        controller.create().startCommand(0, 1).destroy()
        unlock() // не должно упасть и ничего не запускает
        assertNull(shadowOf(context).nextStartedActivity)
    }

    @Test fun destroyWithTailRepostsNotification() = runTest {
        context.appContainer.timeline.startTracking(now = 0)
        // testContainer в TestCheckTimeApp использует System.currentTimeMillis → хвост огромный, ≥ 1 мин
        controller.create().startCommand(0, 1)
        controller.destroy()

        awaitCondition { shadowOf(notifications).getNotification(PendingNotification.ID) != null }

        assertNotNull(shadowOf(notifications).getNotification(PendingNotification.ID))
    }

    @Test fun destroyWithoutTailLeavesNoNotification() = runTest {
        controller.create().startCommand(0, 1)
        controller.destroy()

        // Уведомление снимается синхронно в onDestroy() (STOP_FOREGROUND_REMOVE); последующий
        // TailNotifier.sync на реальном диспетчере не должен успеть выставить его заново.
        awaitCondition(timeoutMs = 200) { false }

        assertNull(shadowOf(notifications).getNotification(PendingNotification.ID))
    }
}
