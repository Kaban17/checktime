package dev.boar.checktime.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.boar.checktime.AppContainer
import dev.boar.checktime.data.Settings
import dev.boar.checktime.data.TrackingState
import dev.boar.checktime.domain.TimeMath
import dev.boar.checktime.domain.exportAllCsv
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.OutputStream
import java.time.Instant
import java.time.ZoneId

data class SettingsUiState(val settings: Settings, val tracking: TrackingState?)

class SettingsViewModel(
    private val container: AppContainer,
    private val zone: ZoneId = ZoneId.systemDefault(),
) : ViewModel() {
    val state: StateFlow<SettingsUiState?> =
        combine(container.settings.settings, container.timeline.observeTrackingState()) { s, t -> SettingsUiState(s, t) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Возвращают Job, чтобы тесты могли дождаться записи и перестановки будильника. */
    fun setInterval(minutes: Int): Job = viewModelScope.launch {
        container.settings.setIntervalMinutes(minutes)
        rearm()
    }

    fun setSnooze(minutes: Int): Job = viewModelScope.launch { container.settings.setSnoozeMinutes(minutes) }

    fun setStep(minutes: Int): Job = viewModelScope.launch { container.settings.setStepMinutes(minutes) }

    fun startTracking(): Job = viewModelScope.launch {
        container.timeline.startTracking(container.now())
        rearm()
    }

    /** Имя файла по умолчанию для экспорта: checktime-2026-11-07.csv. */
    fun exportFileName(): String = "checktime-${Instant.ofEpochMilli(container.now()).atZone(zone).toLocalDate()}.csv"

    /** Пишет все записи в CSV; [open] даёт поток (null — не открылся). Результат — число записей или null при ошибке. */
    fun exportCsv(open: () -> OutputStream?, onResult: (Int?) -> Unit): Job = viewModelScope.launch {
        val count = try {
            val (csv, count) = exportAllCsv(container.db, zone)
            withContext(Dispatchers.IO) {
                (open() ?: throw IOException("no stream")).use { it.write(csv.toByteArray(Charsets.UTF_8)) }
            }
            count
        } catch (e: IOException) {
            null
        } catch (e: SecurityException) {
            null // провайдер документа отозвал доступ
        }
        onResult(count)
    }

    /** Будильник = accountedUntil + текущий интервал; без начатого учёта будильника нет. */
    private suspend fun rearm() {
        val tracking = container.timeline.trackingState() ?: return
        val settings = container.settings.settings.first()
        // Не ставим будильник в прошлое: он сработал бы немедленно поверх экрана настроек.
        val at = maxOf(TimeMath.nextAlarmAt(tracking.accountedUntil, settings.intervalMinutes), container.now() + TimeMath.MINUTE_MS)
        container.alarms.schedule(at)
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { SettingsViewModel(container) }
        }
    }
}
