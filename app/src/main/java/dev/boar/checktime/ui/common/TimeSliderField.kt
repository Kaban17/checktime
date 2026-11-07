package dev.boar.checktime.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
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
    val local = Instant.ofEpochMilli(current).atZone(zone)
    val time = LocalTime.of(hour, minute, local.second, local.nano)
    val date = local.toLocalDate()
    val sameDay = date.atTime(time).atZone(zone).toInstant().toEpochMilli()
    if (sameDay in range) return sameDay
    for (shift in listOf(1L, -1L)) {
        val candidate = date.plusDays(shift).atTime(time).atZone(zone).toInstant().toEpochMilli()
        if (candidate in range) return candidate
    }
    return sameDay
}

/**
 * Значение со слайдера: прилипает к сетке [stepMinutes] по местным часам (12:05, 12:10…),
 * края диапазона достижимы точно, даже если не лежат на сетке.
 */
fun snapTime(raw: Long, range: LongRange, stepMinutes: Int, zone: ZoneId): Long {
    if (raw <= range.first) return range.first
    if (raw >= range.last) return range.last
    val step = stepMinutes * TimeMath.MINUTE_MS
    val offset = zone.rules.getOffset(Instant.ofEpochMilli(raw)).totalSeconds * 1000L
    val snapped = Math.floorDiv(raw + offset + step / 2, step) * step - offset
    return snapped.coerceIn(range.first, range.last)
}

/** Время со слайдером по [range] (включительно, шаг [stepMinutes]) и TimePicker по тапу на значение. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeSliderField(
    value: Long,
    range: LongRange,
    stepMinutes: Int,
    onValueChange: (Long) -> Unit,
    modifier: Modifier = Modifier,
    zone: ZoneId = ZoneId.systemDefault(),
) {
    var showPicker by remember { mutableStateOf(false) }
    val inRange = value in range
    val empty = range.first > range.last
    val pickLabel = stringResource(R.string.time_pick)
    // Слайдер работает в минутах от начала диапазона: epoch-мс во Float не помещаются без потери точности.
    val spanMinutes = if (empty) 0f else ((range.last - range.first) / TimeMath.MINUTE_MS).toFloat()

    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            formatTime(value, zone),
            modifier = Modifier
                .clickable(onClickLabel = pickLabel) { showPicker = true }
                .padding(end = 16.dp, top = 8.dp, bottom = 8.dp)
                .testTag("time-value"),
            style = MaterialTheme.typography.headlineSmall,
            color = if (inRange) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
        )
        CompactSlider(
            value = if (empty) 0f else ((value.coerceIn(range.first, range.last) - range.first).toFloat() / TimeMath.MINUTE_MS),
            onValueChange = { minutes ->
                val raw = if (minutes >= spanMinutes) range.last else range.first + (minutes * TimeMath.MINUTE_MS).toLong()
                onValueChange(snapTime(raw, range, stepMinutes, zone))
            },
            valueRange = 0f..maxOf(spanMinutes, 1f),
            enabled = !empty && spanMinutes > 0f,
            modifier = Modifier.weight(1f).testTag("time-slider"),
        )
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
