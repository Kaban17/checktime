package dev.boar.checktime.ui.common

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/** Язык интерфейса: русский для ru, иначе английский (как ресурсы `values-ru` / `values`). */
fun isRussian(locale: Locale = Locale.getDefault()): Boolean = locale.language == "ru"

/** Шаблон даты под язык: порядок дня и месяца у ru и en разный. */
fun datePattern(ru: String, en: String, locale: Locale = Locale.getDefault()): DateTimeFormatter =
    DateTimeFormatter.ofPattern(if (isRussian(locale)) ru else en, locale)

/**
 * «47 мин», «2 ч», «1 ч 05 мин» (en: «47 min», «2 h», «1 h 05 min»). Единицы не в ресурсах:
 * функция нужна и без Context (уведомление).
 */
fun formatDuration(minutes: Int, locale: Locale = Locale.getDefault()): String {
    val (hUnit, mUnit) = if (isRussian(locale)) "ч" to "мин" else "h" to "min"
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h == 0 -> "$m $mUnit"
        m == 0 -> "$h $hUnit"
        else -> "$h $hUnit ${m.toString().padStart(2, '0')} $mUnit"
    }
}

fun formatDurationMs(millis: Long, locale: Locale = Locale.getDefault()): String =
    formatDuration((millis / 60_000L).toInt(), locale)

fun formatTime(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
    Instant.ofEpochMilli(epochMillis).atZone(zone).format(timeFormatter)

/** «15 сентября 2026, 23:45» (en: «September 15, 2026, 23:45») — когда одного времени мало. */
fun formatDateTime(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault(), locale: Locale = Locale.getDefault()): String =
    Instant.ofEpochMilli(epochMillis).atZone(zone)
        .format(datePattern("d MMMM yyyy, HH:mm", "MMMM d, yyyy, HH:mm", locale))
