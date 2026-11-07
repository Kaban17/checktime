package dev.boar.checktime.ui.goals

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.boar.checktime.R
import dev.boar.checktime.domain.GoalProgress
import dev.boar.checktime.domain.GoalTarget
import dev.boar.checktime.ui.common.RepeatButton
import dev.boar.checktime.ui.common.formatDuration
import dev.boar.checktime.ui.common.formatDurationMs
import dev.boar.checktime.ui.stats.periodTitle

private const val STEP_MINUTES = 30
private const val MAX_MINUTES = 168 * 60
private const val DEFAULT_MINUTES = 5 * 60

private sealed interface GoalDialog {
    data object New : GoalDialog
    data class Edit(val goal: GoalProgress) : GoalDialog
}

@Composable
fun GoalsRoute(viewModel: GoalsViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    GoalsScreen(
        state = state,
        onPrevious = viewModel::previous,
        onNext = viewModel::next,
        onSave = viewModel::setGoal,
        onDelete = viewModel::removeGoal,
    )
}

@Composable
fun GoalsScreen(
    state: GoalsUiState,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSave: (GoalTarget, Int) -> Unit,
    onDelete: (GoalTarget) -> Unit,
) {
    var dialog by remember { mutableStateOf<GoalDialog?>(null) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPrevious, modifier = Modifier.testTag("goals-prev")) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.goals_previous))
                }
                Text(periodTitle(state.week), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = onNext, enabled = !state.isCurrentWeek, modifier = Modifier.testTag("goals-next")) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.goals_next))
                }
            }
            HorizontalDivider()

            if (state.goals.isEmpty()) {
                Column(Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.goals_empty), color = MaterialTheme.colorScheme.outline)
                    if (state.isCurrentWeek) {
                        Text(
                            stringResource(R.string.goals_empty_hint),
                            Modifier.padding(top = 8.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                }
            } else {
                LazyColumn(Modifier.weight(1f)) {
                    items(state.goals, key = { it.target.toString() }) { goal ->
                        GoalRow(
                            goal = goal,
                            showRemaining = state.isCurrentWeek,
                            onClick = if (state.isCurrentWeek) ({ dialog = GoalDialog.Edit(goal) }) else null,
                        )
                    }
                }
            }
        }

        if (state.isCurrentWeek && state.candidates.isNotEmpty()) {
            FloatingActionButton(
                onClick = { dialog = GoalDialog.New },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp).testTag("goals-add"),
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.goals_add))
            }
        }
    }

    // Неделя перестала быть текущей (листание или полночь понедельника) — править больше нечего.
    LaunchedEffect(state.isCurrentWeek) {
        if (!state.isCurrentWeek) dialog = null
    }

    dialog?.let { d ->
        key(d) {
            GoalEditDialog(
                dialog = d,
                candidates = state.candidates,
                onDismiss = { dialog = null },
                onSave = { target, minutes ->
                    onSave(target, minutes)
                    dialog = null
                },
                onDelete = { target ->
                    onDelete(target)
                    dialog = null
                },
            )
        }
    }
}

@Composable
private fun GoalRow(goal: GoalProgress, showRemaining: Boolean, onClick: (() -> Unit)?) {
    val color = Color(goal.color)
    Column(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(start = if (goal.isGroup) 16.dp else 40.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(if (goal.isGroup) 12.dp else 10.dp).clip(CircleShape).background(color))
            Text(
                goal.name,
                Modifier.weight(1f).padding(start = 12.dp),
                style = if (goal.isGroup) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
            )
            Text(
                stringResource(R.string.goals_progress, formatDurationMs(goal.doneMillis), formatDuration(goal.goalMinutes)),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (goal.reached) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = stringResource(R.string.goals_reached),
                    modifier = Modifier.padding(start = 4.dp).size(18.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
        LinearProgressIndicator(
            progress = { goal.fraction },
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            color = color,
            trackColor = color.copy(alpha = 0.2f),
        )
        if (showRemaining && !goal.reached) {
            Text(
                stringResource(R.string.goals_remaining, formatDurationMs(goal.remainingMillis)),
                Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

@Composable
private fun GoalEditDialog(
    dialog: GoalDialog,
    candidates: List<GoalCandidate>,
    onDismiss: () -> Unit,
    onSave: (GoalTarget, Int) -> Unit,
    onDelete: (GoalTarget) -> Unit,
) {
    val editing = (dialog as? GoalDialog.Edit)?.goal
    var target by remember { mutableStateOf(editing?.target ?: candidates.firstOrNull()?.target) }
    var minutes by remember { mutableIntStateOf(editing?.goalMinutes ?: DEFAULT_MINUTES) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(editing?.name ?: stringResource(R.string.goals_add)) },
        text = {
            Column {
                if (editing == null) {
                    Column(Modifier.heightIn(max = 240.dp).verticalScroll(rememberScrollState())) {
                        candidates.forEach { c ->
                            val isCategory = c.target is GoalTarget.OfCategory
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .selectable(selected = target == c.target, onClick = { target = c.target }, role = Role.RadioButton)
                                    .padding(start = if (isCategory) 24.dp else 0.dp, top = 2.dp, bottom = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(selected = target == c.target, onClick = null)
                                Box(Modifier.padding(start = 8.dp).size(10.dp).clip(CircleShape).background(Color(c.color)))
                                Text(c.name, Modifier.padding(start = 8.dp))
                            }
                        }
                    }
                    Spacer(Modifier.size(12.dp))
                }
                Text(stringResource(R.string.goals_minimum), style = MaterialTheme.typography.labelLarge)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RepeatButton(
                        onClick = { minutes = (minutes - STEP_MINUTES).coerceAtLeast(STEP_MINUTES) },
                        modifier = Modifier.testTag("goal-less"),
                    ) { Text("−", style = MaterialTheme.typography.titleLarge) }
                    Text(
                        formatDuration(minutes),
                        Modifier.weight(1f).testTag("goal-minutes"),
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                    )
                    RepeatButton(
                        onClick = { minutes = (minutes + STEP_MINUTES).coerceAtMost(MAX_MINUTES) },
                        modifier = Modifier.testTag("goal-more"),
                    ) { Text("+", style = MaterialTheme.typography.titleLarge) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { target?.let { onSave(it, minutes) } }, enabled = target != null) {
                Text(stringResource(R.string.dialog_save))
            }
        },
        dismissButton = {
            Row {
                if (editing != null) {
                    TextButton(onClick = { onDelete(editing.target) }) { Text(stringResource(R.string.goals_delete)) }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_cancel)) }
            }
        },
    )
}
