package com.wordflip.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.wordflip.feature.auth.LoginScreen
import com.wordflip.feature.settings.SettingsPreferences

object Routes {
    const val LOGIN = "login"
    const val MAIN = "main"
}

@Composable
fun WordFlipNavHost(
    isLoggedIn: Boolean,
    onLoginSuccess: () -> Unit,
    onLogout: () -> Unit,
    settingsPreferences: SettingsPreferences,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    LaunchedEffect(isLoggedIn) {
        if (!isLoggedIn) {
            navController.navigate(Routes.LOGIN) {
                popUpTo(Routes.MAIN) { inclusive = true }
            }
        }
    }

    val startDestination = if (isLoggedIn) Routes.MAIN else Routes.LOGIN

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
    ) {
        composable(Routes.LOGIN) {
            LoginScreen(onLoginSuccess = {
                onLoginSuccess()
                navController.navigate(Routes.MAIN) {
                    popUpTo(Routes.LOGIN) { inclusive = true }
                }
            })
        }
        composable(Routes.MAIN) {
            MainScreen(
                settingsPreferences = settingsPreferences,
                onLogout = onLogout,
            )
        }
    }
}
