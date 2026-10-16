package com.yehleh.aislewise

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.yehleh.aislewise.nav.AppNavHost
import com.yehleh.aislewise.ui.theme.AisleWiseTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            AisleWiseTheme {
                AppNavHost()
            }
        }
    }
}
