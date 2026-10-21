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
private const val EXPORT_PREFIX = "wallhavend_export_"

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
			?.filterNot { it.name.endsWith(PART_SUFFIX) || it.name.startsWith(EXPORT_PREFIX) }
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
		readPngDimensions(randomAccessFile) ?: readJpegDimensions(randomAccessFile)
	}
}.getOrNull()

private fun readPngDimensions(randomAccessFile: RandomAccessFile): Pair<Int, Int>? {
	if (randomAccessFile.length() < 24) {
		return null
	}

	val header = ByteArray(24)
	randomAccessFile.readFully(header)
	val hasSignature = header.copyOfRange(0, 8).contentEquals(PNG_SIGNATURE)
	val hasIhdr = header.copyOfRange(12, 16).contentEquals(IHDR_CHUNK_TYPE)
	if (!hasSignature || !hasIhdr) {
		return null
	}

	val width = readBigEndianInt(header, 16)
	val height = readBigEndianInt(header, 20)

	return positiveDimensionsOrNull(width, height)
}

private val PNG_SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
private val IHDR_CHUNK_TYPE = byteArrayOf(0x49, 0x48, 0x44, 0x52)
private val JPEG_SOF_MARKERS = (0xC0..0xCF).toSet() - setOf(0xC4, 0xC8, 0xCC)
private val JPEG_STAND_ALONE_MARKERS = (0xD0..0xD7).toSet() + 0x01

private fun readBigEndianInt(bytes: ByteArray, offset: Int) =
	((bytes[offset].toInt() and 0xFF) shl 24) or
		((bytes[offset + 1].toInt() and 0xFF) shl 16) or
		((bytes[offset + 2].toInt() and 0xFF) shl 8) or
		(bytes[offset + 3].toInt() and 0xFF)

private fun readJpegDimensions(randomAccessFile: RandomAccessFile): Pair<Int, Int>? {
	randomAccessFile.seek(0)
	if (!hasJpegStartOfImage(randomAccessFile)) {
		return null
	}

	while (randomAccessFile.filePointer < randomAccessFile.length()) {
		val marker = nextJpegMarker(randomAccessFile) ?: return null
		val dimensions = inspectJpegSegment(randomAccessFile, marker)
		if (dimensions != null) {
			return dimensions
		}
	}

	return null
}

private fun hasJpegStartOfImage(randomAccessFile: RandomAccessFile) =
	randomAccessFile.length() >= 4 &&
		randomAccessFile.readUnsignedByte() == 0xFF &&
		randomAccessFile.readUnsignedByte() == 0xD8

private fun nextJpegMarker(randomAccessFile: RandomAccessFile): Int? {
	val prefix = skipUntilMarkerPrefix(randomAccessFile) ?: return null
	val marker = skipMarkerPadding(randomAccessFile, prefix)

	return marker.takeUnless { it == 0xD9 || it == 0xDA }
}

private fun skipUntilMarkerPrefix(randomAccessFile: RandomAccessFile): Int? {
	while (randomAccessFile.filePointer < randomAccessFile.length()) {
		val byte = randomAccessFile.readUnsignedByte()
		if (byte == 0xFF) {
			return byte
		}
	}

	return null
}

private fun skipMarkerPadding(randomAccessFile: RandomAccessFile, initialByte: Int): Int {
	var current = initialByte
	while (current == 0xFF && randomAccessFile.filePointer < randomAccessFile.length()) {
		current = randomAccessFile.readUnsignedByte()
	}

	return current
}

private fun inspectJpegSegment(randomAccessFile: RandomAccessFile, marker: Int): Pair<Int, Int>? {
	if (marker in JPEG_STAND_ALONE_MARKERS) {
		return null
	}

	val segmentLength = randomAccessFile.readUnsignedShort()
	check(segmentLength >= 2) { "Invalid JPEG segment length" }

	if (marker in JPEG_SOF_MARKERS && segmentLength >= 7) {
		randomAccessFile.readUnsignedByte()
		val height = randomAccessFile.readUnsignedShort()
		val width = randomAccessFile.readUnsignedShort()

		return positiveDimensionsOrNull(width, height) ?: error("Invalid SOF dimensions")
	}

	randomAccessFile.skipBytes(segmentLength - 2)

	return null
}

private fun positiveDimensionsOrNull(width: Int, height: Int) =
	if (width > 0 && height > 0) {
		width to height
	} else {
		null
	}
