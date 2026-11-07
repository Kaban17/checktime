package dev.boar.checktime.ui.common

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.performSemanticsAction
import dev.boar.checktime.domain.TimeMath.MINUTE_MS
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TimeSliderFieldTest {
    @get:Rule val compose = createComposeRule()

    private val zone = ZoneId.of("Europe/Moscow")
    private fun at(date: LocalDate, h: Int, m: Int) = date.atTime(LocalTime.of(h, m)).atZone(zone).toInstant().toEpochMilli()
    private val day = LocalDate.of(2026, 9, 15)

    @Test fun pickerKeepsDateWhenInRange() {
        val current = at(day, 14, 0)
        val range = at(day, 12, 0)..at(day, 18, 0)
        assertEquals(at(day, 15, 30), pickerToEpoch(current, 15, 30, range, zone))
    }

    @Test fun pickerRollsToNextDayAcrossMidnight() {
        // запись 23:00 (15-го) – 02:00 (16-го); текущее значение 23:30; выбрали 01:00 → это уже 16-е
        val current = at(day, 23, 30)
        val range = at(day, 23, 1)..at(day.plusDays(1), 1, 59)
        assertEquals(at(day.plusDays(1), 1, 0), pickerToEpoch(current, 1, 0, range, zone))
    }

    @Test fun pickerRollsToPreviousDay() {
        val current = at(day.plusDays(1), 0, 30)
        val range = at(day, 23, 1)..at(day.plusDays(1), 1, 59)
        assertEquals(at(day, 23, 30), pickerToEpoch(current, 23, 30, range, zone))
    }

    @Test fun pickerOutOfRangeReturnsSameDateCandidate() {
        val current = at(day, 14, 0)
        val range = at(day, 12, 0)..at(day, 18, 0)
        assertEquals(at(day, 9, 0), pickerToEpoch(current, 9, 0, range, zone))
    }

    @Test fun pickerKeepsSubMinuteOffset() {
        val current = at(day, 14, 0) + 27_000 + 123
        val range = at(day, 0, 0)..at(day, 23, 59)
        assertEquals(at(day, 15, 30) + 27_123, pickerToEpoch(current, 15, 30, range, zone))
    }

    @Test fun snapFollowsLocalClockGrid() {
        val range = at(day, 12, 1)..at(day, 13, 58)
        assertEquals(at(day, 12, 35), snapTime(at(day, 12, 33), range, 5, zone))
        assertEquals(at(day, 12, 30), snapTime(at(day, 12, 32) + 29_000, range, 5, zone))
        // Сетка по местным часам, а не по UTC: в Индии смещение +5:30, 15-минутная сетка всё равно :00/:15/…
        val india = ZoneId.of("Asia/Kolkata")
        val r = day.atTime(12, 0).atZone(india).toInstant().toEpochMilli()
        assertEquals(r + 15 * MINUTE_MS, snapTime(r + 13 * MINUTE_MS, r..(r + 60 * MINUTE_MS), 15, india))
    }

    @Test fun snapReachesOffGridEdgesExactly() {
        val range = at(day, 12, 1)..at(day, 13, 58)
        assertEquals(at(day, 12, 1), snapTime(at(day, 11, 0), range, 5, zone))
        assertEquals(at(day, 13, 58), snapTime(at(day, 13, 58), range, 5, zone))
        assertEquals(at(day, 12, 1), snapTime(at(day, 12, 1) + 1, range, 5, zone)) // 12:00 вне диапазона → зажим
    }

    @Test fun sliderSnapsToStepAndReachesEnd() {
        val range = at(day, 12, 0)..at(day, 12, 12)
        var value by mutableStateOf(at(day, 12, 5))
        compose.setContent {
            TimeSliderField(value = value, range = range, stepMinutes = 5, onValueChange = { value = it }, zone = zone)
        }
        compose.onNodeWithTag("time-value").assertTextEquals("12:05")
        slider().performSemanticsAction(SemanticsActions.SetProgress) { it(8.4f) }
        compose.onNodeWithTag("time-value").assertTextEquals("12:10")
        slider().performSemanticsAction(SemanticsActions.SetProgress) { it(12f) }
        compose.onNodeWithTag("time-value").assertTextEquals("12:12")
        assertEquals(at(day, 12, 12), value)
    }

    @Test fun emptyRangeDisablesSlider() {
        val range = at(day, 12, 10)..at(day, 12, 5)
        compose.setContent {
            TimeSliderField(value = at(day, 12, 7), range = range, stepMinutes = 5, onValueChange = {}, zone = zone)
        }
        compose.onNodeWithTag("time-value").assertTextEquals("12:07")
        slider().assertIsNotEnabled()
    }

    private fun slider() = compose.onNodeWithTag("time-slider")
}
