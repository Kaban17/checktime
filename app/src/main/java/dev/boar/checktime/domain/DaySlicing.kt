package dev.boar.checktime.domain

import dev.boar.checktime.data.Segment
import java.time.LocalDate
import java.time.ZoneId

data class DayRange(val start: Long, val end: Long)

/** [локальная полночь date, локальная полночь date+1) в unix-мс. */
fun dayRange(date: LocalDate, zone: ZoneId): DayRange = DayRange(
    start = date.atStartOfDay(zone).toInstant().toEpochMilli(),
    end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(),
)

/** Обрезает сегменты по [from, to); не попавшие внутрь отбрасывает. */
fun clipSegments(segments: List<Segment>, from: Long, to: Long): List<Segment> =
    segments.mapNotNull { s ->
        val a = maxOf(s.startAt, from)
        val b = minOf(s.endAt, to)
        if (b > a) s.copy(startAt = a, endAt = b) else null
    }

/** categoryId -> суммарная длительность в мс. */
fun totalsByCategory(segments: List<Segment>): Map<Long, Long> =
    segments.groupBy { it.categoryId }.mapValues { (_, list) -> list.sumOf { it.endAt - it.startAt } }
