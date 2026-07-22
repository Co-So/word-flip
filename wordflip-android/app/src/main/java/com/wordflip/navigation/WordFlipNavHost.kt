package com.wordflip.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.wordflip.feature.auth.ForgotPasswordScreen
import com.wordflip.feature.auth.LoginScreen
import com.wordflip.feature.auth.RegisterScreen
import com.wordflip.feature.settings.SettingsPreferences

object Routes {
    const val LOGIN = "login"
    const val REGISTER = "register"
    const val FORGOT_PASSWORD = "forgot_password"
    const val MAIN = "main"
    const val PLAN_GATE = "plan_gate"
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
    // 登出或 Token 失效：清凭证后仅保留 Auth graph
    LaunchedEffect(isLoggedIn) {
        if (!isLoggedIn) {
            navController.navigate(Routes.LOGIN) {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    val startDestination = if (isLoggedIn) Routes.PLAN_GATE else Routes.LOGIN

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
    ) {
        composable(Routes.LOGIN) {
            LoginScreen(
                onLoginSuccess = {
                    onLoginSuccess()
                    navController.navigate(Routes.PLAN_GATE) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                },
                onNavigateToRegister = {
                    navController.navigate(Routes.REGISTER)
                },
                onNavigateToForgotPassword = {
                    navController.navigate(Routes.FORGOT_PASSWORD)
                },
            )
        }
        composable(Routes.FORGOT_PASSWORD) {
            ForgotPasswordScreen(
                onNavigateBack = { navController.popBackStack() },
                onResetSuccess = { navController.popBackStack() },
            )
        }
        composable(Routes.REGISTER) {
            RegisterScreen(
                onRegisterSuccess = {
                    onLoginSuccess()
                    navController.navigate(Routes.PLAN_GATE) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                },
                onNavigateToLogin = { navController.popBackStack() },
            )
        }
        composable(Routes.MAIN) {
            MainScreen(
                settingsPreferences = settingsPreferences,
                onLogout = onLogout,
            )
        }
        composable(Routes.PLAN_GATE) {
            PlanGateScreen(
                onReady = {
                    navController.navigate(Routes.MAIN) {
                        popUpTo(Routes.PLAN_GATE) { inclusive = true }
                    }
                },
            )
        }
    }
}
