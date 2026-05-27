package xyz.attacktive.wallhavend.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import xyz.attacktive.wallhavend.ui.home.HomeScreen
import xyz.attacktive.wallhavend.ui.settings.SettingsScreen

@Composable
fun AppNavigationHost(navigationController: NavHostController = rememberNavController()) {
	NavHost(navController = navigationController, startDestination = "home") {
		composable("home") {
			HomeScreen(onNavigateToSettings = { navigationController.navigate("settings") })
		}

		composable("settings") {
			SettingsScreen(onNavigateBack = { navigationController.popBackStack() })
		}
	}
}
