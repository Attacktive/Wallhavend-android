package xyz.attacktive.wallhavend.domain.repository

import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import xyz.attacktive.wallhavend.data.api.WallhavenApiService
import xyz.attacktive.wallhavend.data.api.dto.WallpaperDto
import xyz.attacktive.wallhavend.data.api.dto.toDomain
import xyz.attacktive.wallhavend.domain.model.AppSettings
import xyz.attacktive.wallhavend.domain.model.NoResultsException
import xyz.attacktive.wallhavend.domain.model.Wallpaper
import xyz.attacktive.wallhavend.domain.model.toBitString
import xyz.attacktive.wallhavend.domain.service.WallpaperFileManager

@Singleton
class WallhavenRepository @Inject constructor(private val wallhavenApiService: WallhavenApiService, private val fileManager: WallpaperFileManager) {
	private var cache = ArrayDeque<WallpaperDto>()
	private var cacheKey: SearchKey? = null
	private var currentPage = 1
	private var lastPage = 1

	private data class SearchKey(
		val query: String?,
		val categories: String,
		val purity: String,
		val ratios: String?,
		val colors: String?,
		val apiKey: String?,
		val sorting: String,
		val toplistRange: String
	)

	private fun AppSettings.toSearchKey() = SearchKey(
		query = searchQuery.ifBlank { null },
		categories = categories.toBitString(),
		purity = purity.toBitString(),
		ratios = aspectRatio.ifBlank { null },
		colors = filterColor.ifBlank { null },
		apiKey = apiKey.ifBlank { null },
		sorting = sorting,
		toplistRange = toplistRange
	)

	suspend fun next(settings: AppSettings): Result<Pair<Wallpaper, File>> {
		val key = settings.toSearchKey()
		if (key != cacheKey) {
			cache.clear()
			cacheKey = key
			currentPage = 1
			lastPage = 1
		}

		if (cache.isEmpty()) {
			val fetchResult = refetch(key)
			if (fetchResult.isFailure) {
				return Result.failure(fetchResult.exceptionOrNull()!!)
			}
		}

		if (cache.isEmpty()) {
			return Result.failure(NoResultsException())
		}

		val dto = cache.removeFirst()
		val wallpaper = dto.toDomain()

		return fileManager.download(wallpaper)
			.map { file -> Pair(wallpaper, file) }
	}

	private suspend fun refetch(key: SearchKey) = runCatching {
		val pageToFetch = if (key.sorting == "random") {
			1
		} else {
			if (currentPage > lastPage) {
				1
			} else {
				currentPage
			}
		}

		val response = wallhavenApiService.search(
			query = key.query,
			categories = key.categories,
			purity = key.purity,
			ratios = key.ratios,
			sorting = key.sorting,
			seed = if (key.sorting == "random") {
				(('a'..'z') + ('A'..'Z') + ('0'..'9'))
					.shuffled()
					.take(6)
					.joinToString("")
			} else {
				null
			},
			topRange = if (key.sorting == "toplist") {
				key.toplistRange
			} else {
				null
			},
			page = pageToFetch,
			colors = key.colors,
			apiKey = key.apiKey
		)

		currentPage = response.meta.currentPage + 1
		lastPage = response.meta.lastPage

		cache = ArrayDeque(response.data)
	}
}
