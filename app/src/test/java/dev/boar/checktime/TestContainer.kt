package dev.boar.checktime

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import dev.boar.checktime.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File

/** AppContainer на in-memory Room и временном DataStore. Вызывающий закрывает container.db. */
fun testContainer(context: Context, now: () -> Long): AppContainer {
    val file = File.createTempFile("settings", ".preferences_pb").also { it.delete() }
    val store = PreferenceDataStoreFactory.create(scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)) { file }
    return AppContainer(context, db = inMemoryDb(), settings = SettingsRepository(store), now = now)
}
