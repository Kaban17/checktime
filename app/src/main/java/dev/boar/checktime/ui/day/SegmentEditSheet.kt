package dev.boar.checktime.ui.day

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.boar.checktime.R
import dev.boar.checktime.domain.TimeMath
import dev.boar.checktime.ui.common.TimeStepperField
import dev.boar.checktime.ui.common.formatDurationMs
import dev.boar.checktime.ui.common.formatTime
import java.time.ZoneId

/** Режимы шторки: меню действий, выбор категории, выбор времени для одной из операций. */
private sealed interface Mode {
    data object Menu : Mode
    data object PickCategory : Mode
    data class PickTime(val kind: TimeKind) : Mode
}

private enum class TimeKind { Split, MoveStart, MoveEnd }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SegmentEditSheet(
    state: EditorState,
    onDismiss: () -> Unit,
    onChangeCategory: (Long) -> Unit,
    onSplit: (Long) -> Unit,
    onMoveStart: (Long) -> Unit,
    onMoveEnd: (Long) -> Unit,
    onMergePrevious: () -> Unit,
    onMergeNext: () -> Unit,
    zone: ZoneId = ZoneId.systemDefault(),
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        SegmentEditContent(state, onChangeCategory, onSplit, onMoveStart, onMoveEnd, onMergePrevious, onMergeNext, zone)
    }
}

/** Содержимое шторки отдельно от ModalBottomSheet — так его можно тестировать под Robolectric. */
@Composable
internal fun SegmentEditContent(
    state: EditorState,
    onChangeCategory: (Long) -> Unit,
    onSplit: (Long) -> Unit,
    onMoveStart: (Long) -> Unit,
    onMoveEnd: (Long) -> Unit,
    onMergePrevious: () -> Unit,
    onMergeNext: () -> Unit,
    zone: ZoneId,
) {
    var mode by remember { mutableStateOf<Mode>(Mode.Menu) }
    val s = state.segment

    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(14.dp).clip(CircleShape).background(Color(state.category?.color ?: 0xFF9E9E9E.toInt())))
            Spacer(Modifier.width(12.dp))
            Text(
                state.category?.name ?: stringResource(R.string.day_unknown_category),
                style = MaterialTheme.typography.titleMedium,
            )
        }
        Text(
            "${formatTime(s.startAt, zone)} – ${formatTime(s.endAt, zone)} · ${formatDurationMs(s.endAt - s.startAt)}",
            Modifier.padding(top = 4.dp, bottom = 16.dp),
            style = MaterialTheme.typography.bodyLarge,
        )

        when (val m = mode) {
            Mode.Menu -> Menu(
                state,
                onPickCategory = { mode = Mode.PickCategory },
                onSplit = { mode = Mode.PickTime(TimeKind.Split) },
                onMoveStart = { mode = Mode.PickTime(TimeKind.MoveStart) },
                onMoveEnd = { mode = Mode.PickTime(TimeKind.MoveEnd) },
                onMergePrevious = onMergePrevious,
                onMergeNext = onMergeNext,
            )
            Mode.PickCategory -> CategoryList(state, onChangeCategory, onBack = { mode = Mode.Menu })
            is Mode.PickTime -> TimeEditor(
                state, m.kind, zone,
                onConfirm = { at ->
                    when (m.kind) {
                        TimeKind.Split -> onSplit(at)
                        TimeKind.MoveStart -> onMoveStart(at)
                        TimeKind.MoveEnd -> onMoveEnd(at)
                    }
                },
                onBack = { mode = Mode.Menu },
            )
        }
    }
}

@Composable
private fun Menu(
    state: EditorState,
    onPickCategory: () -> Unit,
    onSplit: () -> Unit,
    onMoveStart: () -> Unit,
    onMoveEnd: () -> Unit,
    onMergePrevious: () -> Unit,
    onMergeNext: () -> Unit,
) {
    val hasPrev = state.previous != null
    val hasNext = state.next != null
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onPickCategory, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.edit_category)) }
        OutlinedButton(onClick = onSplit, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.edit_split)) }
        OutlinedButton(onClick = onMoveStart, enabled = hasPrev, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.edit_move_start)) }
        OutlinedButton(onClick = onMoveEnd, enabled = hasNext, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.edit_move_end)) }
        OutlinedButton(onClick = onMergePrevious, enabled = hasPrev, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.edit_merge_previous)) }
        OutlinedButton(onClick = onMergeNext, enabled = hasNext, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.edit_merge_next)) }
    }
}

@Composable
private fun CategoryList(state: EditorState, onChangeCategory: (Long) -> Unit, onBack: () -> Unit) {
    Column {
        state.groups.forEach { g ->
            Text(g.group.name, Modifier.padding(vertical = 6.dp), style = MaterialTheme.typography.labelLarge, color = Color(g.group.color))
            g.categories.forEach { c ->
                Row(
                    Modifier.fillMaxWidth().clickable { onChangeCategory(c.id) }.padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(12.dp).clip(CircleShape).background(Color(c.color)))
                    Spacer(Modifier.width(12.dp))
                    Text(c.name, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        TextButton(onClick = onBack) { Text(stringResource(R.string.dialog_cancel)) }
    }
}

/**
 * Диапазон допустимых значений — строго внутри внешних границ (шаг = минута):
 * split — внутри записи; moveStart — (prev.startAt, segment.endAt); moveEnd — (segment.startAt, next.endAt).
 */
@Composable
private fun TimeEditor(state: EditorState, kind: TimeKind, zone: ZoneId, onConfirm: (Long) -> Unit, onBack: () -> Unit) {
    val s = state.segment
    val minute = TimeMath.MINUTE_MS
    val (range, initial, title) = when (kind) {
        TimeKind.Split -> Triple(
            (s.startAt + minute)..(s.endAt - minute),
            s.startAt + ((s.endAt - s.startAt) / 2 / minute) * minute,
            R.string.edit_split,
        )
        TimeKind.MoveStart -> Triple(
            ((state.previous?.startAt ?: s.startAt) + minute)..(s.endAt - minute),
            s.startAt,
            R.string.edit_move_start,
        )
        TimeKind.MoveEnd -> Triple(
            (s.startAt + minute)..((state.next?.endAt ?: s.endAt) - minute),
            s.endAt,
            R.string.edit_move_end,
        )
    }
    var value by remember(kind) { mutableStateOf(initial) }
    Column {
        Text(stringResource(title), style = MaterialTheme.typography.labelLarge)
        TimeStepperField(
            value = value,
            range = range,
            stepMinutes = state.stepMinutes,
            onValueChange = { value = it },
            modifier = Modifier.padding(vertical = 8.dp),
            zone = zone,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.dialog_cancel)) }
            Button(onClick = { onConfirm(value) }, enabled = value in range, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.allocation_done))
            }
        }
    }
}
