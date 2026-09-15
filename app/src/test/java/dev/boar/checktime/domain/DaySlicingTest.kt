package dev.boar.checktime.domain

import dev.boar.checktime.data.Segment
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class DaySlicingTest {
    private val zone = ZoneId.of("Europe/Moscow")

    @Test fun dayRangeIsLocalMidnightToMidnight() {
        val r = dayRange(LocalDate.of(2026, 9, 15), zone)
        // 2026-09-15T00:00+03:00 = 2026-09-14T21:00Z
        assertEquals(1_789_419_600_000L, r.start)
        assertEquals(r.start + 24 * 3_600_000L, r.end)
    }

    @Test fun clipCutsSegmentsAtRangeBounds() {
        val segs = listOf(
            Segment(startAt = 0, endAt = 100, categoryId = 1),
            Segment(startAt = 100, endAt = 200, categoryId = 2),
            Segment(startAt = 200, endAt = 300, categoryId = 3),
        )
        val clipped = clipSegments(segs, from = 50, to = 250)
        assertEquals(listOf(50L to 100L, 100L to 200L, 200L to 250L), clipped.map { it.startAt to it.endAt })
    }

    @Test fun clipDropsSegmentsOutsideRange() {
        val segs = listOf(Segment(startAt = 0, endAt = 100, categoryId = 1))
        assertEquals(emptyList<Segment>(), clipSegments(segs, from = 100, to = 200))
    }

    @Test fun totalsSumPerCategory() {
        val segs = listOf(
            Segment(startAt = 0, endAt = 100, categoryId = 1),
            Segment(startAt = 100, endAt = 250, categoryId = 2),
            Segment(startAt = 250, endAt = 300, categoryId = 1),
        )
        assertEquals(mapOf(1L to 150L, 2L to 150L), totalsByCategory(segs))
    }
}
