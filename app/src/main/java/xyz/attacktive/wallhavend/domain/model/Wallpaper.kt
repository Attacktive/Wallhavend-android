package xyz.attacktive.wallhavend.domain.model

/**
 * A downloaded wallpaper about to hit disk.
 * [directUrl] is what gets fetched; the page to visit is [WallpaperIdentity.pageUrl], which each source's id can reconstruct on demand.
 */
data class Wallpaper(val identity: WallpaperIdentity, val directUrl: String, val metadata: WallpaperMetadata? = null) {
	constructor(identity: WallpaperIdentity, directUrl: String, thumbnailUrl: String?): this(
		identity = identity,
		directUrl = directUrl,
		metadata = thumbnailUrl?.let { WallpaperMetadata(thumbnailUrl = it) }
	)

	val thumbnailUrl get() = metadata?.thumbnailUrl
}
