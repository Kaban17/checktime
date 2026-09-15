package dev.boar.checktime.ui.categories

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.boar.checktime.AppContainer
import dev.boar.checktime.R
import dev.boar.checktime.data.Category
import dev.boar.checktime.data.Group
import dev.boar.checktime.data.GroupWithCategories
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CategoriesViewModel(private val container: AppContainer) : ViewModel() {
    private val repo = container.categories

    val tree: StateFlow<List<GroupWithCategories>> =
        repo.observeTree().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _messages = Channel<Int>(Channel.BUFFERED)
    /** Id строкового ресурса для snackbar. */
    val messages: Flow<Int> = _messages.receiveAsFlow()

    fun addGroup(name: String, color: Int) = inScope { repo.addGroup(name.trim(), color) }
    fun editGroup(group: Group, name: String, color: Int) = inScope { repo.updateGroup(group.copy(name = name.trim(), color = color)) }
    fun moveGroup(group: Group, delta: Int) = inScope { repo.moveGroup(group, delta) }
    fun deleteGroup(group: Group) = inScope {
        if (!repo.deleteGroup(group)) notify(R.string.categories_delete_group_not_empty)
    }

    fun addCategory(groupId: Long, name: String, color: Int) = inScope { repo.addCategory(groupId, name.trim(), color) }
    fun editCategory(category: Category, name: String, color: Int) = inScope { repo.updateCategory(category.copy(name = name.trim(), color = color)) }
    fun toggleArchived(category: Category) = inScope { repo.updateCategory(category.copy(archived = !category.archived)) }
    fun moveCategory(category: Category, delta: Int) = inScope { repo.moveCategory(category, delta) }
    fun deleteCategory(category: Category) = inScope {
        if (!repo.deleteCategory(category)) notify(R.string.categories_delete_has_segments)
    }

    private fun inScope(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    private suspend fun notify(@StringRes message: Int) = _messages.send(message)

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { CategoriesViewModel(container) }
        }
    }
}
