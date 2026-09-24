package dev.boar.checktime.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class StatsPeriodTest {
    private val zone = ZoneId.of("Europe/Moscow")
    private val wed = LocalDate.of(2026, 9, 16) // среда

    @Test fun dayPeriodIsOneLocalDay() {
        val p = periodOf(PeriodKind.DAY, wed)
        assertEquals(StatsPeriod.Day(wed), p)
        assertEquals(dayRange(wed, zone), p.range(zone))
        assertEquals(1, p.dayCount())
    }

    @Test fun weekStartsOnMonday() {
        val p = periodOf(PeriodKind.WEEK, wed)
        assertEquals(StatsPeriod.Week(LocalDate.of(2026, 9, 14)), p)
        assertEquals(LocalDate.of(2026, 9, 14), p.firstDate())
        assertEquals(LocalDate.of(2026, 9, 20), p.lastDate())
        assertEquals(7, p.dayCount())
        assertEquals(dayRange(LocalDate.of(2026, 9, 14), zone).start, p.range(zone).start)
        assertEquals(dayRange(LocalDate.of(2026, 9, 20), zone).end, p.range(zone).end)
    }

    @Test fun monthStartsOnFirstDay() {
        val p = periodOf(PeriodKind.MONTH, wed)
        assertEquals(StatsPeriod.Month(LocalDate.of(2026, 9, 1)), p)
        assertEquals(LocalDate.of(2026, 9, 30), p.lastDate())
        assertEquals(30, p.dayCount())
    }

    @Test fun shiftMovesWholePeriods() {
        assertEquals(StatsPeriod.Day(wed.minusDays(1)), periodOf(PeriodKind.DAY, wed).shift(-1))
        assertEquals(StatsPeriod.Week(LocalDate.of(2026, 9, 7)), periodOf(PeriodKind.WEEK, wed).shift(-1))
        assertEquals(StatsPeriod.Month(LocalDate.of(2026, 8, 1)), periodOf(PeriodKind.MONTH, wed).shift(-1))
        assertEquals(StatsPeriod.Month(LocalDate.of(2026, 10, 1)), periodOf(PeriodKind.MONTH, wed).shift(1))
    }

    @Test fun customRangeIsInclusiveAndShiftsByItsLength() {
        val p = StatsPeriod.Custom(LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 12))
        assertEquals(3, p.dayCount())
        assertEquals(dayRange(LocalDate.of(2026, 9, 12), zone).end, p.range(zone).end)
        assertEquals(
            StatsPeriod.Custom(LocalDate.of(2026, 9, 7), LocalDate.of(2026, 9, 9)),
            p.shift(-1),
        )
    }
}
