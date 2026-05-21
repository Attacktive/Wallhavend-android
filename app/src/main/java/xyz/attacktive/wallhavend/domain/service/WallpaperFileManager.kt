package xyz.attacktive.wallhavend.domain.service

import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import xyz.attacktive.wallhavend.domain.model.UnsupportedFormatException
import xyz.attacktive.wallhavend.domain.model.Wallpaper

class WallpaperFileManager(private val wallpaperDir: File, private val okHttpClient: OkHttpClient) {
	private val dir: File get() = wallpaperDir.also { it.mkdirs() }

	suspend fun download(wallpaper: Wallpaper): Result<File> = withContext(Dispatchers.IO) {
		runCatching {
			val request = Request.Builder()
				.url(wallpaper.directUrl)
				.build()

			okHttpClient.newCall(request)
				.execute()
				.use { response ->
					check(response.isSuccessful) { "HTTP ${response.code}" }

					val contentType = response.body?.contentType()?.toString() ?: ""
					if (!contentType.contains("image/jpeg") && !contentType.contains("image/png")) {
						throw UnsupportedFormatException(contentType)
					}

					val file = File(dir, "${wallpaper.id}.${wallpaper.fileExtension}")

					requireNotNull(response.body)
						.byteStream()
						.use { input ->
							file.outputStream().use { output -> input.copyTo(output) }
						}

					file
				}
		}
	}

	fun listAll(): List<File> =
		dir.listFiles()
			?.sortedByDescending { it.lastModified() }
			?: emptyList()

	fun trimToSize(maxSize: Int): List<File> {
		val all = dir.listFiles()
			?.sortedByDescending { it.lastModified() }
			?: emptyList()

		all.drop(maxSize).forEach { it.delete() }

		return all.take(maxSize)
	}

}
