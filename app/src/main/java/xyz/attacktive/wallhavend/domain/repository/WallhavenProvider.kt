package xyz.attacktive.wallhavend.domain.repository

import java.math.BigInteger
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import xyz.attacktive.wallhavend.data.api.WallhavenApiService
import xyz.attacktive.wallhavend.data.api.dto.WallhavenWallpaperDto
import xyz.attacktive.wallhavend.data.api.dto.toDomain
import xyz.attacktive.wallhavend.domain.model.AppSettings
import xyz.attacktive.wallhavend.domain.model.NoResultsException
import xyz.attacktive.wallhavend.domain.model.ScreenInfo
import xyz.attacktive.wallhavend.domain.model.Wallpaper
import xyz.attacktive.wallhavend.domain.model.WallpaperSource
import xyz.attacktive.wallhavend.domain.model.keywords
import xyz.attacktive.wallhavend.domain.model.query.Sorting
import xyz.attacktive.wallhavend.domain.model.query.toBitString

@Singleton
class WallhavenProvider @Inject constructor(
	private val wallhavenApiService: WallhavenApiService,
	private val selectionStore: WallhavenSelectionStore
): WallpaperProvider {
	override val source = WallpaperSource.WALLHAVEN

	private val mutex = Mutex()

	internal constructor(wallhavenApiService: WallhavenApiService): this(
		wallhavenApiService,
		InMemoryWallhavenSelectionStore()
	)

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
		keywords = keywords,
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

	override suspend fun next(settings: AppSettings, screenInfo: ScreenInfo, blockedIds: Set<String>): Result<Wallpaper> = mutex.withLock {
		val key = settings.toSearchKey(screenInfo)
		val fingerprint = key.fingerprint()
		var state = selectionStore.read()
		if (state.searchKeyFingerprint != fingerprint) {
			state = WallhavenSelectionState(searchKeyFingerprint = fingerprint)
		}

		val selection = runCatching { selectNext(key, blockedIds, state) }
		val dto = selection.getOrElse { exception -> return@withLock Result.failure(exception) }
			?: return@withLock Result.failure(NoResultsException())

		Result.success(dto.toDomain())
	}

	/*
	 * Blocked wallpapers are filtered after the fetch, so they still occupy slots in a returned page.
	 * Drop them when picking, and when a whole page comes back blocked, advance to the next page rather than giving up.
	 * A genuinely empty page stops immediately, and an all-blocked search is bounded so it cannot loop forever before falling back to NoResults.
	 */
	private suspend fun selectNext(
		key: SearchKey,
		blockedIds: Set<String>,
		initialState: WallhavenSelectionState
	): WallhavenWallpaperDto? {
		val maxBlockedPageRefetches = 5
		var refetches = 0
		var state = initialState

		while (true) {
			val usableCache = state.cache.dropWhile { it.id in blockedIds }
			if (usableCache.isNotEmpty()) {
				val selected = usableCache.first()
				state = state.copy(cache = usableCache.drop(1))
				selectionStore.write(state)

				return selected
			}

			if (usableCache != state.cache) {
				state = state.copy(cache = usableCache)
			}

			if (refetches >= maxBlockedPageRefetches) {
				selectionStore.write(state)

				return null
			}

			refetches++
			state = refetch(key, state).getOrThrow()
			if (state.cache.isEmpty()) {
				selectionStore.write(state)

				return null
			}
		}
	}

	private suspend fun refetch(key: SearchKey, state: WallhavenSelectionState) = runCatching {
		// Random sorting always fetches page 1 with a fresh seed.
		// Deterministic sorts walk through pages; after the last page, wrap back to 1.
		// currentPage is advanced to response.currentPage + 1 after each fetch.
		val pageToFetch = if (key.sorting == Sorting.RANDOM) {
			1
		} else if (state.currentPage > state.lastPage) {
			1
		} else {
			state.currentPage
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
		val cache = if (effectiveKeywords.size > 1) {
			responses.flatMap { it.data }
				.shuffled()
		} else {
			responses.single().data
		}

		state.copy(
			cache = cache,
			currentPage = firstResponse.meta.currentPage + 1,
			lastPage = firstResponse.meta.lastPage
		)
	}

	private fun SearchKey.fingerprint(): String {
		val input = buildString {
			appendFingerprintPart(keywords.size.toString())
			keywords.forEach { appendFingerprintPart(it) }
			appendFingerprintPart(categories)
			appendFingerprintPart(purity)
			appendFingerprintPart(ratios)
			appendFingerprintPart(atleast)
			appendFingerprintPart(colors)
			appendFingerprintPart(apiKey)
			appendFingerprintPart(sorting.apiValue)
			appendFingerprintPart(toplistRange)
		}

		val digest = MessageDigest.getInstance("SHA-256")
			.digest(input.toByteArray(Charsets.UTF_8))

		return BigInteger(1, digest)
			.toString(16)
			.padStart(64, '0')
	}

	private fun StringBuilder.appendFingerprintPart(value: String?) {
		if (value == null) {
			append("-1:")
		} else {
			append(value.length)
				.append(':')
				.append(value)
		}

		append('|')
	}

	private fun randomSeed() = (('a'..'z') + ('A'..'Z') + ('0'..'9'))
		.shuffled()
		.take(6)
		.joinToString("")
}
