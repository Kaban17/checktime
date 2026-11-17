package dev.boar.checktime.domain

import dev.boar.checktime.data.Category
import dev.boar.checktime.data.Group
import dev.boar.checktime.data.Segment
import dev.boar.checktime.domain.TimeMath.MINUTE_MS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class CsvExportTest {
    private val zone = ZoneId.of("Europe/Moscow")
    private val groups = listOf(Group(1, "Личное", 0, 0), Group(2, "Work, \"main\"", 0, 1))
    private val categories = listOf(
        Category(10, 1, "Сон", 0, 0),
        Category(11, 2, "Код", 0, 0, archived = true),
    )

    private fun at(text: String) = LocalDateTime.parse(text).atZone(zone).toInstant().toEpochMilli()

    private fun rows(segments: List<Segment>): List<String> {
        val csv = segmentsCsv(segments, categories, groups, zone)
        assertTrue(csv.startsWith("﻿"))
        assertTrue(csv.endsWith("\r\n"))
        return csv.removePrefix("﻿").removeSuffix("\r\n").split("\r\n")
    }

    @Test fun headerOnlyWhenEmpty() = assertEquals(listOf("date,start,end,minutes,group,category"), rows(emptyList()))

    @Test fun rowUsesLocalTimeAndNames() {
        val r = rows(listOf(Segment(1, at("2026-11-06T23:30"), at("2026-11-07T07:15"), 10)))
        assertEquals("2026-11-06,23:30,07:15,465,Личное,Сон", r[1])
    }

    @Test fun quotesFieldsWithCommasAndQuotes() {
        val start = at("2026-11-07T09:00")
        val r = rows(listOf(Segment(1, start, start + 50 * MINUTE_MS, 11)))
        assertEquals("2026-11-07,09:00,09:50,50,\"Work, \"\"main\"\"\",Код", r[1])
    }

    @Test fun unknownCategoryLeavesNamesEmpty() {
        val start = at("2026-11-07T09:00")
        assertEquals("2026-11-07,09:00,09:01,1,,", rows(listOf(Segment(1, start, start + MINUTE_MS, 99)))[1])
    }
}
