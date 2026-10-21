package xyz.attacktive.wallhavend.domain.model

sealed class AppError {
	data object NoResults: AppError()
	data object NoResultsWithRatioHint: AppError()
	data class ApiError(val code: Int, val source: WallpaperSource? = null): AppError()
	data object UnsupportedFormat: AppError()
	data class WallpaperApplyFailed(val cause: String): AppError()
	data class NetworkError(val cause: String, val source: WallpaperSource? = null): AppError()
	data class MissingApiKey(val source: WallpaperSource): AppError()
	data class InvalidApiKey(val source: WallpaperSource): AppError()
	data class QuotaExceeded(val source: WallpaperSource): AppError()
	data class RateLimited(val source: WallpaperSource, val retryAfterSeconds: Long? = null): AppError()
}
