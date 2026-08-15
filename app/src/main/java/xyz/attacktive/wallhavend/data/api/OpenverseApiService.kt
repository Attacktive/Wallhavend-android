package xyz.attacktive.wallhavend.data.api

import retrofit2.http.GET
import retrofit2.http.Query
import xyz.attacktive.wallhavend.data.api.dto.OpenverseSearchResponseDto

interface OpenverseApiService {
	/** The trailing slash is load-bearing: without it the API answers 301 to the slashed form, costing a round trip on every search. */
	@GET("v1/images/")
	suspend fun search(
		@Query("q") query: String?,
		@Query("license") license: String,
		@Query("source") source: String,
		@Query("extension") extension: String,
		@Query("aspect_ratio") aspectRatio: String,
		@Query("size") size: String?,
		@Query("mature") mature: Boolean,
		@Query("page") page: Int,
		@Query("page_size") pageSize: Int
	): OpenverseSearchResponseDto
}
