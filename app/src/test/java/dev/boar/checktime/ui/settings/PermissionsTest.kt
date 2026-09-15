package dev.boar.checktime.ui.settings

import android.app.Application
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowSettings

@RunWith(RobolectricTestRunner::class)
class PermissionsTest {
    private val context: Application = ApplicationProvider.getApplicationContext()

    @Test fun overlayReflectsSystemSetting() {
        ShadowSettings.setCanDrawOverlays(false)
        assertFalse(Permissions.status(context).overlay)
        ShadowSettings.setCanDrawOverlays(true)
        assertTrue(Permissions.status(context).overlay)
    }

    @Test fun overlayIntentTargetsThisPackage() {
        val intent = Permissions.overlaySettingsIntent(context)
        assertEquals(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, intent.action)
        assertEquals("package:${context.packageName}", intent.data.toString())
    }
}
