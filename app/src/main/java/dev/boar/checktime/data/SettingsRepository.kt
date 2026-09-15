package dev.boar.checktime.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class Settings(
    val intervalMinutes: Int = 30,
    val snoozeMinutes: Int = 10,
    val stepMinutes: Int = 5,
) {
    companion object {
        const val MIN_MINUTES = 1
        const val MAX_MINUTES = 720
    }
}

val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val dataStore: DataStore<Preferences>) {
    private object Keys {
        val interval = intPreferencesKey("interval_minutes")
        val snooze = intPreferencesKey("snooze_minutes")
        val step = intPreferencesKey("step_minutes")
    }

    private val defaults = Settings()

    val settings: Flow<Settings> = dataStore.data.map { p ->
        Settings(
            intervalMinutes = p[Keys.interval] ?: defaults.intervalMinutes,
            snoozeMinutes = p[Keys.snooze] ?: defaults.snoozeMinutes,
            stepMinutes = p[Keys.step] ?: defaults.stepMinutes,
        )
    }

    suspend fun setIntervalMinutes(value: Int) = set(Keys.interval, value)
    suspend fun setSnoozeMinutes(value: Int) = set(Keys.snooze, value)
    suspend fun setStepMinutes(value: Int) = set(Keys.step, value)

    private suspend fun set(key: Preferences.Key<Int>, value: Int) {
        val clamped = value.coerceIn(Settings.MIN_MINUTES, Settings.MAX_MINUTES)
        dataStore.edit { it[key] = clamped }
    }
}
