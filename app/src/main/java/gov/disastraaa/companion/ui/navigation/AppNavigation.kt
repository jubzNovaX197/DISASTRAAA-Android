package gov.disastraaa.companion.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import gov.disastraaa.companion.DisastraaaApplication
import gov.disastraaa.companion.auth.AuthViewModel
import gov.disastraaa.companion.ui.login.LoginScreen
import gov.disastraaa.companion.ui.monitoring.MonitoringScreen
import gov.disastraaa.companion.ui.monitoring.MonitoringViewModel

sealed class Screen(val route: String) {
    data object Login : Screen("login")
    data object Monitoring : Screen("monitoring")
}

@Composable
fun AppNavigation(
    navController: NavHostController = rememberNavController(),
    modifier: Modifier = Modifier
) {
    val app = DisastraaaApplication.instance
    val startDestination = if (app.authRepository.isLoggedIn()) {
        Screen.Monitoring.route
    } else {
        Screen.Login.route
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        composable(Screen.Login.route) {
            val authViewModel: AuthViewModel = viewModel(
                factory = AuthViewModel.provideFactory(app.authRepository)
            )

            LoginScreen(
                viewModel = authViewModel,
                onLoginSuccess = {
                    navController.navigate(Screen.Monitoring.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Monitoring.route) {
            val monitoringViewModel: MonitoringViewModel = viewModel(
                factory = MonitoringViewModel.provideFactory(
                    authRepository = app.authRepository,
                    locationRepository = app.locationRepository
                )
            )

            MonitoringScreen(
                viewModel = monitoringViewModel,
                onLogoutClick = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
    }
}
