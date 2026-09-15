package dev.boar.checktime.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import dev.boar.checktime.AppContainer
import dev.boar.checktime.appContainer
import dev.boar.checktime.domain.TimeMath
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Общий код для ресиверов: держит процесс живым, пока handle() работает. */
private fun BroadcastReceiver.handleAsync(context: Context) {
    val pending = goAsync()
    val container = context.appContainer
    container.applicationScope.launch {
        try {
            AlarmReceiver.handle(context, container)
        } finally {
            pending.finish()
        }
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = handleAsync(context)

    companion object {
        /**
         * Вызывается по будильнику, после загрузки и после обновления приложения.
         * 1. Учёт не начат — ничего. Хвост < 1 мин — снять уведомление, будильник на accountedUntil + N.
         * 2. Иначе — уведомление с fullScreenIntent, страховочный будильник через snooze
         *    (если экран не откроется или его проигнорируют) и прямой запуск экрана, когда есть overlay.
         */
        suspend fun handle(context: Context, container: AppContainer) {
            val state = container.timeline.trackingState() ?: return
            val settings = container.settings.settings.first()
            val now = container.now()
            val tail = TimeMath.tailMinutes(state.accountedUntil, now)
            if (tail < 1) {
                PendingNotification.cancel(context)
                container.alarms.schedule(TimeMath.nextAlarmAt(state.accountedUntil, settings.intervalMinutes))
                return
            }
            PendingNotification.show(context, tail, urgent = true)
            container.alarms.schedule(TimeMath.snoozeAlarmAt(now, settings.snoozeMinutes))
            if (Settings.canDrawOverlays(context)) {
                context.startActivity(PendingNotification.allocationIntent(context))
            }
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = handleAsync(context)
}
