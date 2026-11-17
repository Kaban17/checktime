package dev.boar.checktime.domain

import androidx.room.withTransaction
import dev.boar.checktime.data.AppDatabase
import dev.boar.checktime.data.Category
import dev.boar.checktime.data.Group
import dev.boar.checktime.data.Segment
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd")
private val timeFormat = DateTimeFormatter.ofPattern("HH:mm")

/**
 * CSV по RFC 4180: строка на запись, местное время, заголовок на английском (для таблиц и
 * скриптов, не для чтения в приложении). Запись через полночь — одна строка с датой начала.
 * BOM в начале, чтобы Excel узнал UTF-8 и не испортил кириллицу.
 */
fun segmentsCsv(segments: List<Segment>, categories: List<Category>, groups: List<Group>, zone: ZoneId): String {
    val categoryById = categories.associateBy { it.id }
    val groupById = groups.associateBy { it.id }
    return buildString {
        append('﻿')
        appendCsvRow(listOf("date", "start", "end", "minutes", "group", "category"))
        for (s in segments) {
            val start = Instant.ofEpochMilli(s.startAt).atZone(zone)
            val end = Instant.ofEpochMilli(s.endAt).atZone(zone)
            val category = categoryById[s.categoryId]
            appendCsvRow(
                listOf(
                    start.format(dateFormat),
                    start.format(timeFormat),
                    end.format(timeFormat),
                    ((s.endAt - s.startAt) / TimeMath.MINUTE_MS).toString(),
                    category?.let { groupById[it.groupId]?.name }.orEmpty(),
                    category?.name.orEmpty(),
                ),
            )
        }
    }
}

private fun StringBuilder.appendCsvRow(fields: List<String>) {
    fields.joinTo(this, ",") { f ->
        if (f.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"${f.replace("\"", "\"\"")}\"" else f
    }
    append("\r\n")
}

/** Все записи разом в одном снимке базы; возвращает CSV и число записей. */
suspend fun exportAllCsv(db: AppDatabase, zone: ZoneId): Pair<String, Int> = db.withTransaction {
    val segments = db.segmentDao().all()
    segmentsCsv(segments, db.categoryDao().categories(), db.categoryDao().groups(), zone) to segments.size
}
