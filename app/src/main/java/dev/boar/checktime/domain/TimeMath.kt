package dev.boar.checktime.domain

object TimeMath {
    const val MINUTE_MS = 60_000L

    /** Целые минуты нерасписанного хвоста [accountedUntil, now). 0, если часы ушли назад. */
    fun tailMinutes(accountedUntil: Long, now: Long): Int =
        if (now <= accountedUntil) 0 else ((now - accountedUntil) / MINUTE_MS).toInt()

    fun nextAlarmAt(accountedUntil: Long, intervalMinutes: Int): Long =
        accountedUntil + intervalMinutes * MINUTE_MS

    fun snoozeAlarmAt(now: Long, snoozeMinutes: Int): Long =
        now + snoozeMinutes * MINUTE_MS
}
