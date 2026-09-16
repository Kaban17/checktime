package dev.boar.checktime.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

/** Ровно один будильник: каждый schedule() заменяет предыдущий. */
class AlarmScheduler(private val context: Context) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    private fun operation(): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        Intent(context, AlarmReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    fun schedule(atMillis: Long) {
        val op = operation()
        val exactAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
        if (exactAllowed) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, op)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, op)
        }
    }

    fun cancel() {
        val op = operation()
        alarmManager.cancel(op)
        op.cancel()
    }

    /** true, если наш PendingIntent существует — после force-stop / очистки система его снимает вместе с будильником. */
    fun isScheduled(): Boolean = PendingIntent.getBroadcast(
        context, REQUEST_CODE, Intent(context, AlarmReceiver::class.java),
        PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
    ) != null

    private companion object {
        const val REQUEST_CODE = 100
    }
}
