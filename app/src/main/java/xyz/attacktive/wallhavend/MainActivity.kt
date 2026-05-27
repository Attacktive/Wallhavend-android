package xyz.attacktive.wallhavend

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import dagger.hilt.android.AndroidEntryPoint
import xyz.attacktive.wallhavend.ui.navigation.AppNavigationHost
import xyz.attacktive.wallhavend.ui.theme.WallhavendTheme

@AndroidEntryPoint
class MainActivity: ComponentActivity() {
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		setContent {
			WallhavendTheme {
				AppNavigationHost()
			}
		}
	}
}
