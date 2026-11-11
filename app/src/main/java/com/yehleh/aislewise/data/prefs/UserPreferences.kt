package com.yehleh.aislewise.data.prefs

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Small key-value settings store. */
@Singleton
class UserPreferences @Inject constructor(@ApplicationContext context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    private val _showPrices = MutableStateFlow(prefs.getBoolean(KEY_SHOW_PRICES, true))
    val showPrices: StateFlow<Boolean> = _showPrices.asStateFlow()

    fun setShowPrices(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_PRICES, enabled).apply()
        _showPrices.value = enabled
    }

    private companion object {
        const val NAME = "aislewise_prefs"
        const val KEY_SHOW_PRICES = "show_prices"
    }
}
