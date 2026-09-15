package dev.boar.checktime.ui.categories

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import dev.boar.checktime.AppContainer
import dev.boar.checktime.MainDispatcherRule
import dev.boar.checktime.R
import dev.boar.checktime.data.Segment
import dev.boar.checktime.testContainer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CategoriesViewModelTest {
    @get:Rule val mainDispatcher = MainDispatcherRule()

    private val context: Application = ApplicationProvider.getApplicationContext()
    private lateinit var container: AppContainer
    private lateinit var vm: CategoriesViewModel

    @Before fun setUp() {
        container = testContainer(context) { 0L }
        vm = CategoriesViewModel(container)
    }

    @After fun tearDown() = container.db.close()

    @Test fun addGroupAndCategoryAppearInTree() = runTest {
        vm.addGroup("Работа", 1)
        val g = vm.tree.first { it.isNotEmpty() }[0].group
        vm.addCategory(g.id, "Код", 2)
        val tree = vm.tree.first { it[0].categories.isNotEmpty() }
        assertEquals("Код", tree[0].categories[0].name)
    }

    @Test fun editRenamesAndRecolors() = runTest {
        vm.addGroup("A", 1)
        val g = vm.tree.first { it.isNotEmpty() }[0].group
        vm.editGroup(g, "B", 7)
        val edited = vm.tree.first { it[0].group.name == "B" }[0].group
        assertEquals(7, edited.color)
    }

    @Test fun deleteCategoryWithSegmentsReportsMessage() = runTest {
        vm.addGroup("A", 1)
        val g = vm.tree.first { it.isNotEmpty() }[0].group
        vm.addCategory(g.id, "c", 2)
        val c = vm.tree.first { it[0].categories.isNotEmpty() }[0].categories[0]
        container.db.segmentDao().insertAll(listOf(Segment(startAt = 0, endAt = 1, categoryId = c.id)))
        vm.deleteCategory(c)
        assertEquals(R.string.categories_delete_has_segments, vm.messages.first())
        assertEquals(1, vm.tree.first()[0].categories.size)
    }

    @Test fun deleteGroupWithCategoriesReportsMessage() = runTest {
        vm.addGroup("A", 1)
        val g = vm.tree.first { it.isNotEmpty() }[0].group
        vm.addCategory(g.id, "c", 2)
        vm.tree.first { it[0].categories.isNotEmpty() }
        vm.deleteGroup(g)
        assertEquals(R.string.categories_delete_group_not_empty, vm.messages.first())
    }

    @Test fun toggleArchivedFlips() = runTest {
        vm.addGroup("A", 1)
        val g = vm.tree.first { it.isNotEmpty() }[0].group
        vm.addCategory(g.id, "c", 2)
        val c = vm.tree.first { it[0].categories.isNotEmpty() }[0].categories[0]
        vm.toggleArchived(c)
        assertTrue(vm.tree.first { it[0].categories[0].archived }[0].categories[0].archived)
    }
}
