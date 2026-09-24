package dev.boar.checktime.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

enum class PeriodKind { DAY, WEEK, MONTH }

/** Период статистики. Границы — локальные сутки; [Custom.to] включительно. */
sealed interface StatsPeriod {
    data class Day(val date: LocalDate) : StatsPeriod
    data class Week(val monday: LocalDate) : StatsPeriod
    data class Month(val firstDay: LocalDate) : StatsPeriod
    data class Custom(val from: LocalDate, val to: LocalDate) : StatsPeriod
}

fun periodOf(kind: PeriodKind, anchor: LocalDate): StatsPeriod = when (kind) {
    PeriodKind.DAY -> StatsPeriod.Day(anchor)
    PeriodKind.WEEK -> StatsPeriod.Week(anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)))
    PeriodKind.MONTH -> StatsPeriod.Month(anchor.withDayOfMonth(1))
}

fun StatsPeriod.firstDate(): LocalDate = when (this) {
    is StatsPeriod.Day -> date
    is StatsPeriod.Week -> monday
    is StatsPeriod.Month -> firstDay
    is StatsPeriod.Custom -> from
}

fun StatsPeriod.lastDate(): LocalDate = when (this) {
    is StatsPeriod.Day -> date
    is StatsPeriod.Week -> monday.plusDays(6)
    is StatsPeriod.Month -> firstDay.with(TemporalAdjusters.lastDayOfMonth())
    is StatsPeriod.Custom -> to
}

fun StatsPeriod.dayCount(): Int = (lastDate().toEpochDay() - firstDate().toEpochDay() + 1).toInt()

/** [первая локальная полночь периода, полночь после последнего дня). */
fun StatsPeriod.range(zone: ZoneId): DayRange = DayRange(
    start = dayRange(firstDate(), zone).start,
    end = dayRange(lastDate(), zone).end,
)

/** Листание: ±1 период того же вида; для произвольного диапазона — на его длину. */
fun StatsPeriod.shift(periods: Long): StatsPeriod = when (this) {
    is StatsPeriod.Day -> StatsPeriod.Day(date.plusDays(periods))
    is StatsPeriod.Week -> StatsPeriod.Week(monday.plusWeeks(periods))
    is StatsPeriod.Month -> StatsPeriod.Month(firstDay.plusMonths(periods))
    is StatsPeriod.Custom -> {
        val days = dayCount().toLong() * periods
        StatsPeriod.Custom(from.plusDays(days), to.plusDays(days))
    }
}
