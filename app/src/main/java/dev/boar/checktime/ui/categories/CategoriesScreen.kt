package dev.boar.checktime.ui.categories

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.boar.checktime.R
import dev.boar.checktime.data.Category
import dev.boar.checktime.data.Group
import dev.boar.checktime.data.GroupWithCategories
import dev.boar.checktime.data.Palette
import dev.boar.checktime.ui.common.ColorPicker

/** Что сейчас редактируем в диалоге. */
private sealed interface Editing {
    data object NewGroup : Editing
    data class EditGroup(val group: Group) : Editing
    data class NewCategory(val groupId: Long) : Editing
    data class EditCategory(val category: Category) : Editing
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesRoute(viewModel: CategoriesViewModel, onBack: () -> Unit) {
    val tree by viewModel.tree.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    var editing by remember { mutableStateOf<Editing?>(null) }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbar.showSnackbar(context.getString(it)) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.categories_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { editing = Editing.NewGroup }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.categories_add_group))
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(Modifier.padding(padding)) {
            tree.forEach { g ->
                item(key = "g${g.group.id}") {
                    GroupRow(
                        group = g.group,
                        onEdit = { editing = Editing.EditGroup(g.group) },
                        onDelete = { viewModel.deleteGroup(g.group) },
                        onUp = { viewModel.moveGroup(g.group, -1) },
                        onDown = { viewModel.moveGroup(g.group, +1) },
                        onAddCategory = { editing = Editing.NewCategory(g.group.id) },
                    )
                }
                items(g.categories, key = { "c${it.id}" }) { c ->
                    CategoryRow(
                        category = c,
                        onEdit = { editing = Editing.EditCategory(c) },
                        onDelete = { viewModel.deleteCategory(c) },
                        onUp = { viewModel.moveCategory(c, -1) },
                        onDown = { viewModel.moveCategory(c, +1) },
                        onToggleArchived = { viewModel.toggleArchived(c) },
                    )
                }
            }
        }
    }

    editing?.let { e ->
        val (title, name, color) = when (e) {
            Editing.NewGroup -> Triple(stringResource(R.string.categories_add_group), "", Palette.colors[0])
            is Editing.EditGroup -> Triple(stringResource(R.string.categories_edit_group), e.group.name, e.group.color)
            is Editing.NewCategory -> Triple(stringResource(R.string.categories_add_category), "", Palette.colors[0])
            is Editing.EditCategory -> Triple(stringResource(R.string.categories_edit_category), e.category.name, e.category.color)
        }
        NameColorDialog(
            title = title,
            initialName = name,
            initialColor = color,
            onDismiss = { editing = null },
            onConfirm = { newName, newColor ->
                when (e) {
                    Editing.NewGroup -> viewModel.addGroup(newName, newColor)
                    is Editing.EditGroup -> viewModel.editGroup(e.group, newName, newColor)
                    is Editing.NewCategory -> viewModel.addCategory(e.groupId, newName, newColor)
                    is Editing.EditCategory -> viewModel.editCategory(e.category, newName, newColor)
                }
                editing = null
            },
        )
    }
}

@Composable
private fun GroupRow(
    group: Group,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onAddCategory: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            ColorDot(group.color)
            Text(group.name, Modifier.weight(1f).padding(start = 12.dp), style = MaterialTheme.typography.titleMedium)
            IconButton(onClick = onUp) { Icon(Icons.Default.KeyboardArrowUp, contentDescription = null) }
            IconButton(onClick = onDown) { Icon(Icons.Default.KeyboardArrowDown, contentDescription = null) }
            IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, contentDescription = null) }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = null) }
        }
        TextButton(onClick = onAddCategory, modifier = Modifier.padding(start = 28.dp)) {
            Text(stringResource(R.string.categories_add_category))
        }
    }
}

@Composable
private fun CategoryRow(
    category: Category,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onToggleArchived: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(start = 40.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ColorDot(category.color)
        Text(
            category.name,
            Modifier.weight(1f).padding(start = 12.dp),
            textDecoration = if (category.archived) TextDecoration.LineThrough else null,
            color = if (category.archived) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
        )
        IconButton(onClick = onUp) { Icon(Icons.Default.KeyboardArrowUp, contentDescription = null) }
        IconButton(onClick = onDown) { Icon(Icons.Default.KeyboardArrowDown, contentDescription = null) }
        IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, contentDescription = null) }
        TextButton(onClick = onToggleArchived) {
            Text(stringResource(if (category.archived) R.string.categories_unarchive else R.string.categories_archive))
        }
        IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = null) }
    }
}

@Composable
private fun ColorDot(color: Int) {
    Box(Modifier.size(14.dp).clip(CircleShape).background(Color(color)))
}

@Composable
private fun NameColorDialog(
    title: String,
    initialName: String,
    initialColor: Int,
    onDismiss: () -> Unit,
    onConfirm: (String, Int) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var color by remember { mutableStateOf(initialColor) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.categories_name)) },
                    singleLine = true,
                )
                Spacer(Modifier.size(12.dp))
                ColorPicker(selected = color, onSelect = { color = it })
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name, color) }, enabled = name.isNotBlank()) {
                Text(stringResource(R.string.dialog_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_cancel)) }
        },
    )
}
