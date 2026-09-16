package dev.boar.checktime.scheduler

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import dev.boar.checktime.appContainer
import dev.boar.checktime.domain.TimeMath
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * «Попап назрел»: живёт с момента будильника до разблокировки телефона.
 * Foreground-уведомление — то же «Не расписано» (id 1), в шторке ничего лишнего.
 * По ACTION_USER_PRESENT запускает экран распределения (если хвост ≥ 1 мин и есть
 * overlay) и останавливается. Если процесс убьют — следующий будильник запустит заново.
 */
class UnlockWatcherService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val unlockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_USER_PRESENT) onUnlocked()
        }
    }

    override fun onCreate() {
        super.onCreate()
        ContextCompat.registerReceiver(
            this,
            unlockReceiver,
            IntentFilter(Intent.ACTION_USER_PRESENT),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val tail = intent?.getIntExtra(EXTRA_TAIL_MINUTES, 0) ?: 0
        ServiceCompat.startForeground(
            this,
            PendingNotification.ID,
            PendingNotification.build(this, tail, urgent = false),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
        )
        return START_NOT_STICKY
    }

    private fun onUnlocked() {
        scope.launch {
            try {
                val container = appContainer
                val state = container.timeline.trackingState()
                val tail = state?.let { TimeMath.tailMinutes(it.accountedUntil, container.now()) } ?: 0
                if (tail >= 1 && Settings.canDrawOverlays(this@UnlockWatcherService)) {
                    startActivity(PendingNotification.allocationIntent(this@UnlockWatcherService))
                }
            } catch (e: Exception) {
                Log.e(TAG, "unlock handling failed", e)
            } finally {
                stopSelf()
            }
        }
    }

    override fun onDestroy() {
        unregisterReceiver(unlockReceiver)
        scope.cancel()
        // Уведомление не снимаем: пока хвост есть, оно должно висеть (см. TailNotifier).
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_DETACH)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "UnlockWatcher"
        private const val EXTRA_TAIL_MINUTES = "tail_minutes"

        fun intent(context: Context, tailMinutes: Int = 0): Intent =
            Intent(context, UnlockWatcherService::class.java).putExtra(EXTRA_TAIL_MINUTES, tailMinutes)

        fun start(context: Context, tailMinutes: Int) =
            ContextCompat.startForegroundService(context, intent(context, tailMinutes))

        fun stop(context: Context) {
            context.stopService(intent(context))
        }
    }
}
