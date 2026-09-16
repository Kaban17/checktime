package dev.boar.checktime.ui.day

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.boar.checktime.R
import dev.boar.checktime.scheduler.PendingNotification
import dev.boar.checktime.ui.common.formatDuration
import dev.boar.checktime.ui.common.formatDurationMs
import dev.boar.checktime.ui.common.formatTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.forLanguageTag("ru"))

@Composable
fun DayRoute(viewModel: DayViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    DayScreen(
        state = state,
        onPrevious = viewModel::previousDay,
        onNext = viewModel::nextDay,
        onToday = viewModel::today,
        onAllocate = { context.startActivity(PendingNotification.allocationIntent(context)) },
    )
}

@Composable
fun DayScreen(
    state: DayUiState,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
    onAllocate: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onPrevious) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = null) }
            Text(
                state.date.format(dateFormatter).replaceFirstChar { it.uppercase() },
                Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
            )
            if (!state.isToday) {
                TextButton(onClick = onToday) { Text(stringResource(R.string.day_today)) }
            }
            IconButton(onClick = onNext, enabled = !state.isToday) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
            }
        }

        if (!state.trackingStarted) {
            Text(
                stringResource(R.string.day_not_started),
                Modifier.padding(16.dp),
                color = MaterialTheme.colorScheme.outline,
            )
        }
        if (state.clockWentBack) {
            Text(
                stringResource(R.string.day_clock_went_back),
                Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (state.tailMinutes >= 1) {
            Card(
                Modifier.fillMaxWidth().padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.day_tail, formatDuration(state.tailMinutes)),
                        Modifier.weight(1f),
                    )
                    Button(onClick = onAllocate) { Text(stringResource(R.string.day_allocate)) }
                }
            }
        }

        LazyColumn(Modifier.weight(1f)) {
            if (state.totals.isNotEmpty()) {
                item { SectionTitle(stringResource(R.string.day_totals)) }
                items(state.totals, key = { "t${it.category.id}" }) { t ->
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ColorDot(t.category.color)
                        Text("${t.group.name} / ${t.category.name}", Modifier.weight(1f).padding(start = 12.dp))
                        Text(formatDurationMs(t.millis), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
            }
            if (state.segments.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.day_empty),
                        Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            } else {
                item { SectionTitle(stringResource(R.string.day_segments)) }
                items(state.segments, key = { "s${it.startAt}" }) { s ->
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "${formatTime(s.startAt)}–${formatTime(s.endAt)}",
                            Modifier.width(96.dp),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        ColorDot(s.category?.color ?: 0xFF9E9E9E.toInt())
                        Spacer(Modifier.width(12.dp))
                        Text(s.category?.name ?: stringResource(R.string.day_unknown_category), Modifier.weight(1f))
                        Text(formatDurationMs(s.endAt - s.startAt), color = MaterialTheme.colorScheme.outline)
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun ColorDot(color: Int) {
    Box(Modifier.size(12.dp).clip(CircleShape).background(Color(color)))
}
