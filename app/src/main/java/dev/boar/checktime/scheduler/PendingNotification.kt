package dev.boar.checktime.scheduler

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import dev.boar.checktime.R
import dev.boar.checktime.ui.common.formatDuration

/** Постоянное уведомление «Не расписано: X мин». Тап и fullScreenIntent открывают экран распределения. */
object PendingNotification {
    const val ID = 1
    const val ACTION_ALLOCATE = "dev.boar.checktime.action.ALLOCATE"
    private const val CHANNEL_ALARM = "allocation_alarm"
    private const val CHANNEL_TAIL = "tail"

    /** Неявный intent внутри пакета: планировщик не зависит от класса активности. */
    fun allocationIntent(context: Context): Intent =
        Intent(ACTION_ALLOCATE).setPackage(context.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ALARM, context.getString(R.string.channel_alarm), NotificationManager.IMPORTANCE_HIGH),
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_TAIL, context.getString(R.string.channel_tail), NotificationManager.IMPORTANCE_LOW),
        )
    }

    /** [urgent] — по будильнику: канал HIGH + fullScreenIntent. Иначе тихое обновление. */
    fun show(context: Context, tailMinutes: Int, urgent: Boolean) {
        val content = PendingIntent.getActivity(
            context, 0, allocationIntent(context),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = Notification.Builder(context, if (urgent) CHANNEL_ALARM else CHANNEL_TAIL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notification_title, formatDuration(tailMinutes)))
            .setContentText(context.getString(R.string.notification_text))
            .setContentIntent(content)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_REMINDER)
        if (urgent) builder.setFullScreenIntent(content, true)
        context.getSystemService(NotificationManager::class.java).notify(ID, builder.build())
    }

    fun cancel(context: Context) = context.getSystemService(NotificationManager::class.java).cancel(ID)
}
