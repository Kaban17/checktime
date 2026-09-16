package dev.boar.checktime.ui.day

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.boar.checktime.AppContainer
import dev.boar.checktime.R
import dev.boar.checktime.data.Category
import dev.boar.checktime.data.Group
import dev.boar.checktime.data.GroupWithCategories
import dev.boar.checktime.data.Segment
import dev.boar.checktime.data.TrackingState
import dev.boar.checktime.domain.EditResult
import dev.boar.checktime.domain.TimeMath
import dev.boar.checktime.domain.clipSegments
import dev.boar.checktime.domain.dayRange
import dev.boar.checktime.domain.totalsByCategory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class CategoryTotal(val category: Category, val group: Group, val millis: Long)

data class SegmentRow(val id: Long, val startAt: Long, val endAt: Long, val category: Category?, val group: Group?)

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

/** Что открыто в шторке правки: запись, её смежные соседи и данные для меню. */
data class EditorState(
    val segment: Segment,
    val previous: Segment?,
    val next: Segment?,
    val category: Category?,
    /** Группы только с неархивными категориями — для смены категории. */
    val groups: List<GroupWithCategories>,
    val stepMinutes: Int,
)

@OptIn(ExperimentalCoroutinesApi::class)
class DayViewModel(
    private val container: AppContainer,
    val zone: ZoneId = ZoneId.systemDefault(),
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

    private val _editor = MutableStateFlow<EditorState?>(null)
    val editor: StateFlow<EditorState?> = _editor.asStateFlow()

    private val _messages = Channel<Int>(Channel.BUFFERED)
    /** Id строкового ресурса для snackbar. */
    val messages: Flow<Int> = _messages.receiveAsFlow()

    private var editorJob: Job? = null

    fun openEditor(segmentId: Long) {
        editorJob?.cancel()
        editorJob = viewModelScope.launch {
            val segment = container.timeline.segment(segmentId) ?: return@launch
            val (prev, next) = container.timeline.neighbours(segment)
            val tree = container.categories.observeTree().first()
            val groups = tree
                .map { g -> g.copy(categories = g.categories.filter { !it.archived }) }
                .filter { it.categories.isNotEmpty() }
            val category = tree.flatMap { it.categories }.firstOrNull { it.id == segment.categoryId }
            val step = container.settings.settings.first().stepMinutes
            _editor.value = EditorState(segment, prev, next, category, groups, step)
        }
    }

    fun closeEditor() {
        editorJob?.cancel()
        _editor.value = null
    }

    fun changeCategory(categoryId: Long) = edit { e -> container.timeline.changeCategory(e.segment.id, categoryId) }
    fun split(at: Long) = edit { e -> container.timeline.split(e.segment.id, at) }
    fun moveStart(at: Long) = edit { e ->
        val prev = e.previous ?: return@edit EditResult.Rejected
        container.timeline.moveBoundary(prev.id, e.segment.id, at)
    }
    fun moveEnd(at: Long) = edit { e ->
        val next = e.next ?: return@edit EditResult.Rejected
        container.timeline.moveBoundary(e.segment.id, next.id, at)
    }
    fun mergeWithPrevious() = edit { e ->
        val prev = e.previous ?: return@edit EditResult.Rejected
        container.timeline.merge(keepId = e.segment.id, otherId = prev.id)
    }
    fun mergeWithNext() = edit { e ->
        val next = e.next ?: return@edit EditResult.Rejected
        container.timeline.merge(keepId = e.segment.id, otherId = next.id)
    }

    /** Общий каркас действия: закрыть шторку сразу же, затем выполнить над записью; при отказе — сообщить. */
    private fun edit(action: suspend (EditorState) -> EditResult) {
        val e = _editor.value ?: return
        editorJob?.cancel()
        _editor.value = null
        viewModelScope.launch {
            val result = runCatching { action(e) }
                .onFailure { Log.w("DayViewModel", "edit action failed", it) }
                .getOrDefault(EditResult.Rejected)
            if (result == EditResult.Rejected) _messages.send(R.string.edit_rejected)
        }
    }

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
            SegmentRow(s.id, s.startAt, s.endAt, hit?.first, hit?.second)
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
