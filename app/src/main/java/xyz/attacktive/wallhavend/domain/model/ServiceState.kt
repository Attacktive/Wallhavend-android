package xyz.attacktive.wallhavend.domain.model

data class ServiceState(
	val lastUpdatedMs: Long? = null,
	val currentWallpaperPath: String? = null,
	val previousWallpaperPath: String? = null,
	val poolPaths: List<String> = emptyList(),
	val isOnline: Boolean = true,
	val error: AppError? = null,
	val providerFailures: Map<WallpaperSource, ProviderFailure> = emptyMap()
)
