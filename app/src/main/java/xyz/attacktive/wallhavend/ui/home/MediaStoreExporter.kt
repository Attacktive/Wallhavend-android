package xyz.attacktive.wallhavend.ui.home

import java.io.File
import android.content.ContentResolver
import android.content.ContentValues
import android.provider.MediaStore

internal class MediaStoreExporter(private val contentResolver: ContentResolver) {
	fun save(file: File, mimeType: String) {
		val pendingValues = ContentValues().apply {
			put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
			put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
			put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/Wallhavend")
			put(MediaStore.MediaColumns.IS_PENDING, 1)
		}

		val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, pendingValues)
			?: error("Failed to create MediaStore entry")

		try {
			contentResolver.openOutputStream(uri)
				?.use { outputStream -> file.inputStream().use { inputStream -> inputStream.copyTo(outputStream) } }
				?: error("Failed to open output stream")

			val finalizedValues = ContentValues().apply {
				put(MediaStore.MediaColumns.IS_PENDING, 0)
			}

			check(contentResolver.update(uri, finalizedValues, null, null) == 1) { "Failed to finalize MediaStore entry" }
		} catch (exception: Throwable) {
			val cleanupFailure = runCatching { contentResolver.delete(uri, null, null) }.exceptionOrNull()
			if (cleanupFailure != null) {
				exception.addSuppressed(cleanupFailure)
			}

			throw exception
		}
	}
}
