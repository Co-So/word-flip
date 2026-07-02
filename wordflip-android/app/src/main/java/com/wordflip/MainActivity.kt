package com.wordflip

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.wordflip.core.model.settings.ThemeMode
import com.wordflip.core.ui.theme.WordFlipTheme
import com.wordflip.feature.settings.SettingsPreferences
import com.wordflip.navigation.WordFlipNavHost
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            val settingsPreferences = remember {
                SettingsPreferences(context.applicationContext)
            }
            val themeMode by settingsPreferences.themeModeFlow.collectAsState(initial = ThemeMode.SYSTEM)
            val systemDark = isSystemInDarkTheme()
            val darkTheme = when (themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> systemDark
            }
            // Debug 包默认已登录，便于直接预览主流程
            var isLoggedIn by rememberSaveable { mutableStateOf(BuildConfig.DEBUG) }

            WordFlipTheme(darkTheme = darkTheme) {
                WordFlipNavHost(
                    isLoggedIn = isLoggedIn,
                    onLoginSuccess = { isLoggedIn = true },
                    onLogout = { isLoggedIn = false },
                    settingsPreferences = settingsPreferences,
                )
            }
        }
    }
}
