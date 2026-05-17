package xyz.attacktive.wallhavend.domain.model

data class ServiceState(
    val isRunning: Boolean = false,
    val lastUpdatedMs: Long? = null,
    val currentWallpaperPath: String? = null,
    val previousWallpaperPath: String? = null,
    val poolPaths: List<String> = emptyList(),
    val isOnline: Boolean = true,
    val error: AppError? = null
)
