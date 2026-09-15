package dev.boar.checktime.ui

import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MainActivityTest {
    @Test
    fun activityStarts() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        assertNotNull(activity)
    }
}
