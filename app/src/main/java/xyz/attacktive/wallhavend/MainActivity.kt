package xyz.attacktive.wallhavend

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import xyz.attacktive.wallhavend.ui.navigation.AppNavHost
import xyz.attacktive.wallhavend.ui.theme.WallhavendTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WallhavendTheme {
                AppNavHost()
            }
        }
    }
}
