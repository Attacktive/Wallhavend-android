package xyz.attacktive.wallhavend.data.api.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import android.annotation.SuppressLint

@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class WallhavenSearchResponseDto(val data: List<WallhavenWallpaperDto>, val meta: WallhavenMetaDto)

@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class WallhavenMetaDto(
	@SerialName("current_page") val currentPage: Int,
	@SerialName("last_page") val lastPage: Int,
	@SerialName("per_page") val perPage: Int,
	val total: Int
)
