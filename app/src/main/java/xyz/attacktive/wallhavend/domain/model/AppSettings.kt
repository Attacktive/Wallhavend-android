package xyz.attacktive.wallhavend.domain.model

data class AppSettings(
	val searchQuery: String = "",
	val categories: Set<WallhavenCategory> = setOf(WallhavenCategory.GENERAL),
	val purity: Set<Purity> = setOf(Purity.SFW),
	val aspectRatio: String = "",
	val updateIntervalMinutes: Int = 60,
	val wallpaperTarget: WallpaperTarget = WallpaperTarget.HOME,
	val wifiOnly: Boolean = true,
	val poolSize: Int = 10,
	val apiKey: String = "",
	val autoStartOnBoot: Boolean = true,
	val filterColor: String = "",
	val sorting: String = "random",
	val toplistRange: String = "1M"
)

val UPDATE_INTERVAL_OPTIONS = listOf(1, 5, 15, 30, 60, 180, 360, 1440)
val POOL_SIZE_OPTIONS = listOf(0, 5, 10, 25, 50)
val ASPECT_RATIO_SUGGESTIONS = listOf("9x16", "10x16", "16x9", "16x10", "21x9", "4x3", "1x1")
val SORTING_OPTIONS = listOf("random", "date_added", "views", "favorites", "toplist")
val TOPLIST_RANGE_OPTIONS = listOf("1d", "3d", "1w", "1M", "3M", "6M", "1y")
