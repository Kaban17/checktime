package dev.boar.checktime

import android.app.Application
import android.content.Context
import androidx.room.Room
import dev.boar.checktime.data.AppDatabase
import dev.boar.checktime.data.CategoryRepository
import dev.boar.checktime.data.SettingsRepository
import dev.boar.checktime.data.settingsDataStore
import dev.boar.checktime.domain.TimelineRepository
import dev.boar.checktime.scheduler.AlarmScheduler
import dev.boar.checktime.scheduler.PendingNotification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Ручной DI. Параметры с дефолтами подменяются в тестах (см. testContainer). */
class AppContainer(
    context: Context,
    val db: AppDatabase = Room.databaseBuilder(context, AppDatabase::class.java, "checktime.db").build(),
    val settings: SettingsRepository = SettingsRepository(context.settingsDataStore),
    val now: () -> Long = System::currentTimeMillis,
) {
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val timeline = TimelineRepository(db)
    val categories = CategoryRepository(db.categoryDao())
    val alarms = AlarmScheduler(context)
}

class CheckTimeApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        PendingNotification.createChannels(this)
        container.applicationScope.launch { container.categories.seedDefaultsIfEmpty() }
    }
}

val Context.appContainer: AppContainer
    get() = (applicationContext as CheckTimeApp).container
