package dev.boar.checktime.ui.common

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
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
class TimeStepperFieldTest {
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

    @Test fun stepButtonsMoveByStepAndClamp() {
        val range = at(day, 12, 0)..at(day, 12, 12)
        var value by mutableStateOf(at(day, 12, 5))
        compose.setContent {
            TimeStepperField(value = value, range = range, stepMinutes = 5, onValueChange = { value = it }, zone = zone)
        }
        compose.onNodeWithTag("time-value").assertTextEquals("12:05")
        compose.onNodeWithTag("time-inc").performClick()
        compose.onNodeWithTag("time-value").assertTextEquals("12:10")
        compose.onNodeWithTag("time-inc").performClick() // 12:15 > 12:12 → зажим
        compose.onNodeWithTag("time-value").assertTextEquals("12:12")
        compose.onNodeWithTag("time-dec").performClick()
        compose.onNodeWithTag("time-dec").performClick()
        compose.onNodeWithTag("time-dec").performClick() // 12:12 → 12:07 → 12:02 → 12:00 (зажим)
        compose.onNodeWithTag("time-value").assertTextEquals("12:00")
        assertEquals(at(day, 12, 0), value)
    }
}
