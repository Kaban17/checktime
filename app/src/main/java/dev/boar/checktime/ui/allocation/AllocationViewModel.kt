package dev.boar.checktime.ui.allocation

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.boar.checktime.AppContainer
import dev.boar.checktime.data.GroupWithCategories
import dev.boar.checktime.domain.AllocateResult
import dev.boar.checktime.domain.AllocationDraft
import dev.boar.checktime.domain.TimeMath
import dev.boar.checktime.scheduler.TailNotifier
import dev.boar.checktime.scheduler.UnlockWatcherService
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface AllocationUiState {
    data object Loading : AllocationUiState
    /** Учёт не начат или хвост короче минуты — экран закрывается. */
    data object Empty : AllocationUiState
    data class Ready(
        val start: Long,
        val end: Long,
        val groups: List<GroupWithCategories>,
        val draft: AllocationDraft,
    ) : AllocationUiState
}

sealed interface AllocationEvent {
    data object Saved : AllocationEvent
    data object Postponed : AllocationEvent
    data object Stale : AllocationEvent
}

class AllocationViewModel(
    private val appContext: Context,
    private val container: AppContainer,
) : ViewModel() {
    private val _state = MutableStateFlow<AllocationUiState>(AllocationUiState.Loading)
    val state: StateFlow<AllocationUiState> = _state.asStateFlow()

    private val _events = Channel<AllocationEvent>(Channel.BUFFERED)
    val events: Flow<AllocationEvent> = _events.receiveAsFlow()

    init {
        load()
    }

    /** Фиксирует end = now в момент загрузки; всё, что натикает дальше, уедет в следующий хвост. */
    fun load() {
        viewModelScope.launch {
            val tracking = container.timeline.trackingState()
            val total = tracking?.let { TimeMath.tailMinutes(it.accountedUntil, container.now()) } ?: 0
            if (tracking == null || total < 1) {
                _state.value = AllocationUiState.Empty
                return@launch
            }
            val settings = container.settings.settings.first()
            val groups = container.categories.observeTree().first()
                .map { g -> g.copy(categories = g.categories.filter { !it.archived }) }
                .filter { it.categories.isNotEmpty() }
            val order = groups.flatMap { g -> g.categories.map { it.id } }
            _state.value = AllocationUiState.Ready(
                start = tracking.accountedUntil,
                end = tracking.accountedUntil + total * TimeMath.MINUTE_MS,
                groups = groups,
                draft = AllocationDraft(totalMinutes = total, stepMinutes = settings.stepMinutes, order = order),
            )
        }
    }

    fun edit(transform: (AllocationDraft) -> AllocationDraft) {
        _state.update { s -> if (s is AllocationUiState.Ready) s.copy(draft = transform(s.draft)) else s }
    }

    fun save() {
        viewModelScope.launch {
            val s = _state.value as? AllocationUiState.Ready ?: return@launch
            if (!s.draft.isComplete) return@launch
            when (container.timeline.allocate(expectedAccountedUntil = s.start, allocations = s.draft.allocations())) {
                AllocateResult.Saved -> {
                    val settings = container.settings.settings.first()
                    container.alarms.schedule(TimeMath.nextAlarmAt(s.end, settings.intervalMinutes))
                    UnlockWatcherService.stop(appContext)
                    TailNotifier.sync(appContext, container)
                    _events.send(AllocationEvent.Saved)
                }
                AllocateResult.Stale -> {
                    _events.send(AllocationEvent.Stale)
                    load()
                }
            }
        }
    }

    fun postpone() {
        viewModelScope.launch {
            val settings = container.settings.settings.first()
            container.alarms.schedule(TimeMath.snoozeAlarmAt(container.now(), settings.snoozeMinutes))
            UnlockWatcherService.stop(appContext)
            _events.send(AllocationEvent.Postponed)
        }
    }

    companion object {
        fun factory(appContext: Context, container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { AllocationViewModel(appContext, container) }
        }
    }
}
