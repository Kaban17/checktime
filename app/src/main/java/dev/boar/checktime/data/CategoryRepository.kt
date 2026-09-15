package dev.boar.checktime.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

data class GroupWithCategories(val group: Group, val categories: List<Category>)

class CategoryRepository(private val dao: CategoryDao) {

    fun observeTree(): Flow<List<GroupWithCategories>> =
        combine(dao.observeGroups(), dao.observeCategories()) { groups, categories ->
            groups.map { g -> GroupWithCategories(g, categories.filter { it.groupId == g.id }) }
        }

    /** Стартовый набор из спеки (п. 4.2). Строки не в ресурсах — репозиторию контекст не нужен. */
    suspend fun seedDefaultsIfEmpty() {
        if (dao.groupCount() > 0) return
        val personal = addGroup("Личное", Palette.colors[3])
        listOf("Сон", "Еда", "Спорт", "Отдых").forEachIndexed { i, name ->
            addCategory(personal, name, Palette.colors[(i + 4) % Palette.colors.size])
        }
        val work = addGroup("Работа", Palette.colors[5])
        addCategory(work, "Работа", Palette.colors[5])
    }

    suspend fun addGroup(name: String, color: Int): Long =
        dao.insertGroup(Group(name = name, color = color, sortOrder = dao.groupCount()))

    suspend fun updateGroup(group: Group) = dao.updateGroup(group)

    suspend fun deleteGroup(group: Group): Boolean {
        if (dao.categoryCount(group.id) > 0) return false
        dao.deleteGroup(group)
        return true
    }

    suspend fun addCategory(groupId: Long, name: String, color: Int): Long =
        dao.insertCategory(Category(groupId = groupId, name = name, color = color, sortOrder = dao.categoryCount(groupId)))

    suspend fun updateCategory(category: Category) = dao.updateCategory(category)

    suspend fun deleteCategory(category: Category): Boolean {
        if (dao.segmentCount(category.id) > 0) return false
        dao.deleteCategory(category)
        return true
    }

    suspend fun moveGroup(group: Group, delta: Int) {
        val groups = dao.groups()
        val i = groups.indexOfFirst { it.id == group.id }
        val j = i + delta
        if (i < 0 || j !in groups.indices) return
        // Перенумеровываем весь список, чтобы sortOrder всегда был 0..n-1 без дублей.
        val reordered = groups.toMutableList().apply { add(j, removeAt(i)) }
        dao.updateGroups(reordered.mapIndexed { idx, g -> g.copy(sortOrder = idx) })
    }

    suspend fun moveCategory(category: Category, delta: Int) {
        val siblings = dao.categoriesOfGroup(category.groupId)
        val i = siblings.indexOfFirst { it.id == category.id }
        val j = i + delta
        if (i < 0 || j !in siblings.indices) return
        val reordered = siblings.toMutableList().apply { add(j, removeAt(i)) }
        dao.updateCategories(reordered.mapIndexed { idx, c -> c.copy(sortOrder = idx) })
    }
}
