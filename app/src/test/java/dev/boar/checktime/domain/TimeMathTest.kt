package dev.boar.checktime.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class TimeMathTest {
    private val m = TimeMath.MINUTE_MS

    @Test fun tailIsWholeMinutes() {
        assertEquals(47, TimeMath.tailMinutes(accountedUntil = 0, now = 47 * m + 59_000))
    }

    @Test fun tailIsZeroWhenClockWentBack() {
        assertEquals(0, TimeMath.tailMinutes(accountedUntil = 10 * m, now = 5 * m))
    }

    @Test fun tailIsZeroUnderOneMinute() {
        assertEquals(0, TimeMath.tailMinutes(accountedUntil = 0, now = 59_999))
    }

    @Test fun nextAlarmIsAccountedUntilPlusInterval() {
        assertEquals(1_000 + 30 * m, TimeMath.nextAlarmAt(accountedUntil = 1_000, intervalMinutes = 30))
    }

    @Test fun snoozeAlarmIsNowPlusSnooze() {
        assertEquals(5_000 + 10 * m, TimeMath.snoozeAlarmAt(now = 5_000, snoozeMinutes = 10))
    }
}
