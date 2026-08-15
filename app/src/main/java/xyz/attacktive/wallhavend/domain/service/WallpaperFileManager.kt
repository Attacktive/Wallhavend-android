package xyz.attacktive.wallhavend.domain.service

import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import xyz.attacktive.wallhavend.domain.model.UnsupportedFormatException
import xyz.attacktive.wallhavend.domain.model.Wallpaper
import xyz.attacktive.wallhavend.domain.model.WallpaperIdentity

class WallpaperFileManager(private val wallpaperDir: File, private val okHttpClient: OkHttpClient) {
	private val dir: File get() = wallpaperDir.also { it.mkdirs() }

	suspend fun download(wallpaper: Wallpaper) = withContext(Dispatchers.IO) {
		runCatching {
			val request = Request.Builder()
				.url(wallpaper.directUrl)
				.build()

			okHttpClient.newCall(request)
				.execute()
				.use { response ->
					check(response.isSuccessful) { "HTTP ${response.code}" }

					val file = File(dir, wallpaper.identity.toFileName(response.imageExtension()))

					response.body
						.byteStream()
						.use { input ->
							file.outputStream().use { output -> input.copyTo(output) }
						}

					file
				}
		}
	}

	fun listAll() = sortedFiles()

	fun trimToSize(maxSize: Int, pinnedIds: Set<String> = emptySet()): List<File> {
		val (pinned, rotating) = sortedFiles()
			.partition {
				WallpaperIdentity.parse(it.nameWithoutExtension)
					.matches(pinnedIds)
			}

		rotating.drop(maxSize)
			.forEach { it.delete() }

		return (pinned + rotating.take(maxSize)).sortedByDescending { it.lastModified() }
	}

	private fun sortedFiles() =
		dir.listFiles()
			?.sortedByDescending { it.lastModified() }
			?: emptyList()
}

/**
 * The extension names what the server actually sent rather than what the search metadata claimed.
 * Not every source reports a file type — Openverse leaves it null on most results — and the bytes are the reliable answer regardless.
 */
private fun Response.imageExtension(): String {
	val contentType = body.contentType().toString()

	return when {
		contentType.contains("image/jpeg") -> "jpg"
		contentType.contains("image/png") -> "png"
		else -> throw UnsupportedFormatException(contentType)
	}
}
