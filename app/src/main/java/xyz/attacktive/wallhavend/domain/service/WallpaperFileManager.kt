package xyz.attacktive.wallhavend.domain.service

import xyz.attacktive.wallhavend.domain.model.UnsupportedFormatException
import xyz.attacktive.wallhavend.domain.model.Wallpaper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WallpaperFileManager @Inject constructor(
    private val wallpaperDir: File,
    private val okHttpClient: OkHttpClient
) {
    private val dir: File get() = wallpaperDir.also { it.mkdirs() }

    suspend fun download(wallpaper: Wallpaper): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder().url(wallpaper.directUrl).build()
            val response = okHttpClient.newCall(request).execute()
            check(response.isSuccessful) { "HTTP ${response.code}" }
            val contentType = response.body?.contentType()?.toString() ?: ""
            if (!contentType.contains("image/jpeg") && !contentType.contains("image/png")) {
                throw UnsupportedFormatException(contentType)
            }
            val file = File(dir, "${wallpaper.id}.${wallpaper.fileExtension}")
            response.body!!.byteStream().use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            }
            file
        }
    }

    fun trimToSize(maxSize: Int): List<File> {
        val all = dir.listFiles()
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
        all.drop(maxSize).forEach { it.delete() }
        return all.take(maxSize)
    }

    fun listAll(): List<File> =
        dir.listFiles()?.sortedByDescending { it.lastModified() } ?: emptyList()
}
