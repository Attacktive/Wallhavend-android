package xyz.attacktive.wallhavend.ui.picker

import java.io.File
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import xyz.attacktive.wallhavend.MainActivity
import xyz.attacktive.wallhavend.R
import xyz.attacktive.wallhavend.ui.theme.WallhavendTheme

@AndroidEntryPoint
class WallpaperPickerActivity: ComponentActivity() {
	private val viewModel: WallpaperPickerViewModel by viewModels()

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		setContent {
			val wallpapers by viewModel.wallpapers.collectAsStateWithLifecycle()

			WallhavendTheme {
				WallpaperPickerScreen(
					wallpapers = wallpapers,
					onSelect = ::returnWallpaper,
					onCancel = {
						setResult(RESULT_CANCELED)
						finish()
					},
					onOpenApp = {
						startActivity(Intent(this, MainActivity::class.java))
						finish()
					}
				)
			}
		}
	}

	private fun returnWallpaper(file: File) {
		if (!file.exists()) {
			handleUnavailableWallpaper()
			return
		}

		val resultIntent = try {
			createResultIntent(this, file)
		} catch (exception: IllegalArgumentException) {
			if (file.exists()) {
				throw exception
			}

			handleUnavailableWallpaper()
			return
		}

		setResult(RESULT_OK, resultIntent)
		finish()
	}

	private fun handleUnavailableWallpaper() {
		viewModel.refresh()
		Toast.makeText(this, R.string.picker_file_unavailable, Toast.LENGTH_SHORT).show()
	}

	companion object {
		fun createResultIntent(context: Context, file: File): Intent {
			require(file.exists()) { "Wallpaper file no longer exists" }

			val uri = FileProvider.getUriForFile(
				context,
				"${context.packageName}.fileprovider",
				file
			)

			val mimeType = when (file.extension.lowercase()) {
				"jpg", "jpeg" -> "image/jpeg"
				"png" -> "image/png"
				else -> "image/*"
			}

			return Intent().apply {
				setDataAndType(uri, mimeType)
				clipData = ClipData.newRawUri(null, uri)
				addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
			}
		}
	}
}
