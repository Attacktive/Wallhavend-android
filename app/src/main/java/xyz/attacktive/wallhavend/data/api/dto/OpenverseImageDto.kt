package xyz.attacktive.wallhavend.data.api.dto

import kotlinx.serialization.Serializable
import android.annotation.SuppressLint
import xyz.attacktive.wallhavend.domain.model.Wallpaper
import xyz.attacktive.wallhavend.domain.model.WallpaperIdentity
import xyz.attacktive.wallhavend.domain.model.WallpaperSource

/**
 * One Openverse result.
 * [width] and [height] are what make the resolution check possible at all: Openverse's own `size` filter is a coarse small/medium/large bucket, so "at least as large as the screen" has to be decided here rather than in the query.
 * Both are nullable because the index doesn't always know them, and a result that can't prove its size is treated as too small.
 * [url] is nullable because [the Openverse schema does not guarantee it](https://api.openverse.org/v1/#schema_image); records without a direct URL can't be downloaded and are skipped.
 */
@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class OpenverseImageDto(
	val id: String,
	val url: String? = null,
	val width: Int? = null,
	val height: Int? = null
)

/** [WallpaperIdentity.pageUrl] is the Openverse entry for the id; Openverse only indexes what other sites host, so the origin page stays out of the model. */
fun OpenverseImageDto.toDomain() = Wallpaper(identity = WallpaperIdentity(WallpaperSource.OPENVERSE, id), directUrl = checkNotNull(url))
