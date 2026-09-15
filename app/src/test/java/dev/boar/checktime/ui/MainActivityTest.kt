package dev.boar.checktime.ui

import android.app.NotificationManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import dev.boar.checktime.scheduler.PendingNotification
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MainActivityTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun waitForText(text: String) =
        compose.waitUntil(5_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    @Test fun bottomBarSwitchesBetweenDayAndSettings() {
        waitForText("Учёт не начат — откройте «Настройки»")
        compose.onNodeWithText("Настройки").performClick()
        waitForText("Начать учёт") // экран настроек ждёт первого значения из DataStore/Room
        compose.onNodeWithText("Начать учёт").assertIsDisplayed()
        compose.onNodeWithText("День").performClick()
        waitForText("Учёт не начат — откройте «Настройки»")
    }

    @Test fun resumeWithoutTailClearsNotification() {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        PendingNotification.createChannels(context)
        PendingNotification.show(context, 5, urgent = false)
        compose.activityRule.scenario.recreate() // onResume -> TailNotifier.sync
        val nm = shadowOf(context.getSystemService(NotificationManager::class.java))
        compose.waitUntil(5_000) { nm.getNotification(PendingNotification.ID) == null }
        assertNull(nm.getNotification(PendingNotification.ID))
    }
}
