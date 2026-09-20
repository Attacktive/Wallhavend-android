package xyz.attacktive.wallhavend.domain.service

import java.io.File
import javax.inject.Inject
import android.app.WallpaperManager
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import xyz.attacktive.wallhavend.domain.model.WallpaperTarget

class WallpaperApplier @Inject constructor(@param:ApplicationContext private val context: Context) {
	fun apply(file: File, target: WallpaperTarget) = runCatching {
		val flags = when (target) {
			WallpaperTarget.HOME -> WallpaperManager.FLAG_SYSTEM
			WallpaperTarget.LOCK -> WallpaperManager.FLAG_LOCK
			WallpaperTarget.BOTH -> WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
		}

		file.inputStream()
			.use { input ->
				context.getSystemService(WallpaperManager::class.java)
					.setStream(input, null, true, flags)
			}
	}
}
