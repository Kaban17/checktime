package dev.boar.checktime.ui.day

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.boar.checktime.AppContainer
import dev.boar.checktime.data.Category
import dev.boar.checktime.data.Group
import dev.boar.checktime.data.GroupWithCategories
import dev.boar.checktime.data.Segment
import dev.boar.checktime.data.TrackingState
import dev.boar.checktime.domain.TimeMath
import dev.boar.checktime.domain.clipSegments
import dev.boar.checktime.domain.dayRange
import dev.boar.checktime.domain.totalsByCategory
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

data class CategoryTotal(val category: Category, val group: Group, val millis: Long)

data class SegmentRow(val startAt: Long, val endAt: Long, val category: Category?, val group: Group?)

data class DayUiState(
    val date: LocalDate,
    val isToday: Boolean,
    val totals: List<CategoryTotal>,
    val segments: List<SegmentRow>,
    val trackingStarted: Boolean,
    val tailMinutes: Int,
    /** now < accountedUntil больше чем на минуту — часы перевели назад. */
    val clockWentBack: Boolean,
)

@OptIn(ExperimentalCoroutinesApi::class)
class DayViewModel(
    private val container: AppContainer,
    private val zone: ZoneId = ZoneId.systemDefault(),
) : ViewModel() {
    private val date = MutableStateFlow(currentDate())

    /** Раз в 30 с пересчитываем хвост и «сегодня», не трогая базу. */
    private val ticker = flow {
        while (true) {
            emit(Unit)
            delay(30_000)
        }
    }

    private val daySegments = date.flatMapLatest { d ->
        val range = dayRange(d, zone)
        container.timeline.observeSegments(range.start, range.end)
            .map { d to clipSegments(it, range.start, range.end) }
    }

    val state: StateFlow<DayUiState> = combine(
        daySegments,
        container.categories.observeTree(),
        container.timeline.observeTrackingState(),
        ticker,
    ) { (d, segments), tree, tracking, _ -> build(d, segments, tree, tracking) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyState(date.value))

    fun previousDay() = date.update { it.minusDays(1) }
    fun nextDay() = date.update { it.plusDays(1) }
    fun today() = date.update { currentDate() }

    private fun currentDate(): LocalDate = Instant.ofEpochMilli(container.now()).atZone(zone).toLocalDate()

    private fun build(
        d: LocalDate,
        segments: List<Segment>,
        tree: List<GroupWithCategories>,
        tracking: TrackingState?,
    ): DayUiState {
        val categories = tree.flatMap { g -> g.categories.map { it.id to (it to g.group) } }.toMap()
        val totals = totalsByCategory(segments)
            .mapNotNull { (id, ms) -> categories[id]?.let { (c, g) -> CategoryTotal(c, g, ms) } }
            .sortedByDescending { it.millis }
        val rows = segments.map { s ->
            val hit = categories[s.categoryId]
            SegmentRow(s.startAt, s.endAt, hit?.first, hit?.second)
        }
        val now = container.now()
        return DayUiState(
            date = d,
            isToday = d == currentDate(),
            totals = totals,
            segments = rows,
            trackingStarted = tracking != null,
            tailMinutes = tracking?.let { TimeMath.tailMinutes(it.accountedUntil, now) } ?: 0,
            clockWentBack = tracking != null && now < tracking.accountedUntil - TimeMath.MINUTE_MS,
        )
    }

    private fun emptyState(d: LocalDate) = DayUiState(
        date = d, isToday = true, totals = emptyList(), segments = emptyList(),
        trackingStarted = false, tailMinutes = 0, clockWentBack = false,
    )

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { DayViewModel(container) }
        }
    }
}
