package xyz.attacktive.wallhavend.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import xyz.attacktive.wallhavend.ui.blocklist.BlocklistScreen
import xyz.attacktive.wallhavend.ui.home.HomeScreen
import xyz.attacktive.wallhavend.ui.preview.PreviewScreen
import xyz.attacktive.wallhavend.ui.settings.SettingsScreen

@Composable
fun AppNavigationHost(navigationController: NavHostController = rememberNavController()) {
	NavHost(navController = navigationController, startDestination = "home") {
		composable("home") {
			HomeScreen(
				onNavigateToSettings = { navigationController.navigate("settings") },
				onNavigateToPreview = { id -> navigationController.navigate("preview/$id") }
			)
		}

		composable("settings") {
			SettingsScreen(
				onNavigateBack = { navigationController.popBackStack() },
				onNavigateToBlocklist = { navigationController.navigate("blocklist") }
			)
		}

		composable("blocklist") {
			BlocklistScreen(onNavigateBack = { navigationController.popBackStack() })
		}

		composable("preview/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) { backStackEntry ->
			val id = backStackEntry.arguments?.getString("id").orEmpty()

			PreviewScreen(id = id, onNavigateBack = { navigationController.popBackStack() })
		}
	}
}
