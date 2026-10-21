package xyz.attacktive.wallhavend.domain.service

import java.io.File
import java.io.RandomAccessFile
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import xyz.attacktive.wallhavend.domain.model.UndersizedWallpaperException
import xyz.attacktive.wallhavend.domain.model.UnsupportedFormatException
import xyz.attacktive.wallhavend.domain.model.Wallpaper
import xyz.attacktive.wallhavend.domain.model.WallpaperIdentity

private const val PART_SUFFIX = ".part"

class WallpaperFileManager(private val wallpaperDir: File, private val okHttpClient: OkHttpClient) {
	private val dir: File get() = wallpaperDir.also { it.mkdirs() }

	suspend fun download(
		wallpaper: Wallpaper,
		minimumWidth: Int? = null,
		minimumHeight: Int? = null
	) = withContext(Dispatchers.IO) {
		runCatching {
			val request = Request.Builder()
				.url(wallpaper.directUrl)
				.build()

			okHttpClient.newCall(request)
				.execute()
				.use { response ->
					check(response.isSuccessful) { "HTTP ${response.code}" }

					val file = File(dir, wallpaper.identity.toFileName(response.imageExtension()))
					val partialFile = File.createTempFile(".${file.name}.", PART_SUFFIX, dir)

					try {
						response.body
							.byteStream()
							.use { input ->
								partialFile.outputStream().use { output -> input.copyTo(output) }
							}

						if (minimumWidth != null && minimumHeight != null) {
							val dimensions = readImageDimensions(partialFile)
							if (dimensions != null && (dimensions.first < minimumWidth || dimensions.second < minimumHeight)) {
								throw UndersizedWallpaperException(
									width = dimensions.first,
									height = dimensions.second,
									minimumWidth = minimumWidth,
									minimumHeight = minimumHeight
								)
							}
						}

						Files.move(
							partialFile.toPath(),
							file.toPath(),
							StandardCopyOption.ATOMIC_MOVE,
							StandardCopyOption.REPLACE_EXISTING
						)

						file
					} finally {
						partialFile.delete()
					}
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
			?.filterNot { it.name.endsWith(PART_SUFFIX) }
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

internal fun readImageDimensions(file: File) = runCatching {
	RandomAccessFile(file, "r").use { randomAccessFile ->
		if (randomAccessFile.length() >= 24) {
			val header = ByteArray(24)
			randomAccessFile.readFully(header)
			if (isPngHeader(header)) {
				val width = readBigEndianInt(header, 16)
				val height = readBigEndianInt(header, 20)
				if (width > 0 && height > 0) {
					return@use width to height
				}
			}
		}

		randomAccessFile.seek(0)
		readJpegDimensions(randomAccessFile)
	}
}.getOrNull()

private fun isPngHeader(bytes: ByteArray) =
	bytes[0] == 0x89.toByte() &&
		bytes[1] == 0x50.toByte() &&
		bytes[2] == 0x4E.toByte() &&
		bytes[3] == 0x47.toByte() &&
		bytes[12] == 0x49.toByte() &&
		bytes[13] == 0x48.toByte() &&
		bytes[14] == 0x44.toByte() &&
		bytes[15] == 0x52.toByte()

private fun readBigEndianInt(bytes: ByteArray, offset: Int) =
	((bytes[offset].toInt() and 0xFF) shl 24) or
		((bytes[offset + 1].toInt() and 0xFF) shl 16) or
		((bytes[offset + 2].toInt() and 0xFF) shl 8) or
		(bytes[offset + 3].toInt() and 0xFF)

private fun readJpegDimensions(randomAccessFile: RandomAccessFile): Pair<Int, Int>? {
	val hasJpegMagic = randomAccessFile.length() >= 4 &&
		randomAccessFile.readUnsignedByte() == 0xFF &&
		randomAccessFile.readUnsignedByte() == 0xD8
	if (!hasJpegMagic) {
		return null
	}

	while (randomAccessFile.filePointer < randomAccessFile.length()) {
		var marker = randomAccessFile.readUnsignedByte()
		while (marker != 0xFF && randomAccessFile.filePointer < randomAccessFile.length()) {
			marker = randomAccessFile.readUnsignedByte()
		}

		while (marker == 0xFF && randomAccessFile.filePointer < randomAccessFile.length()) {
			marker = randomAccessFile.readUnsignedByte()
		}

		if (marker == 0xD9 || marker == 0xDA) {
			return null
		}

		if (marker == 0x01 || marker in 0xD0..0xD7) {
			continue
		}

		val segmentLength = randomAccessFile.readUnsignedShort()
		if (segmentLength < 2) {
			return null
		}

		val isStartOfFrame = marker in 0xC0..0xCF && marker != 0xC4 && marker != 0xC8 && marker != 0xCC
		if (isStartOfFrame && segmentLength >= 7) {
			randomAccessFile.readUnsignedByte()
			val height = randomAccessFile.readUnsignedShort()
			val width = randomAccessFile.readUnsignedShort()

			return if (width > 0 && height > 0) {
				width to height
			} else {
				null
			}
		}

		randomAccessFile.skipBytes(segmentLength - 2)
	}

	return null
}
