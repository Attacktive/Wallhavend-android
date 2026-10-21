package xyz.attacktive.wallhavend.ui.home

import java.io.File
import android.content.ContentResolver
import android.content.ContentValues
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import xyz.attacktive.wallhavend.domain.model.WallpaperMetadata
internal class MediaStoreExporter(private val contentResolver: ContentResolver) {
	fun save(file: File, mimeType: String, metadata: WallpaperMetadata? = null) {
		val description = metadata?.galleryDescription
		val sourceFile = if (description != null) {
			val tempFile = File.createTempFile("wallhavend_export_", ".${file.extension}")
			try {
				file.copyTo(tempFile, overwrite = true)
				embedExifDescription(tempFile, description)
				tempFile
			} catch (exception: Throwable) {
				tempFile.delete()
				throw exception
			}
		} else {
			file
		}

		try {
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
					?.use { outputStream -> sourceFile.inputStream().use { inputStream -> inputStream.copyTo(outputStream) } }
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
		} finally {
			if (sourceFile != file) {
				sourceFile.delete()
			}
		}
	}
}

internal fun embedExifDescription(file: File, description: String) {
	val exifInterface = ExifInterface(file)
	exifInterface.setAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION, description)
	exifInterface.saveAttributes()
}
