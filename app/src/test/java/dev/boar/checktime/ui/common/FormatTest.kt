package dev.boar.checktime.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId

class FormatTest {
    @Test fun durationUnderHour() = assertEquals("47 мин", formatDuration(47))
    @Test fun durationWholeHours() = assertEquals("2 ч", formatDuration(120))
    @Test fun durationHoursAndMinutes() = assertEquals("1 ч 05 мин", formatDuration(65))
    @Test fun durationZero() = assertEquals("0 мин", formatDuration(0))
    @Test fun durationFromMillisFloors() = assertEquals("1 мин", formatDurationMs(119_999))
    @Test fun timeUsesZone() = assertEquals("03:00", formatTime(0, ZoneId.of("Europe/Moscow")))
}
