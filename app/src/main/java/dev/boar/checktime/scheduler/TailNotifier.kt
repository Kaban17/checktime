package dev.boar.checktime.scheduler

import android.content.Context
import dev.boar.checktime.AppContainer
import dev.boar.checktime.domain.TimeMath

/** Приводит уведомление в соответствие с текущим хвостом (вызывается при заходе в приложение и после сохранения). */
object TailNotifier {
    suspend fun sync(context: Context, container: AppContainer) {
        val state = container.timeline.trackingState()
        val tail = state?.let { TimeMath.tailMinutes(it.accountedUntil, container.now()) } ?: 0
        if (tail >= 1) PendingNotification.show(context, tail, urgent = false) else PendingNotification.cancel(context)
    }
}
