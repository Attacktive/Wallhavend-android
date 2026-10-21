package xyz.attacktive.wallhavend.domain.repository

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import xyz.attacktive.wallhavend.data.api.PexafyApiService
import xyz.attacktive.wallhavend.data.api.dto.PexafyPhotoDto
import xyz.attacktive.wallhavend.data.api.dto.PexafySearchResponseDto
import xyz.attacktive.wallhavend.data.api.dto.toDomain
import xyz.attacktive.wallhavend.domain.model.AppSettings
import xyz.attacktive.wallhavend.domain.model.InvalidApiKeyException
import xyz.attacktive.wallhavend.domain.model.MissingApiKeyException
import xyz.attacktive.wallhavend.domain.model.NoResultsException
import xyz.attacktive.wallhavend.domain.model.QuotaExceededException
import xyz.attacktive.wallhavend.domain.model.RateLimitedException
import xyz.attacktive.wallhavend.domain.model.ScreenInfo
import xyz.attacktive.wallhavend.domain.model.Wallpaper
import xyz.attacktive.wallhavend.domain.model.WallpaperSource
import xyz.attacktive.wallhavend.domain.model.keywords
import xyz.attacktive.wallhavend.domain.model.query.Sorting

@Singleton
class PexafyProvider(
	private val pexafyApiService: PexafyApiService,
	private val clock: () -> Long
): WallpaperProvider {
	@Inject
	constructor(pexafyApiService: PexafyApiService): this(pexafyApiService, System::currentTimeMillis)

	override val source = WallpaperSource.PEXAFY

	private val mutex = Mutex()
	private var cache = ArrayDeque<PexafyPhotoDto>()
	private var cachePopulatedAtMs = 0L
	private var cacheKey: SearchKey? = null
	private var cursorStates = mutableMapOf<QueryWindow, CursorState>()
	private var servedIds = mutableSetOf<String>()
	private var lastApiKey: String? = null
	private var rateLimitedUntilMs = 0L

	private data class SearchKey(
		val apiKey: String,
		val keywords: List<String>,
		val orientation: String,
		val colorHexes: List<String>,
		val sortBy: String,
		val minimumWidth: Int?,
		val minimumHeight: Int?
	)

	private data class QueryWindow(val keyword: String?, val colorHex: String?)

	private data class CursorState(
		val nextCursor: String?,
		val hasMore: Boolean,
		val issuedAtMs: Long
	)

	private fun AppSettings.toSearchKey(screenInfo: ScreenInfo) = SearchKey(
		apiKey = pexafyApiKey.trim(),
		keywords = keywords,
		orientation = screenInfo.pexafyOrientation(),
		colorHexes = pexafyColorHexes(),
		sortBy = sorting.toPexafySortBy(),
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
		val precheckFailure = prepareSearchState(key)
		if (precheckFailure != null) {
			return@withLock Result.failure(precheckFailure)
		}

		val selection = runCatching { selectNext(key, blockedIds) }
		val dto = selection.getOrElse { exception -> return@withLock Result.failure(exception) }
			?: return@withLock Result.failure(NoResultsException())

		servedIds.add(dto.photoId)

		Result.success(dto.toDomain())
	}

	private fun prepareSearchState(key: SearchKey): Throwable? {
		if (key.apiKey.isEmpty()) {
			return MissingApiKeyException(WallpaperSource.PEXAFY)
		}

		if (key.apiKey != lastApiKey) {
			lastApiKey = key.apiKey
			rateLimitedUntilMs = 0L
		}

		val now = clock()
		if (now < rateLimitedUntilMs) {
			val remainingSeconds = ((rateLimitedUntilMs - now + 999L) / 1_000L).coerceAtLeast(1L)

			return RateLimitedException(WallpaperSource.PEXAFY, remainingSeconds)
		}

		refreshCacheLifecycle(key, now)

		return null
	}

	private fun refreshCacheLifecycle(key: SearchKey, now: Long) {
		if (key != cacheKey) {
			cache.clear()
			cachePopulatedAtMs = 0L
			cacheKey = key
			cursorStates.clear()
			servedIds.clear()
		} else if (cache.isNotEmpty() && now - cachePopulatedAtMs >= CACHE_TTL_MS) {
			// Pexafy's terms cap search response caching at 24 hours.
			cache.clear()
			cachePopulatedAtMs = 0L
		}
	}

	private suspend fun selectNext(key: SearchKey, blockedIds: Set<String>): PexafyPhotoDto? {
		var refetches = 0
		var fallbackCandidate: PexafyPhotoDto? = null

		while (true) {
			val (freshCandidate, seenCandidate) = pollCachedCandidate(blockedIds)
			if (fallbackCandidate == null) {
				fallbackCandidate = seenCandidate
			}

			if (freshCandidate != null) {
				return freshCandidate
			}

			val windows = nextRefetchWindows(key, refetches) ?: return recycleFallback(fallbackCandidate)
			refetches++

			val fetchedCount = refetch(key, windows).getOrThrow()
			if (isExhausted(fetchedCount)) {
				return recycleFallback(fallbackCandidate)
			}
		}
	}

	private fun pollCachedCandidate(blockedIds: Set<String>): Pair<PexafyPhotoDto?, PexafyPhotoDto?> {
		var firstSeen: PexafyPhotoDto? = null

		while (cache.isNotEmpty()) {
			val candidate = cache.removeFirst()
			if (candidate.photoId !in blockedIds) {
				if (candidate.photoId !in servedIds) {
					return candidate to firstSeen
				}

				if (firstSeen == null) {
					firstSeen = candidate
				}
			}
		}

		return null to firstSeen
	}

	private fun nextRefetchWindows(key: SearchKey, refetches: Int): List<QueryWindow>? {
		if (refetches >= MAX_REFETCHES) {
			return null
		}

		return activeWindowsFor(key).takeIf { it.isNotEmpty() }
	}

	private fun isExhausted(fetchedCount: Int) = fetchedCount == 0 && cursorStates.values.none { it.hasMore }

	private fun recycleFallback(fallbackCandidate: PexafyPhotoDto?): PexafyPhotoDto? {
		if (fallbackCandidate != null) {
			servedIds.clear()
		}

		return fallbackCandidate
	}

	private fun activeWindowsFor(key: SearchKey): List<QueryWindow> {
		val effectiveKeywords: List<String?> = key.keywords.ifEmpty { listOf(null) }
		val effectiveColors: List<String?> = key.colorHexes.ifEmpty { listOf(null) }
		val allWindows = effectiveKeywords.flatMap { keyword ->
			effectiveColors.map { color -> QueryWindow(keyword, color) }
		}
		val now = clock()

		return allWindows.filter { window ->
			val state = cursorStates[window]
			state == null || state.hasMore || now - state.issuedAtMs >= CURSOR_TTL_MS
		}
	}

	private suspend fun refetch(key: SearchKey, windows: List<QueryWindow>) = runCatching {
		val responses = coroutineScope {
			windows
				.map { window ->
					async {
						fetchWindow(key, window)
					}
				}
				.awaitAll()
		}

		val fetchedAt = clock()
		windows.zip(responses)
			.forEach { (window, response) ->
				val pagination = response.pagination
				val hasMore = pagination?.hasMore == true && !pagination.nextCursor.isNullOrBlank()
				cursorStates[window] = CursorState(
					nextCursor = pagination?.nextCursor?.takeIf { hasMore },
					hasMore = hasMore,
					issuedAtMs = fetchedAt
				)
			}

		val results = responses.flatMap { it.data.orEmpty() }
		val admitted = results
			.filter { it.downloadUrl != null && key.admits(it) }
			.shuffled()

		cache = ArrayDeque(admitted)
		cachePopulatedAtMs = fetchedAt

		results.size
	}

	private suspend fun fetchWindow(key: SearchKey, window: QueryWindow): PexafySearchResponseDto {
		val cursor = validCursorFor(window)

		return try {
			validateResponse(requestWindow(key, window, cursor))
		} catch (exception: HttpException) {
			if (isRejectedCursor(cursor, exception)) {
				cursorStates.remove(window)

				return validateResponse(requestWindow(key, window, null))
			}

			throw mapHttpException(exception)
		}
	}

	private suspend fun requestWindow(key: SearchKey, window: QueryWindow, cursor: String?) = pexafyApiService.search(
		apiKey = key.apiKey,
		query = window.keyword,
		orientation = key.orientation,
		colorHex = window.colorHex,
		sortBy = key.sortBy,
		perPage = PAGE_SIZE,
		cursor = cursor
	)

	private fun validateResponse(response: PexafySearchResponseDto): PexafySearchResponseDto {
		if (!response.success && response.error != null) {
			throw mapErrorCode(response.error.code, null)
				?: IllegalStateException(response.error.message ?: "Pexafy search failed")
		}

		return response
	}

	private fun isRejectedCursor(cursor: String?, exception: HttpException) =
		cursor != null && (exception.code() == 400 || exception.code() == 422)

	private fun validCursorFor(window: QueryWindow): String? {
		val state = cursorStates[window] ?: return null
		if (!state.hasMore || state.nextCursor == null) {
			return null
		}

		if (clock() - state.issuedAtMs >= CURSOR_TTL_MS) {
			return null
		}

		return state.nextCursor
	}

	private fun mapHttpException(exception: HttpException): Throwable {
		val code = exception.code()
		val retryAfter = exception.response()
			?.headers()
			?.get("Retry-After")
			?.trim()
			?.toLongOrNull()

		val errorBody = runCatching {
			exception.response()
				?.errorBody()
				?.string()
		}.getOrNull()

		val errorCode = errorBody?.let { body ->
			runCatching {
				ERROR_JSON.decodeFromString<PexafySearchResponseDto>(body).error?.code
			}.getOrNull()
		}

		mapErrorCode(errorCode, retryAfter)?.let { mapped -> return mapped }

		return when (code) {
			401, 403 -> InvalidApiKeyException(WallpaperSource.PEXAFY)
			429 -> {
				val cooldownSeconds = retryAfter ?: DEFAULT_RETRY_AFTER_SECONDS
				rateLimitedUntilMs = clock() + cooldownSeconds * 1_000L
				RateLimitedException(WallpaperSource.PEXAFY, retryAfter)
			}
			else -> exception
		}
	}

	private fun mapErrorCode(errorCode: String?, retryAfter: Long?) = when (errorCode) {
		"UNAUTHORIZED", "FORBIDDEN", "PLAN_RESTRICTION" -> InvalidApiKeyException(WallpaperSource.PEXAFY)
		"QUOTA_EXCEEDED" -> QuotaExceededException(WallpaperSource.PEXAFY)
		"RATE_LIMITED" -> {
			val cooldownSeconds = retryAfter ?: DEFAULT_RETRY_AFTER_SECONDS
			rateLimitedUntilMs = clock() + cooldownSeconds * 1_000L
			RateLimitedException(WallpaperSource.PEXAFY, retryAfter)
		}
		else -> null
	}

	/**
	 * When exact dimensions are present in the search DTO, filter undersized images up front.
	 * On Pexafy's Free/Starter tiers where width and height may be omitted from search responses, admit the candidate here and let the download step verify decoded bounds.
	 */
	private fun SearchKey.admits(photo: PexafyPhotoDto): Boolean {
		if (minimumWidth == null || minimumHeight == null) {
			return true
		}

		val width = photo.width ?: return true
		val height = photo.height ?: return true

		return width >= minimumWidth && height >= minimumHeight
	}

	companion object {
		private const val PAGE_SIZE = 20
		private const val MAX_REFETCHES = 5
		private const val CURSOR_TTL_MS = 5 * 60 * 1_000L
		private const val CACHE_TTL_MS = 24 * 60 * 60 * 1_000L
		private const val DEFAULT_RETRY_AFTER_SECONDS = 60L

		private val ERROR_JSON = Json { ignoreUnknownKeys = true }
	}
}

private fun ScreenInfo.pexafyOrientation(): String {
	val squareTolerance = 0.05f
	val ratio = width.toFloat() / height

	return when {
		ratio < 1 - squareTolerance -> "portrait"
		ratio > 1 + squareTolerance -> "landscape"
		else -> "square"
	}
}

private fun AppSettings.pexafyColorHexes() = filterColor
	.split(",")
	.map { it.trim().removePrefix("#").lowercase() }
	.filter { it.isNotEmpty() }
	.map { "#$it" }

private fun Sorting.toPexafySortBy() = when (this) {
	Sorting.DATE_ADDED -> "newest"
	else -> "relevance"
}
