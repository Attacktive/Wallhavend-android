package xyz.attacktive.wallhavend.domain.repository

import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import xyz.attacktive.wallhavend.data.api.WallhavenApiService
import xyz.attacktive.wallhavend.data.api.dto.WallpaperDto
import xyz.attacktive.wallhavend.data.api.dto.toDomain
import xyz.attacktive.wallhavend.domain.model.AppSettings
import xyz.attacktive.wallhavend.domain.model.NoResultsException
import xyz.attacktive.wallhavend.domain.model.ScreenInfo
import xyz.attacktive.wallhavend.domain.model.Wallpaper
import xyz.attacktive.wallhavend.domain.model.query.Sorting
import xyz.attacktive.wallhavend.domain.model.query.toBitString
import xyz.attacktive.wallhavend.domain.service.WallpaperFileManager

@Singleton
class WallhavenRepository @Inject constructor(private val wallhavenApiService: WallhavenApiService, private val fileManager: WallpaperFileManager) {
	private val mutex = Mutex()
	private var cache = ArrayDeque<WallpaperDto>()
	private var cacheKey: SearchKey? = null
	private var currentPage = 1
	private var lastPage = 1

	private data class SearchKey(
		val keywords: List<String>,
		val categories: String,
		val purity: String,
		val ratios: String?,
		val atleast: String?,
		val colors: String?,
		val apiKey: String?,
		val sorting: Sorting,
		val toplistRange: String?
	)

	private fun AppSettings.toSearchKey(screenInfo: ScreenInfo) = SearchKey(
		keywords = searchQuery.trim().split(Regex("[,;|\\s]+")).filter { it.isNotBlank() },
		categories = categories.toBitString(),
		purity = purity.toBitString(),
		ratios = screenInfo.aspectRatio,
		atleast = if (avoidBlurryWallpapers) {
			"${screenInfo.width}x${screenInfo.height}"
		} else {
			null
		},
		colors = filterColor.ifBlank { null },
		apiKey = apiKey.ifBlank { null },
		sorting = sorting,
		toplistRange = if (sorting == Sorting.TOPLIST) {
			toplistRange.apiValue
		} else {
			null
		}
	)

	suspend fun next(settings: AppSettings, screenInfo: ScreenInfo): Result<Pair<Wallpaper, File>> = mutex.withLock {
		val key = settings.toSearchKey(screenInfo)
		if (key != cacheKey) {
			cache.clear()
			cacheKey = key
			currentPage = 1
			lastPage = 1
		}

		if (cache.isEmpty()) {
			val fetchResult = refetch(key)
			if (fetchResult.isFailure) {
				return@withLock Result.failure(fetchResult.exceptionOrNull()!!)
			}
		}

		if (cache.isEmpty()) {
			return@withLock Result.failure(NoResultsException())
		}

		val dto = cache.removeFirst()
		val wallpaper = dto.toDomain()

		fileManager.download(wallpaper)
			.map { file -> Pair(wallpaper, file) }
	}

	private suspend fun refetch(key: SearchKey) = runCatching {
		// Random sorting always fetches page 1 with a fresh seed.
		// Deterministic sorts walk through pages; after the last page, wrap back to 1.
		// (currentPage is advanced to response.currentPage + 1 after each fetch.)
		val pageToFetch = if (key.sorting == Sorting.RANDOM) {
			1
		} else {
			if (currentPage > lastPage) {
				currentPage = 1
				1
			} else {
				currentPage
			}
		}

		val effectiveKeywords: List<String?> = key.keywords.ifEmpty { listOf(null) }

		val responses = coroutineScope {
			effectiveKeywords
				.map { keyword ->
					async {
						wallhavenApiService.search(
							query = keyword,
							categories = key.categories,
							purity = key.purity,
							ratios = key.ratios,
							atleast = key.atleast,
							sorting = key.sorting.apiValue,
							seed = if (key.sorting == Sorting.RANDOM) {
								randomSeed()
							} else {
								null
							},
							topRange = key.toplistRange,
							page = pageToFetch,
							colors = key.colors,
							apiKey = key.apiKey
						)
					}
				}
				.awaitAll()
		}

		val firstResponse = responses.first()
		currentPage = firstResponse.meta.currentPage + 1
		lastPage = firstResponse.meta.lastPage

		cache = ArrayDeque(
			if (effectiveKeywords.size > 1) {
				responses.flatMap { it.data }
					.shuffled()
			} else {
				responses.single().data
			}
		)
	}

	private fun randomSeed() = (('a'..'z') + ('A'..'Z') + ('0'..'9'))
		.shuffled()
		.take(6)
		.joinToString("")
}
