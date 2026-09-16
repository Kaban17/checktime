package dev.boar.checktime.scheduler

import android.content.Context
import dev.boar.checktime.AppContainer
import dev.boar.checktime.domain.TimeMath
import kotlinx.coroutines.flow.first

/** Приводит уведомление и будильник в соответствие с реальностью (вызывается при заходе в приложение и после сохранения). */
object TailNotifier {
    suspend fun sync(context: Context, container: AppContainer) {
        val state = container.timeline.trackingState()
        if (state == null) {
            PendingNotification.cancel(context)
            return
        }
        val now = container.now()
        val tail = TimeMath.tailMinutes(state.accountedUntil, now)
        if (tail >= 1) PendingNotification.show(context, tail, urgent = false) else PendingNotification.cancel(context)
        if (!container.alarms.isScheduled()) {
            val settings = container.settings.settings.first()
            // Потерянный будильник (force-stop, OEM-киллер): не в прошлое, чтобы не сработать поверх открытого экрана.
            container.alarms.schedule(
                maxOf(TimeMath.nextAlarmAt(state.accountedUntil, settings.intervalMinutes), now + TimeMath.MINUTE_MS),
            )
        }
    }
}
