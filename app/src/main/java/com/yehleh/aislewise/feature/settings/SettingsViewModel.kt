package com.yehleh.aislewise.feature.settings

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ViewModel
import com.yehleh.aislewise.data.prefs.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/** Whether price entry and price summaries are shown; provided near the nav host. */
val LocalShowPrices = staticCompositionLocalOf { true }

@HiltViewModel
class SettingsViewModel @Inject constructor(private val prefs: UserPreferences) : ViewModel() {
    val showPrices: StateFlow<Boolean> = prefs.showPrices

    fun setShowPrices(enabled: Boolean) = prefs.setShowPrices(enabled)
}
