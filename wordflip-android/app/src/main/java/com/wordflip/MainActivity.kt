package com.wordflip

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import com.wordflip.core.model.settings.ThemeMode
import com.wordflip.core.network.token.TokenStore
import com.wordflip.core.ui.theme.WordFlipTheme
import com.wordflip.feature.settings.SettingsPreferences
import com.wordflip.navigation.WordFlipNavHost
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var tokenStore: TokenStore

    @Inject
    lateinit var settingsPreferences: SettingsPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val isLoggedIn by tokenStore.isLoggedInFlow.collectAsState(
                initial = tokenStore.isLoggedIn(),
            )
            val themeMode by settingsPreferences.themeModeFlow.collectAsState(
                initial = ThemeMode.SYSTEM,
            )
            val systemDark = isSystemInDarkTheme()
            val darkTheme = when (themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> systemDark
            }

            WordFlipTheme(darkTheme = darkTheme) {
                WordFlipNavHost(
                    isLoggedIn = isLoggedIn,
                    onLoginSuccess = { /* TokenStore 已写入；NavHost 内导航至 Main */ },
                    onLogout = { /* AuthRepository.logout 已清 Token；isLoggedInFlow 驱动回登录 */ },
                    settingsPreferences = settingsPreferences,
                )
            }
        }
    }
}
