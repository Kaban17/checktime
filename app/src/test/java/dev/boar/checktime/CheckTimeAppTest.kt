package dev.boar.checktime

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CheckTimeAppTest {

    @Test fun robolectricUsesTestApplicationWithInMemoryDb() {
        val app: Application = ApplicationProvider.getApplicationContext()
        assertTrue(app is TestCheckTimeApp)
        // Room's in-memory builder is created without a database name.
        assertNull((app as CheckTimeApp).container.db.openHelper.databaseName)
    }
}
