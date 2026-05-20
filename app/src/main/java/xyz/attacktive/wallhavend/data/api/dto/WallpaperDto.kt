package xyz.attacktive.wallhavend.data.api.dto

import android.annotation.SuppressLint
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import xyz.attacktive.wallhavend.domain.model.Wallpaper

@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class WallpaperDto(
	val id: String,
	val url: String,
	val path: String,
	val resolution: String,
	@SerialName("file_type") val fileType: String
)

fun WallpaperDto.toDomain() = Wallpaper(
	id = id,
	pageUrl = url,
	directUrl = path,
	resolution = resolution,
	mimeType = fileType
)
