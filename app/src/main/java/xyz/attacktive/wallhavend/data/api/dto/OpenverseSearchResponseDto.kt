package xyz.attacktive.wallhavend.data.api.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import android.annotation.SuppressLint

@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class OpenverseSearchResponseDto(
	@SerialName("page_count") val pageCount: Int,
	val page: Int,
	val results: List<OpenverseImageDto>
)
