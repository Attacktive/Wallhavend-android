package xyz.attacktive.wallhavend.domain.repository

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import xyz.attacktive.wallhavend.data.api.OpenverseApiService
import xyz.attacktive.wallhavend.data.api.dto.OpenverseImageDto
import xyz.attacktive.wallhavend.data.api.dto.toDomain
import xyz.attacktive.wallhavend.domain.model.AppSettings
import xyz.attacktive.wallhavend.domain.model.NoResultsException
import xyz.attacktive.wallhavend.domain.model.ScreenInfo
import xyz.attacktive.wallhavend.domain.model.Wallpaper
import xyz.attacktive.wallhavend.domain.model.WallpaperSource
import xyz.attacktive.wallhavend.domain.model.keywords
import xyz.attacktive.wallhavend.domain.model.query.Purity

@Singleton
class OpenverseProvider @Inject constructor(private val openverseApiService: OpenverseApiService): WallpaperProvider {
	override val source = WallpaperSource.OPENVERSE

	private val mutex = Mutex()
	private var cache = ArrayDeque<OpenverseImageDto>()
	private var cacheKey: SearchKey? = null
	private var pageCount: Int? = null

	private data class SearchKey(
		val keywords: List<String>,
		val license: String,
		val aspectRatio: String,
		val size: String?,
		val mature: Boolean,
		val minimumWidth: Int?,
		val minimumHeight: Int?
	)

	private fun AppSettings.toSearchKey(screenInfo: ScreenInfo) = SearchKey(
		keywords = keywords,
		license = licenseFilter.apiValue,
		aspectRatio = screenInfo.openverseAspectRatio(),
		size = if (avoidBlurryWallpapers) {
			"large"
		} else {
			null
		},
		// Openverse's `mature` widens the results rather than restricting them to explicit ones, so it follows the one purity that means the same thing.
		mature = Purity.NSFW in purity,
		minimumWidth = if (avoidBlurryWallpapers) {
			screenInfo.width
		} else {
			null
		},
		minimumHeight = if (avoidBlurryWallpapers) {
			screenInfo.height
		} else {
			null
		}
	)

	override suspend fun next(settings: AppSettings, screenInfo: ScreenInfo, blockedIds: Set<String>): Result<Wallpaper> = mutex.withLock {
		val key = settings.toSearchKey(screenInfo)
		if (key != cacheKey) {
			cache.clear()
			cacheKey = key
			pageCount = null
		}

		val selection = runCatching { selectNext(key, blockedIds) }
		val dto = selection.getOrElse { exception -> return@withLock Result.failure(exception) }
			?: return@withLock Result.failure(NoResultsException())

		Result.success(dto.toDomain())
	}

	/**
	 * Blocked wallpapers, records without a downloadable URL, and results too small for the screen are dropped after the fetch, so a page can arrive full and still leave nothing to pick.
	 * That earns another page rather than a "no results"; only a page the API returned empty — or too many fruitless tries — gives up.
	 */
	private suspend fun selectNext(key: SearchKey, blockedIds: Set<String>): OpenverseImageDto? {
		val maxRefetches = 5
		var refetches = 0

		while (true) {
			while (cache.isNotEmpty() && cache.first().id in blockedIds) {
				cache.removeFirst()
			}

			if (cache.isNotEmpty()) {
				return cache.removeFirst()
			}

			if (refetches >= maxRefetches) {
				return null
			}

			refetches++
			val fetchedCount = refetch(key).getOrThrow()
			if (fetchedCount == 0) {
				return null
			}
		}
	}

	private suspend fun refetch(key: SearchKey) = runCatching {
		val effectiveKeywords: List<String?> = key.keywords.ifEmpty { listOf(null) }
		val page = pageToFetch()

		val responses = coroutineScope {
			effectiveKeywords
				.map { keyword ->
					async {
						openverseApiService.search(
							query = keyword,
							license = key.license,
							source = SOURCE_ALLOWLIST,
							extension = EXTENSIONS,
							aspectRatio = key.aspectRatio,
							size = key.size,
							mature = key.mature,
							page = page,
							pageSize = PAGE_SIZE
						)
					}
				}
				.awaitAll()
		}

		// The narrowest keyword decides how deep the next page may go, since one page number is shared by the whole fan-out.
		pageCount = responses.minOf { it.pageCount }

		val results = responses.flatMap { it.results }

		cache = ArrayDeque(
			results.filter { it.url != null && key.admits(it) }
				.shuffled()
		)

		results.size
	}

	/**
	 * Openverse has no random sort, so variety comes from where in the result set the fetch lands.
	 * The first fetch has to be page 1 — nothing yet says how many pages there are — and every one after it picks at random within reach.
	 */
	private fun pageToFetch(): Int {
		val knownPageCount = pageCount ?: return 1
		val reachablePages = minOf(knownPageCount, MAX_DEPTH / PAGE_SIZE)

		return (1..reachablePages.coerceAtLeast(1)).random()
	}

	/** Openverse's own `size` filter is a coarse small/medium/large bucket, so "at least as large as the screen" is settled here; a result that never reported its dimensions can't prove it qualifies. */
	private fun SearchKey.admits(image: OpenverseImageDto): Boolean {
		if (minimumWidth == null || minimumHeight == null) {
			return true
		}

		val wideEnough = (image.width ?: 0) >= minimumWidth
		val tallEnough = (image.height ?: 0) >= minimumHeight

		return wideEnough && tallEnough
	}

	companion object {
		/** Openverse indexes 52 sources and most are archival — herbarium sheets and scanned postcards make poor wallpapers — so the query names the ones that don't. */
		private const val SOURCE_ALLOWLIST = "flickr,wikimedia,nasa,spacex,rawpixel,stocksnap"

		/** WallpaperFileManager only accepts JPEG and PNG, so anything else is ruled out server-side instead of downloaded and thrown away. */
		private const val EXTENSIONS = "jpg,png"

		/**
		 * Anonymous requests may ask for 20 results at a time and reach 240 results deep.
		 * An API key would lift both; the app deliberately doesn't carry one.
		 */
		private const val PAGE_SIZE = 20
		private const val MAX_DEPTH = 240
	}
}

/** Openverse buckets every ratio into three, so the screen's own has to be rounded to the nearest of them. */
private fun ScreenInfo.openverseAspectRatio(): String {
	val squareTolerance = 0.05f
	val ratio = width.toFloat() / height

	return when {
		ratio < 1 - squareTolerance -> "tall"
		ratio > 1 + squareTolerance -> "wide"
		else -> "square"
	}
}
