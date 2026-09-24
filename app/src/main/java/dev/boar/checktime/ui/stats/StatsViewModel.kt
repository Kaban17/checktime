package dev.boar.checktime.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.boar.checktime.AppContainer
import dev.boar.checktime.domain.PeriodKind
import dev.boar.checktime.domain.StatsPeriod
import dev.boar.checktime.domain.StatsResult
import dev.boar.checktime.domain.aggregate
import dev.boar.checktime.domain.clipSegments
import dev.boar.checktime.domain.dayCount
import dev.boar.checktime.domain.lastDate
import dev.boar.checktime.domain.periodOf
import dev.boar.checktime.domain.range
import dev.boar.checktime.domain.shift
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class StatsUiState(
    val period: StatsPeriod,
    /** null — произвольный диапазон. */
    val kind: PeriodKind?,
    val result: StatsResult,
    val canGoForward: Boolean,
    val dayCount: Int,
)

@OptIn(ExperimentalCoroutinesApi::class)
class StatsViewModel(
    private val container: AppContainer,
    val zone: ZoneId = ZoneId.systemDefault(),
) : ViewModel() {
    private data class Selection(val period: StatsPeriod, val kind: PeriodKind?)

    private val selection = MutableStateFlow(Selection(StatsPeriod.Day(currentDate()), PeriodKind.DAY))

    private val periodSegments = selection.flatMapLatest { sel ->
        val range = sel.period.range(zone)
        container.timeline.observeSegments(range.start, range.end)
            .map { sel to clipSegments(it, range.start, range.end) }
    }

    /** Раз в 30 с пересчитываем «сегодня», не трогая базу. */
    private val ticker = flow {
        while (true) {
            emit(Unit)
            delay(30_000)
        }
    }

    val state: StateFlow<StatsUiState> = combine(
        periodSegments,
        container.categories.observeTree(),
        ticker,
    ) { (sel, segments), tree, _ ->
        StatsUiState(
            period = sel.period,
            kind = sel.kind,
            result = aggregate(segments, tree),
            canGoForward = sel.period.lastDate() < currentDate(),
            dayCount = sel.period.dayCount(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyState())

    fun selectKind(kind: PeriodKind) = selection.update { Selection(periodOf(kind, currentDate()), kind) }

    fun selectCustom(from: LocalDate, to: LocalDate) =
        selection.update { Selection(StatsPeriod.Custom(minOf(from, to), maxOf(from, to)), null) }

    fun previous() = selection.update { it.copy(period = it.period.shift(-1)) }

    /** Вперёд только пока период не накрыл сегодняшний день. */
    fun next() = selection.update {
        if (it.period.lastDate() < currentDate()) it.copy(period = it.period.shift(1)) else it
    }

    private fun currentDate(): LocalDate = Instant.ofEpochMilli(container.now()).atZone(zone).toLocalDate()

    private fun emptyState() = StatsUiState(
        period = selection.value.period,
        kind = selection.value.kind,
        result = StatsResult(0L, emptyList(), emptyList()),
        canGoForward = false,
        dayCount = selection.value.period.dayCount(),
    )

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { StatsViewModel(container) }
        }
    }
}
