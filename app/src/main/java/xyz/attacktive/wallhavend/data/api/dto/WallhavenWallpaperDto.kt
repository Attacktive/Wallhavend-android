package xyz.attacktive.wallhavend.data.api.dto

import kotlinx.serialization.Serializable
import android.annotation.SuppressLint
import xyz.attacktive.wallhavend.domain.model.Wallpaper
import xyz.attacktive.wallhavend.domain.model.WallpaperIdentity
import xyz.attacktive.wallhavend.domain.model.WallpaperSource

@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class WallhavenWallpaperDto(val id: String, val path: String, val resolution: String)

fun WallhavenWallpaperDto.toDomain() = Wallpaper(
	identity = WallpaperIdentity(WallpaperSource.WALLHAVEN, id),
	directUrl = path,
	resolution = resolution
)
