package dev.boar.checktime.ui.stats

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.boar.checktime.R
import dev.boar.checktime.domain.PeriodKind
import dev.boar.checktime.domain.StatsPeriod
import dev.boar.checktime.domain.firstDate
import dev.boar.checktime.domain.lastDate
import dev.boar.checktime.ui.common.formatDurationMs
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private val dayFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.forLanguageTag("ru"))
private val shortFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", Locale.forLanguageTag("ru"))

@Composable
fun StatsRoute(viewModel: StatsViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    StatsScreen(
        state = state,
        onSelectKind = viewModel::selectKind,
        onSelectCustom = viewModel::selectCustom,
        onPrevious = viewModel::previous,
        onNext = viewModel::next,
    )
}

@Composable
fun StatsScreen(
    state: StatsUiState,
    onSelectKind: (PeriodKind) -> Unit,
    onSelectCustom: (LocalDate, LocalDate) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    var showPicker by rememberSaveable { mutableStateOf(false) }
    var expandedIds by rememberSaveable { mutableStateOf(setOf<Long>()) }

    Column(Modifier.fillMaxSize()) {
        PeriodChips(state.kind, onSelectKind, onOpenPicker = { showPicker = true })
        PeriodHeader(state, onPrevious, onNext)
        HorizontalDivider()

        if (state.result.totalMillis == 0L) {
            Text(
                stringResource(R.string.stats_empty),
                Modifier.padding(16.dp),
                color = MaterialTheme.colorScheme.outline,
            )
        } else {
            LazyColumn(Modifier.weight(1f)) {
                item {
                    Text(
                        stringResource(R.string.stats_total, formatDurationMs(state.result.totalMillis)),
                        Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                state.result.groups.forEach { group ->
                    item(key = "g${group.group.id}") {
                        val open = group.group.id in expandedIds
                        Column(Modifier.fillMaxWidth()) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        expandedIds = if (group.group.id in expandedIds) {
                                            expandedIds - group.group.id
                                        } else {
                                            expandedIds + group.group.id
                                        }
                                    }
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(Modifier.size(12.dp).clip(CircleShape).background(Color(group.group.color)))
                                Text(
                                    group.group.name,
                                    Modifier.weight(1f).padding(start = 12.dp),
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                Text(formatDurationMs(group.millis), style = MaterialTheme.typography.bodyMedium)
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    percent(group.share),
                                    Modifier.width(48.dp),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.outline,
                                    textAlign = TextAlign.End,
                                )
                            }
                            if (open) {
                                group.categories.forEach { stat ->
                                    Row(
                                        Modifier.fillMaxWidth().padding(start = 40.dp, end = 16.dp, bottom = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Box(Modifier.size(10.dp).clip(CircleShape).background(Color(stat.category.color)))
                                        Text(
                                            stat.category.name,
                                            Modifier.weight(1f).padding(start = 12.dp),
                                            style = MaterialTheme.typography.bodyMedium,
                                        )
                                        Text(formatDurationMs(stat.millis), style = MaterialTheme.typography.bodyMedium)
                                        Spacer(Modifier.width(12.dp))
                                        Text(
                                            percent(stat.share),
                                            Modifier.width(48.dp),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.outline,
                                            textAlign = TextAlign.End,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
                item {
                    Text(
                        stringResource(R.string.stats_by_category),
                        Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                item {
                    CategoryBarChart(
                        state.result.categories,
                        Modifier.padding(horizontal = 16.dp).testTag("stats-chart"),
                    )
                }
            }
        }
    }

    if (showPicker) {
        CustomRangeDialog(
            onDismiss = { showPicker = false },
            onConfirm = { from, to ->
                onSelectCustom(from, to)
                showPicker = false
            },
        )
    }
}

@Composable
private fun PeriodChips(kind: PeriodKind?, onSelectKind: (PeriodKind) -> Unit, onOpenPicker: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(selected = kind == PeriodKind.DAY, onClick = { onSelectKind(PeriodKind.DAY) }, label = { Text(stringResource(R.string.stats_day)) })
        FilterChip(selected = kind == PeriodKind.WEEK, onClick = { onSelectKind(PeriodKind.WEEK) }, label = { Text(stringResource(R.string.stats_week)) })
        FilterChip(selected = kind == PeriodKind.MONTH, onClick = { onSelectKind(PeriodKind.MONTH) }, label = { Text(stringResource(R.string.stats_month)) })
        FilterChip(selected = kind == null, onClick = onOpenPicker, label = { Text(stringResource(R.string.stats_custom)) })
    }
}

@Composable
private fun PeriodHeader(state: StatsUiState, onPrevious: () -> Unit, onNext: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrevious, modifier = Modifier.testTag("stats-prev")) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.stats_previous))
        }
        Text(
            periodTitle(state.period),
            Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
        )
        IconButton(onClick = onNext, enabled = state.canGoForward, modifier = Modifier.testTag("stats-next")) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.stats_next))
        }
    }
}

internal fun periodTitle(period: StatsPeriod): String = when (period) {
    is StatsPeriod.Day -> period.date.format(dayFormatter)
    else -> "${period.firstDate().format(shortFormatter)} – ${period.lastDate().format(shortFormatter)}"
}

private fun percent(share: Float): String = "${(share * 100).roundToInt()}%"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomRangeDialog(onDismiss: () -> Unit, onConfirm: (LocalDate, LocalDate) -> Unit) {
    val pickerState = rememberDateRangePickerState()
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val from = pickerState.selectedStartDateMillis?.toLocalDateUtc()
                    val to = pickerState.selectedEndDateMillis?.toLocalDateUtc() ?: from
                    if (from != null && to != null) onConfirm(from, to)
                },
                enabled = pickerState.selectedStartDateMillis != null,
            ) { Text(stringResource(R.string.dialog_ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_cancel)) } },
        text = { DateRangePicker(state = pickerState) },
    )
}

/** DateRangePicker отдаёт полночь UTC — переводим в дату без сдвига часовым поясом. */
private fun Long.toLocalDateUtc(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneId.of("UTC")).toLocalDate()
