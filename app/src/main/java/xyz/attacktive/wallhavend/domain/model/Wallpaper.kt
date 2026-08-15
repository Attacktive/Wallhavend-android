package xyz.attacktive.wallhavend.domain.model

/**
 * A downloaded wallpaper about to hit disk.
 * [directUrl] is what gets fetched; the page to visit is [WallpaperIdentity.pageUrl], which each source's id can reconstruct on demand.
 */
data class Wallpaper(val identity: WallpaperIdentity, val directUrl: String, val resolution: String)
