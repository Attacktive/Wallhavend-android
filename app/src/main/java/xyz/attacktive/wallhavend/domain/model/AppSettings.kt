package xyz.attacktive.wallhavend.domain.model

import kotlin.math.abs
import xyz.attacktive.wallhavend.domain.model.query.Category
import xyz.attacktive.wallhavend.domain.model.query.Purity
import xyz.attacktive.wallhavend.domain.model.query.Sorting
import xyz.attacktive.wallhavend.domain.model.query.ToplistRange

data class AppSettings(
	val searchQuery: String = "",
	val categories: Set<Category> = setOf(Category.GENERAL),
	val purity: Set<Purity> = setOf(Purity.SFW),
	val updateIntervalMinutes: Int = 60,
	val wallpaperTarget: WallpaperTarget = WallpaperTarget.HOME,
	val rotationMode: RotationMode = RotationMode.FRESH_WIFI,
	val poolSize: Int = 10,
	val apiKey: String = "",
	val autoStartOnBoot: Boolean = true,
	val filterColor: String = "",
	val sorting: Sorting = Sorting.RANDOM,
	val toplistRange: ToplistRange = ToplistRange.ONE_MONTH,
	val avoidBlurryWallpapers: Boolean = false,
	val blockedIds: Set<String> = emptySet(),
	val pinnedIds: Set<String> = emptySet(),
	val autoUpdateEnabled: Boolean = false
)

val UPDATE_INTERVAL_OPTIONS = listOf(1, 5, 15, 30, 60, 180, 360, 1440)
val POOL_SIZE_OPTIONS = listOf(1, 5, 10, 25, 50)

/**
 * Ratios with meaningful content on Wallhaven (verified by querying meta.total per ratio).
 * Sparse ratios like 9x18/9x19/9x20 are intentionally excluded; modern tall phones fall back to 9x16.
 */
private val ASPECT_RATIO_CANDIDATES = listOf("9x16", "10x16", "3x4", "16x9", "16x10", "4x3", "21x9", "1x1")

fun closestAspectRatio(width: Int, height: Int): String {
	val actual = width.toFloat() / height

	return ASPECT_RATIO_CANDIDATES.minBy { candidate ->
		val (w, h) = candidate.split("x").map { it.toFloat() }
		abs(w / h - actual)
	}
}
