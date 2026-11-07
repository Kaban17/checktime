package dev.boar.checktime.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.util.Locale

class FormatTest {
    private val ru = Locale.forLanguageTag("ru")
    private val en = Locale.ENGLISH
    private val moscow = ZoneId.of("Europe/Moscow")

    @Test fun durationUnderHour() = assertEquals("47 мин", formatDuration(47, ru))
    @Test fun durationWholeHours() = assertEquals("2 ч", formatDuration(120, ru))
    @Test fun durationHoursAndMinutes() = assertEquals("1 ч 05 мин", formatDuration(65, ru))
    @Test fun durationZero() = assertEquals("0 мин", formatDuration(0, ru))
    @Test fun durationFromMillisFloors() = assertEquals("1 мин", formatDurationMs(119_999, ru))
    @Test fun durationInEnglish() {
        assertEquals("47 min", formatDuration(47, en))
        assertEquals("2 h", formatDuration(120, en))
        assertEquals("1 h 05 min", formatDuration(65, en))
    }
    @Test fun timeUsesZone() = assertEquals("03:00", formatTime(0, moscow))
    @Test fun dateTimeHasDayMonthYear() = assertEquals("1 января 1970, 03:00", formatDateTime(0, moscow, ru))
    @Test fun dateTimeInEnglish() = assertEquals("January 1, 1970, 03:00", formatDateTime(0, moscow, en))
}
