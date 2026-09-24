package dev.boar.checktime.domain

import dev.boar.checktime.data.Category
import dev.boar.checktime.data.Group
import dev.boar.checktime.data.GroupWithCategories
import dev.boar.checktime.data.Segment

data class CategoryStat(val category: Category, val group: Group, val millis: Long, val share: Float)

data class GroupStat(val group: Group, val millis: Long, val share: Float, val categories: List<CategoryStat>)

/** Итоги периода: всё расписанное время, разбивка по группам и плоский список категорий (для диаграммы). */
data class StatsResult(val totalMillis: Long, val groups: List<GroupStat>, val categories: List<CategoryStat>)

/**
 * Считает итоги по уже обрезанным по периоду [segments].
 * Сегменты неизвестных (удалённых) категорий отбрасываются и в сумму не попадают.
 * Категории без времени в списке не показываем.
 */
fun aggregate(segments: List<Segment>, tree: List<GroupWithCategories>): StatsResult {
    val index = tree.flatMap { g -> g.categories.map { it.id to (it to g.group) } }.toMap()
    val byCategory = totalsByCategory(segments).mapNotNull { (id, ms) ->
        index[id]?.let { (category, group) -> Triple(category, group, ms) }
    }
    val total = byCategory.sumOf { it.third }
    if (total == 0L) return StatsResult(0L, emptyList(), emptyList())

    val categories = byCategory
        .map { (category, group, ms) -> CategoryStat(category, group, ms, ms.toFloat() / total) }
        .sortedByDescending { it.millis }

    val groups = categories
        .groupBy { it.group.id }
        .map { (_, stats) ->
            val ms = stats.sumOf { it.millis }
            GroupStat(stats.first().group, ms, ms.toFloat() / total, stats.sortedByDescending { it.millis })
        }
        .sortedByDescending { it.millis }

    return StatsResult(total, groups, categories)
}
