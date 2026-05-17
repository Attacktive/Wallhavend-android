package xyz.attacktive.wallhavend.data.api.dto

import android.annotation.SuppressLint
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class SearchResponseDto(val data: List<WallpaperDto>, val meta: MetaDto)

@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class MetaDto(
	@SerialName("current_page") val currentPage: Int,
	@SerialName("last_page") val lastPage: Int,
	@SerialName("per_page") val perPage: Int,
	val total: Int
)
