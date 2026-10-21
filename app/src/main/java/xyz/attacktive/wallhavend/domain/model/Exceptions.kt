package xyz.attacktive.wallhavend.domain.model

class NoResultsException: Exception("No wallpapers found for the current query")
class UnsupportedFormatException(mimeType: String): Exception("Unsupported image format: $mimeType")
class MissingApiKeyException(val source: WallpaperSource): Exception("Missing API key for ${source.key}")
class InvalidApiKeyException(val source: WallpaperSource): Exception("Invalid API key for ${source.key}")
class QuotaExceededException(val source: WallpaperSource): Exception("Monthly quota exceeded for ${source.key}")
class RateLimitedException(val source: WallpaperSource, val retryAfterSeconds: Long? = null): Exception("Rate limited by ${source.key}")
class UndersizedWallpaperException(
	val width: Int,
	val height: Int,
	val minimumWidth: Int,
	val minimumHeight: Int
): Exception("Wallpaper ${width}x$height is smaller than minimum ${minimumWidth}x$minimumHeight")
