package xyz.attacktive.wallhavend.data.api.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import android.annotation.SuppressLint

@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class PexafySearchResponseDto(
	val success: Boolean = true,
	val data: List<PexafyPhotoDto>? = emptyList(),
	val pagination: PexafyPaginationDto? = null,
	val error: PexafyErrorDto? = null
)

@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class PexafyPaginationDto(
	@SerialName("per_page") val perPage: Int = 20,
	@SerialName("has_more") val hasMore: Boolean = false,
	@SerialName("next_cursor") val nextCursor: String? = null
)

@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class PexafyErrorDto(
	val code: String? = null,
	val message: String? = null,
	@SerialName("request_id") val requestId: String? = null
)
