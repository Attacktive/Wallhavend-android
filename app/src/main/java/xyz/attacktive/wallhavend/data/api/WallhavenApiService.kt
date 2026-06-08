package xyz.attacktive.wallhavend.data.api

import retrofit2.http.GET
import retrofit2.http.Query
import xyz.attacktive.wallhavend.data.api.dto.SearchResponseDto

interface WallhavenApiService {
	@GET("search")
	suspend fun search(
		@Query("q") query: String?,
		@Query("categories") categories: String,
		@Query("purity") purity: String,
		@Query(value = "ratios", encoded = true) ratios: String?,
		@Query("sorting") sorting: String,
		@Query("seed") seed: String?,
		@Query("topRange") topRange: String?,
		@Query("page") page: Int?,
		@Query("colors") colors: String?,
		@Query("apikey") apiKey: String?
	): SearchResponseDto
}
