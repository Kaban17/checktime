package dev.boar.checktime.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SettingsRepositoryTest {
    @get:Rule val tmp = TemporaryFolder()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var repo: SettingsRepository

    @Before
    fun setUp() {
        val store = PreferenceDataStoreFactory.create(scope = scope) { tmp.newFile("s.preferences_pb") }
        repo = SettingsRepository(store)
    }

    @After
    fun tearDown() = scope.cancel()

    @Test
    fun defaults() = runTest {
        assertEquals(Settings(intervalMinutes = 30, snoozeMinutes = 10, stepMinutes = 5), repo.settings.first())
    }

    @Test
    fun settersPersist() = runTest {
        repo.setIntervalMinutes(45)
        repo.setSnoozeMinutes(7)
        repo.setStepMinutes(10)
        assertEquals(Settings(45, 7, 10), repo.settings.first())
    }

    @Test
    fun valuesAreClamped() = runTest {
        repo.setIntervalMinutes(0)
        repo.setSnoozeMinutes(100_000)
        assertEquals(Settings.MIN_MINUTES, repo.settings.first().intervalMinutes)
        assertEquals(Settings.MAX_MINUTES, repo.settings.first().snoozeMinutes)
    }
}
