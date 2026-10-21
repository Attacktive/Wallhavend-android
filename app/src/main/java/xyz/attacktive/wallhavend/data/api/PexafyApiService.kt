package xyz.attacktive.wallhavend.data.api

import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query
import xyz.attacktive.wallhavend.data.api.dto.PexafySearchResponseDto

interface PexafyApiService {
	@GET("api/v1/search/photos")
	suspend fun search(
		@Header("x-api-key") apiKey: String,
		@Query("q") query: String? = null,
		@Query("orientation") orientation: String? = null,
		@Query("color_hex") colorHex: String? = null,
		@Query("sort_by") sortBy: String? = null,
		@Query("per_page") perPage: Int? = null,
		@Query("cursor") cursor: String? = null
	): PexafySearchResponseDto
}
