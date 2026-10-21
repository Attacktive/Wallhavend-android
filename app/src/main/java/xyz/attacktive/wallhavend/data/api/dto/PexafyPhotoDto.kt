package xyz.attacktive.wallhavend.data.api.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import android.annotation.SuppressLint
import xyz.attacktive.wallhavend.domain.model.WallpaperMetadata
import xyz.attacktive.wallhavend.domain.model.Wallpaper
import xyz.attacktive.wallhavend.domain.model.WallpaperIdentity
import xyz.attacktive.wallhavend.domain.model.WallpaperSource

@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class PexafyPhotoDto(
	@SerialName("photo_id") val photoId: String,
	@SerialName("image_url") val imageUrl: String? = null,
	val urls: PexafyUrlsDto? = null,
	val width: Int? = null,
	val height: Int? = null,
	val orientation: String? = null,
	val source: String? = null,
	@SerialName("license_type") val licenseType: String? = null,
	@SerialName("source_image_url") val sourceImageUrl: String? = null,
	@SerialName("photographer_username") val photographerUsername: String? = null,
	@SerialName("photographer_full_name") val photographerFullName: String? = null,
	@SerialName("photographer_url") val photographerUrl: String? = null,
	val attribution: PexafyAttributionDto? = null
) {
	val downloadUrl get() = listOfNotNull(imageUrl, urls?.full, urls?.large, urls?.regular)
		.firstOrNull { it.isNotBlank() }

	val resolvedThumbnailUrl get() = listOfNotNull(urls?.thumb, urls?.small)
		.firstOrNull { it.isNotBlank() }
}

@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class PexafyUrlsDto(
	val thumb: String? = null,
	val small: String? = null,
	val regular: String? = null,
	val large: String? = null,
	val full: String? = null
)

@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class PexafyAttributionDto(
	val html: String? = null,
	val plain: String? = null
)

fun PexafyPhotoDto.toDomain() = Wallpaper(
	identity = WallpaperIdentity(WallpaperSource.PEXAFY, photoId),
	directUrl = checkNotNull(downloadUrl),
	metadata = WallpaperMetadata(
		sourceUrl = sourceImageUrl,
		thumbnailUrl = resolvedThumbnailUrl,
		upstreamSource = source,
		licenseType = licenseType,
		photographerName = photographerFullName,
		photographerUsername = photographerUsername,
		photographerUrl = photographerUrl,
		attributionText = attribution?.plain,
		attributionHtml = attribution?.html
	)
)
