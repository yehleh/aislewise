package com.yehleh.aislewise.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.yehleh.aislewise.data.prefs.UserPreferences
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class UserPreferencesTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Before fun clearPrefs() {
        context.getSharedPreferences("aislewise_prefs", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun `prices are shown by default`() {
        assertTrue(UserPreferences(context).showPrices.value)
    }

    @Test fun `the toggle is saved for next time`() {
        UserPreferences(context).setShowPrices(false)

        assertFalse(UserPreferences(context).showPrices.value)
    }
}
