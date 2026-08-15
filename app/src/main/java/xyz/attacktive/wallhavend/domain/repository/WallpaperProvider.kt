package xyz.attacktive.wallhavend.domain.repository

import xyz.attacktive.wallhavend.domain.model.AppSettings
import xyz.attacktive.wallhavend.domain.model.ScreenInfo
import xyz.attacktive.wallhavend.domain.model.Wallpaper
import xyz.attacktive.wallhavend.domain.model.WallpaperSource

/**
 * One source's search.
 * A provider only picks a candidate; downloading it and blending sources is [WallpaperRepository]'s job, so a download that fails can fall back to another source.
 */
interface WallpaperProvider {
	val source: WallpaperSource

	/** [blockedIds] are bare ids belonging to this provider's own [source], already stripped of the qualifying prefix. */
	suspend fun next(settings: AppSettings, screenInfo: ScreenInfo, blockedIds: Set<String>): Result<Wallpaper>
}
