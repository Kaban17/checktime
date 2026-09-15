package dev.boar.checktime.ui.common

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/** «47 мин», «2 ч», «1 ч 05 мин». Единицы захардкожены: функция нужна и без Context (уведомление). */
fun formatDuration(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h == 0 -> "$m мин"
        m == 0 -> "$h ч"
        else -> "$h ч ${m.toString().padStart(2, '0')} мин"
    }
}

fun formatDurationMs(millis: Long): String = formatDuration((millis / 60_000L).toInt())

fun formatTime(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
    Instant.ofEpochMilli(epochMillis).atZone(zone).format(timeFormatter)
