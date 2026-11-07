package dev.boar.checktime.ui.goals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.boar.checktime.AppContainer
import dev.boar.checktime.data.GroupWithCategories
import dev.boar.checktime.domain.GoalProgress
import dev.boar.checktime.domain.GoalTarget
import dev.boar.checktime.domain.PeriodKind
import dev.boar.checktime.domain.StatsPeriod
import dev.boar.checktime.domain.activeGoals
import dev.boar.checktime.domain.clipSegments
import dev.boar.checktime.domain.goalProgress
import dev.boar.checktime.domain.periodOf
import dev.boar.checktime.domain.range
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Объект, на который можно поставить новую цель. */
data class GoalCandidate(val target: GoalTarget, val name: String, val color: Int)

data class GoalsUiState(
    val week: StatsPeriod.Week,
    /** Текущая неделя: можно редактировать, вперёд листать некуда. */
    val isCurrentWeek: Boolean,
    val goals: List<GoalProgress>,
    /** Группы и неархивные категории без действующей цели; только для текущей недели. */
    val candidates: List<GoalCandidate>,
)

@OptIn(ExperimentalCoroutinesApi::class)
class GoalsViewModel(
    private val container: AppContainer,
    val zone: ZoneId = ZoneId.systemDefault(),
) : ViewModel() {
    /**
     * Сколько недель назад от текущей смотрим. Храним смещение, а не саму неделю: при 0 экран
     * после полуночи с воскресенья на понедельник сам переходит на новую неделю.
     */
    private val weeksBack = MutableStateFlow(0)

    /** Текущая неделя, раз в 30 с пересчитывается без обращения к базе. */
    private val thisWeek = flow {
        while (true) {
            emit(currentWeek())
            delay(30_000)
        }
    }.distinctUntilChanged()

    private val weekSegments = combine(thisWeek, weeksBack) { current, back ->
        StatsPeriod.Week(current.monday.minusWeeks(back.toLong())) to (back == 0)
    }.flatMapLatest { (w, isCurrent) ->
        val range = w.range(zone)
        container.timeline.observeSegments(range.start, range.end)
            .map { Triple(w, isCurrent, clipSegments(it, range.start, range.end)) }
    }

    val state: StateFlow<GoalsUiState> = combine(
        weekSegments,
        container.goals.observeAll(),
        container.categories.observeTree(),
    ) { (w, isCurrent, segments), goals, tree ->
        val active = activeGoals(goals, w.monday)
        GoalsUiState(
            week = w,
            isCurrentWeek = isCurrent,
            goals = goalProgress(active, segments, tree),
            candidates = if (isCurrent) candidates(tree, active.keys) else emptyList(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GoalsUiState(currentWeek(), true, emptyList(), emptyList()))

    fun previous() = weeksBack.update { it + 1 }

    /** Вперёд — не дальше текущей недели. */
    fun next() = weeksBack.update { (it - 1).coerceAtLeast(0) }

    fun setGoal(target: GoalTarget, minutes: Int) {
        viewModelScope.launch { container.goals.set(target, minutes, currentWeek().monday) }
    }

    fun removeGoal(target: GoalTarget) = setGoal(target, 0)

    private fun currentWeek(): StatsPeriod.Week {
        val today = Instant.ofEpochMilli(container.now()).atZone(zone).toLocalDate()
        return periodOf(PeriodKind.WEEK, today) as StatsPeriod.Week
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { GoalsViewModel(container) }
        }
    }
}

private fun candidates(tree: List<GroupWithCategories>, taken: Set<GoalTarget>): List<GoalCandidate> =
    tree.flatMap { g ->
        listOf(GoalCandidate(GoalTarget.OfGroup(g.group.id), g.group.name, g.group.color)) +
            g.categories.filter { !it.archived }.map { GoalCandidate(GoalTarget.OfCategory(it.id), it.name, it.color) }
    }.filter { it.target !in taken }
