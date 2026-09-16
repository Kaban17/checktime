package dev.boar.checktime.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.boar.checktime.R
import dev.boar.checktime.domain.TimeMath
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/**
 * HH:mm на дате [current]; если вне [range] — пробуем соседние сутки (запись может переходить полночь).
 * Если ничего не попало в диапазон, возвращаем вариант на дате current — UI подсветит ошибку.
 */
fun pickerToEpoch(current: Long, hour: Int, minute: Int, range: LongRange, zone: ZoneId): Long {
    val date = Instant.ofEpochMilli(current).atZone(zone).toLocalDate()
    val sameDay = date.atTime(LocalTime.of(hour, minute)).atZone(zone).toInstant().toEpochMilli()
    if (sameDay in range) return sameDay
    for (shift in listOf(1L, -1L)) {
        val candidate = date.plusDays(shift).atTime(LocalTime.of(hour, minute)).atZone(zone).toInstant().toEpochMilli()
        if (candidate in range) return candidate
    }
    return sameDay
}

/** Время с кнопками ±[stepMinutes] и TimePicker по тапу; значение зажимается в [range] (включительно). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeStepperField(
    value: Long,
    range: LongRange,
    stepMinutes: Int,
    onValueChange: (Long) -> Unit,
    modifier: Modifier = Modifier,
    zone: ZoneId = ZoneId.systemDefault(),
) {
    var showPicker by remember { mutableStateOf(false) }
    val step = stepMinutes * TimeMath.MINUTE_MS
    val inRange = value in range

    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        RepeatButton(
            onClick = { onValueChange((value - step).coerceIn(range.first, range.last)) },
            modifier = Modifier.testTag("time-dec"),
        ) { Text("−", style = MaterialTheme.typography.titleLarge) }
        Text(
            formatTime(value, zone),
            modifier = Modifier
                .clickable { showPicker = true }
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .testTag("time-value"),
            style = MaterialTheme.typography.headlineSmall,
            color = if (inRange) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
        )
        RepeatButton(
            onClick = { onValueChange((value + step).coerceIn(range.first, range.last)) },
            modifier = Modifier.testTag("time-inc"),
        ) { Icon(Icons.Default.Add, contentDescription = null) }
    }

    if (showPicker) {
        val local = Instant.ofEpochMilli(value).atZone(zone).toLocalTime()
        val pickerState = rememberTimePickerState(initialHour = local.hour, initialMinute = local.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    onValueChange(pickerToEpoch(value, pickerState.hour, pickerState.minute, range, zone))
                    showPicker = false
                }) { Text(stringResource(R.string.dialog_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text(stringResource(R.string.dialog_cancel)) }
            },
            text = { TimePicker(state = pickerState) },
        )
    }
}
