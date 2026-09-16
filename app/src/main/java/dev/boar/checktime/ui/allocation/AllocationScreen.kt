package dev.boar.checktime.ui.allocation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.boar.checktime.R
import dev.boar.checktime.data.Category
import dev.boar.checktime.data.GroupWithCategories
import dev.boar.checktime.ui.common.RepeatButton
import dev.boar.checktime.ui.common.formatDuration
import dev.boar.checktime.ui.common.formatTime

@Composable
fun AllocationScreen(
    state: AllocationUiState,
    onIncrement: (Long) -> Unit,
    onDecrement: (Long) -> Unit,
    onAssignRest: (Long) -> Unit,
    onSave: () -> Unit,
    onPostpone: () -> Unit,
) {
    Scaffold { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (state) {
                AllocationUiState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                AllocationUiState.Empty -> Text(stringResource(R.string.allocation_empty), Modifier.align(Alignment.Center))
                is AllocationUiState.Ready -> ReadyContent(state, onIncrement, onDecrement, onAssignRest, onSave, onPostpone)
            }
        }
    }
}

@Composable
private fun ReadyContent(
    state: AllocationUiState.Ready,
    onIncrement: (Long) -> Unit,
    onDecrement: (Long) -> Unit,
    onAssignRest: (Long) -> Unit,
    onSave: () -> Unit,
    onPostpone: () -> Unit,
) {
    val draft = state.draft
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(stringResource(R.string.allocation_title), style = MaterialTheme.typography.titleLarge)
            Text(
                stringResource(
                    R.string.allocation_range,
                    formatTime(state.start), formatTime(state.end), formatDuration(draft.totalMinutes),
                ),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                stringResource(R.string.allocation_remaining, formatDuration(draft.remaining)),
                style = MaterialTheme.typography.titleMedium,
                color = if (draft.remaining == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
        }
        if (state.groups.isEmpty()) {
            Text(
                stringResource(R.string.allocation_no_categories),
                modifier = Modifier.weight(1f).padding(16.dp),
            )
        } else {
            LazyColumn(Modifier.weight(1f)) {
                state.groups.forEach { g ->
                    item(key = "g${g.group.id}") {
                        Text(
                            g.group.name,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.labelLarge,
                            color = Color(g.group.color),
                        )
                    }
                    items(g.categories, key = { "c${it.id}" }) { c ->
                        CategoryRow(c, draft.of(c.id), onIncrement, onDecrement, onAssignRest)
                    }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(onClick = onPostpone, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.allocation_postpone))
            }
            Button(onClick = onSave, enabled = draft.isComplete, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.allocation_done))
            }
        }
    }
}

@Composable
private fun CategoryRow(
    category: Category,
    minutes: Int,
    onIncrement: (Long) -> Unit,
    onDecrement: (Long) -> Unit,
    onAssignRest: (Long) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(12.dp).clip(CircleShape).background(Color(category.color)))
        Spacer(Modifier.width(12.dp))
        // Тап по названию — отдать категории весь остаток.
        Text(
            category.name,
            modifier = Modifier
                .weight(1f)
                .clickable { onAssignRest(category.id) }
                .padding(vertical = 12.dp),
            style = MaterialTheme.typography.bodyLarge,
        )
        RepeatButton(onClick = { onDecrement(category.id) }, modifier = Modifier.testTag("dec-${category.id}")) {
            Text("−", style = MaterialTheme.typography.titleLarge)
        }
        Text(
            minutes.toString(),
            modifier = Modifier.width(48.dp).testTag("minutes-${category.id}"),
            textAlign = TextAlign.Center,
            fontWeight = if (minutes > 0) FontWeight.Bold else FontWeight.Normal,
        )
        RepeatButton(onClick = { onIncrement(category.id) }, modifier = Modifier.testTag("inc-${category.id}")) {
            Icon(Icons.Default.Add, contentDescription = null)
        }
    }
}
