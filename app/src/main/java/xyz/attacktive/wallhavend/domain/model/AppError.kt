package xyz.attacktive.wallhavend.domain.model

sealed class AppError {
	data object NoResults : AppError()
	data class ApiError(val code: Int) : AppError()
	data object UnsupportedFormat : AppError()
	data class WallpaperApplyFailed(val cause: String) : AppError()
}
