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

	/** Sources still worth asking for the current search; one that answers with nothing is struck off rather than asked again. */
	private var liveSources = SOURCES.toMutableSet()

	private var pageCounts = mutableMapOf<PageWindow, Int>()

	private data class SearchKey(
		val keywords: List<String>,
		val license: String,
		val aspectRatio: String,
		val size: String?,
		val mature: Boolean,
		val minimumWidth: Int?,
		val minimumHeight: Int?
	)

	/**
	 * The depth cap applies per query, so each keyword and source pairing is its own result window with its own depth.
	 * Tracking the page count per pairing is what keeps a shallow window from deciding how deep a deep one may go.
	 */
	private data class PageWindow(val keyword: String?, val source: String)

	private fun AppSettings.toSearchKey(screenInfo: ScreenInfo) = SearchKey(
		keywords = keywords,
		license = licenseFilter.apiValue,
		aspectRatio = screenInfo.openverseAspectRatio(),
		/*
		 * Openverse buckets by filesize, which flickr's and nasa's index rows don't carry, so asking for a size at all drops those two outright.
		 * Worth it regardless: both cap their portrait images at 1024px on the long edge and so admit nothing on a modern screen either way, while asking raises wikimedia's admit rate.
		 */
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
			liveSources = SOURCES.toMutableSet()
			pageCounts.clear()
		}

		val selection = runCatching { selectNext(key, blockedIds) }
		val dto = selection.getOrElse { exception -> return@withLock Result.failure(exception) }
			?: return@withLock Result.failure(NoResultsException())

		Result.success(dto.toDomain())
	}

	/**
	 * Blocked wallpapers, records without a downloadable URL, and results too small for the screen are dropped after the fetch, so a page can arrive full and still leave nothing to pick.
	 * That earns another page rather than a "no results"; only running out of sources — or too many fruitless tries — gives up.
	 */
	private suspend fun selectNext(key: SearchKey, blockedIds: Set<String>): OpenverseImageDto? {
		var refetches = 0

		while (true) {
			while (cache.isNotEmpty() && cache.first().id in blockedIds) {
				cache.removeFirst()
			}

			if (cache.isNotEmpty()) {
				return cache.removeFirst()
			}

			if (refetches >= MAX_REFETCHES) {
				return null
			}

			val source = liveSources.randomOrNull() ?: return null

			/* An empty answer means this source holds nothing for the search at all, so striking it off costs a try that a source with results shouldn't have to pay for. */
			if (refetch(key, source).getOrThrow() == 0) {
				liveSources.remove(source)
			} else {
				refetches++
			}
		}
	}

	/**
	 * One source per fetch, rather than the whole allowlist at once.
	 * Ranking decides what fits inside a single query's 240, and it favours whichever source indexes the most, so asking for all of them at once buries the rest — a source only becomes reachable by being asked for on its own.
	 */
	private suspend fun refetch(key: SearchKey, source: String) = runCatching {
		val effectiveKeywords: List<String?> = key.keywords.ifEmpty { listOf(null) }
		val windows = effectiveKeywords.map { PageWindow(it, source) }

		val responses = coroutineScope {
			windows
				.map { window ->
					async {
						openverseApiService.search(
							query = window.keyword,
							license = key.license,
							source = window.source,
							extension = EXTENSIONS,
							aspectRatio = key.aspectRatio,
							size = key.size,
							mature = key.mature,
							page = pageToFetch(window),
							pageSize = PAGE_SIZE
						)
					}
				}
				.awaitAll()
		}

		windows.zip(responses)
			.forEach { (window, response) -> pageCounts[window] = response.pageCount }

		val results = responses.flatMap { it.results }

		cache = ArrayDeque(
			results.filter { it.url != null && key.admits(it) }
				.shuffled()
		)

		results.size
	}

	/**
	 * Openverse has no random sort, so variety comes from where in the result set the fetch lands.
	 * The first fetch of a window has to be page 1 — nothing yet says how deep that window goes — and every one after it picks at random within reach.
	 */
	private fun pageToFetch(window: PageWindow): Int {
		val knownPageCount = pageCounts[window] ?: return 1
		val reachablePages = minOf(knownPageCount, MAX_PAGES)

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
		/**
		 * Openverse indexes 52 sources and most are archival — herbarium sheets and scanned postcards make poor wallpapers — so the search only ever names one of these.
		 * SpaceX is deliberately absent: its stats endpoint claims 1,360 works, but it answers this query shape with nothing at every license tier and with no filters at all, so naming it only ever spends a request to be struck off.
		 */
		private val SOURCES = listOf("flickr", "wikimedia", "nasa", "rawpixel", "stocksnap")

		/** WallpaperFileManager only accepts JPEG and PNG, so anything else is ruled out server-side instead of downloaded and thrown away. */
		private const val EXTENSIONS = "jpg,png"

		/**
		 * Anonymous requests may ask for 20 results at a time and reach 240 results deep.
		 * An API key lifts only the first: authenticated callers face the same ceiling on total works per query and merely reach it in fewer round trips, so carrying one would buy no extra wallpapers.
		 * Going deeper needs expanded access, which Openverse grants case by case and can revoke, so the 240 is treated here as a fact about each query rather than a limit to negotiate around.
		 */
		private const val PAGE_SIZE = 20
		private const val MAX_DEPTH = 240
		private const val MAX_PAGES = MAX_DEPTH / PAGE_SIZE

		/** A page can come back full and still filter down to nothing, so those fetches get a bounded number of retries before the search gives up. */
		private const val MAX_REFETCHES = 5
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
