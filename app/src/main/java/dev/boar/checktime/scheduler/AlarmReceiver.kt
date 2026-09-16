package dev.boar.checktime.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
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
        } catch (e: Exception) {
            Log.e("AlarmReceiver", "handle failed", e)
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
         * Попап никогда не показывается сам по будильнику и не включает экран.
         * 1. Учёт не начат — ничего. Хвост < 1 мин — снять уведомление, будильник на accountedUntil + N.
         * 2. Иначе — уведомление «Не расписано», следующий будильник через интервал и запуск
         *    UnlockWatcherService, который покажет попап при разблокировке телефона.
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
            // Следующее напоминание — через интервал; попап покажет UnlockWatcherService при разблокировке.
            container.alarms.schedule(now + settings.intervalMinutes * TimeMath.MINUTE_MS)
            UnlockWatcherService.start(context, tail)
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = handleAsync(context)
}
