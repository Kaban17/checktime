package dev.boar.checktime.domain

import dev.boar.checktime.data.Category
import dev.boar.checktime.data.Group
import dev.boar.checktime.data.GroupWithCategories
import dev.boar.checktime.data.Segment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StatsTest {
    private val m = TimeMath.MINUTE_MS
    private val personal = Group(id = 1, name = "Личное", color = 1, sortOrder = 0)
    private val workGroup = Group(id = 2, name = "Работа", color = 2, sortOrder = 1)
    private val sleep = Category(id = 1, groupId = 1, name = "Сон", color = 10, sortOrder = 0)
    private val food = Category(id = 2, groupId = 1, name = "Еда", color = 11, sortOrder = 1)
    private val work = Category(id = 3, groupId = 2, name = "Работа", color = 12, sortOrder = 0)
    private val tree = listOf(
        GroupWithCategories(personal, listOf(sleep, food)),
        GroupWithCategories(workGroup, listOf(work)),
    )

    private fun seg(from: Long, to: Long, category: Long) = Segment(startAt = from * m, endAt = to * m, categoryId = category)

    @Test fun emptyInputGivesZeroTotal() {
        val r = aggregate(emptyList(), tree)
        assertEquals(0L, r.totalMillis)
        assertTrue(r.groups.isEmpty())
        assertTrue(r.categories.isEmpty())
    }

    @Test fun categoriesAreSortedByDurationWithShares() {
        val r = aggregate(listOf(seg(0, 60, sleep.id), seg(60, 90, work.id), seg(90, 100, food.id)), tree)
        assertEquals(100 * m, r.totalMillis)
        assertEquals(listOf("Сон", "Работа", "Еда"), r.categories.map { it.category.name })
        assertEquals(listOf(60 * m, 30 * m, 10 * m), r.categories.map { it.millis })
        assertEquals(0.6f, r.categories[0].share, 0.0001f)
        assertEquals("Личное", r.categories[0].group.name)
    }

    @Test fun groupsSumTheirCategoriesAndSortByDuration() {
        val r = aggregate(listOf(seg(0, 60, sleep.id), seg(60, 90, work.id), seg(90, 100, food.id)), tree)
        assertEquals(listOf("Личное", "Работа"), r.groups.map { it.group.name })
        assertEquals(70 * m, r.groups[0].millis)
        assertEquals(0.7f, r.groups[0].share, 0.0001f)
        assertEquals(listOf("Сон", "Еда"), r.groups[0].categories.map { it.category.name })
    }

    @Test fun categoriesWithoutTimeAreOmitted() {
        val r = aggregate(listOf(seg(0, 10, work.id)), tree)
        assertEquals(listOf("Работа"), r.categories.map { it.category.name })
        assertEquals(listOf("Работа"), r.groups.map { it.group.name })
    }

    @Test fun segmentsOfUnknownCategoryAreIgnoredButNotCounted() {
        val r = aggregate(listOf(seg(0, 10, work.id), seg(10, 20, 999)), tree)
        assertEquals(10 * m, r.totalMillis)
        assertEquals(1, r.categories.size)
    }
}
