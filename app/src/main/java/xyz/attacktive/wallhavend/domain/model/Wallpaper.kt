package xyz.attacktive.wallhavend.domain.model

data class Wallpaper(val id: String, val pageUrl: String, val directUrl: String, val resolution: String, val mimeType: String) {
	val fileExtension: String get() = when (mimeType) {
		"image/jpeg" -> "jpg"
		"image/png" -> "png"
		else -> throw UnsupportedFormatException(mimeType)
	}
}
