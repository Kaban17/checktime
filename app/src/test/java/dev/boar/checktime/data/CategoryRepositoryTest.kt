package dev.boar.checktime.data

import dev.boar.checktime.inMemoryDb
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CategoryRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: CategoryRepository

    @Before fun setUp() { db = inMemoryDb(); repo = CategoryRepository(db.categoryDao()) }
    @After fun tearDown() = db.close()

    @Test fun seedCreatesDefaultsOnce() = runTest {
        repo.seedDefaultsIfEmpty()
        repo.seedDefaultsIfEmpty()
        val tree = repo.observeTree().first()
        assertEquals(listOf("Личное", "Работа"), tree.map { it.group.name })
        assertEquals(listOf("Сон", "Еда", "Спорт", "Отдых"), tree[0].categories.map { it.name })
        assertEquals(listOf("Работа"), tree[1].categories.map { it.name })
    }

    @Test fun treeGroupsCategoriesByGroupInOrder() = runTest {
        val g1 = repo.addGroup("A", 1)
        val g2 = repo.addGroup("B", 2)
        repo.addCategory(g2, "b1", 0)
        repo.addCategory(g1, "a1", 0)
        repo.addCategory(g1, "a2", 0)
        val tree = repo.observeTree().first()
        assertEquals(listOf("a1", "a2"), tree[0].categories.map { it.name })
        assertEquals(listOf("b1"), tree[1].categories.map { it.name })
    }

    @Test fun deleteGroupOnlyWhenEmpty() = runTest {
        val g = repo.addGroup("A", 1)
        repo.addCategory(g, "a", 0)
        val group = repo.observeTree().first()[0].group
        assertFalse(repo.deleteGroup(group))
        repo.deleteCategory(repo.observeTree().first()[0].categories[0])
        assertTrue(repo.deleteGroup(group))
        assertEquals(0, repo.observeTree().first().size)
    }

    @Test fun deleteCategoryOnlyWithoutSegments() = runTest {
        val g = repo.addGroup("A", 1)
        val c = repo.addCategory(g, "a", 0)
        db.segmentDao().insertAll(listOf(Segment(startAt = 0, endAt = 1, categoryId = c)))
        val cat = repo.observeTree().first()[0].categories[0]
        assertFalse(repo.deleteCategory(cat))
        repo.updateCategory(cat.copy(archived = true))
        assertTrue(repo.observeTree().first()[0].categories[0].archived)
    }

    @Test fun moveCategorySwapsWithNeighbour() = runTest {
        val g = repo.addGroup("A", 1)
        repo.addCategory(g, "x", 0)
        repo.addCategory(g, "y", 0)
        repo.addCategory(g, "z", 0)
        val y = repo.observeTree().first()[0].categories[1]
        repo.moveCategory(y, -1)
        assertEquals(listOf("y", "x", "z"), repo.observeTree().first()[0].categories.map { it.name })
        repo.moveCategory(repo.observeTree().first()[0].categories[0], -1) // уже первая — без изменений
        assertEquals(listOf("y", "x", "z"), repo.observeTree().first()[0].categories.map { it.name })
    }

    @Test fun moveGroupSwapsWithNeighbour() = runTest {
        repo.addGroup("A", 1)
        repo.addGroup("B", 2)
        repo.moveGroup(repo.observeTree().first()[1].group, -1)
        assertEquals(listOf("B", "A"), repo.observeTree().first().map { it.group.name })
    }
}
