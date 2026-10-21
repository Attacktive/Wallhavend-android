package xyz.attacktive.wallhavend

import java.io.IOException
import kotlinx.coroutines.test.runTest
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertFalse
import retrofit2.Retrofit
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import xyz.attacktive.wallhavend.data.api.PexafyApiService
import xyz.attacktive.wallhavend.data.api.dto.PexafyAttributionDto
import xyz.attacktive.wallhavend.data.api.dto.PexafyPaginationDto
import xyz.attacktive.wallhavend.data.api.dto.PexafyPhotoDto
import xyz.attacktive.wallhavend.data.api.dto.PexafySearchResponseDto
import xyz.attacktive.wallhavend.data.api.dto.PexafyUrlsDto
import xyz.attacktive.wallhavend.domain.model.AppSettings
import xyz.attacktive.wallhavend.domain.model.InvalidApiKeyException
import xyz.attacktive.wallhavend.domain.model.MissingApiKeyException
import xyz.attacktive.wallhavend.domain.model.NoResultsException
import xyz.attacktive.wallhavend.domain.model.QuotaExceededException
import xyz.attacktive.wallhavend.domain.model.RateLimitedException
import xyz.attacktive.wallhavend.domain.model.ScreenInfo
import xyz.attacktive.wallhavend.domain.model.WallpaperSource
import xyz.attacktive.wallhavend.domain.model.query.Sorting
import xyz.attacktive.wallhavend.domain.repository.PexafyProvider

class PexafyProviderTest {
	private val pexafyApiService = mockk<PexafyApiService>()
	private var currentTimeMs = 1_000_000L
	private lateinit var provider: PexafyProvider

	private val portraitScreen = ScreenInfo("9x16", 1080, 2400)
	private val landscapeScreen = ScreenInfo("16x9", 2400, 1080)
	private val squareScreen = ScreenInfo("1x1", 1440, 1440)

	private fun settingsWithKey(
		pexafyApiKey: String = "pexafy_api_test_key",
		searchQuery: String = "",
		filterColor: String = "",
		sorting: Sorting = Sorting.RANDOM,
		avoidBlurryWallpapers: Boolean = false
	) = AppSettings(
		enabledSources = setOf(WallpaperSource.PEXAFY),
		pexafyApiKey = pexafyApiKey,
		searchQuery = searchQuery,
		filterColor = filterColor,
		sorting = sorting,
		avoidBlurryWallpapers = avoidBlurryWallpapers
	)

	private fun makePhoto(
		id: String,
		imageUrl: String? = "https://images.pexels.com/photos/$id.jpeg",
		fullUrl: String? = "https://images.pexels.com/photos/$id.jpeg?w=2400",
		thumbnailUrl: String? = "https://images.pexels.com/photos/$id.jpeg?w=200",
		width: Int? = 4000,
		height: Int? = 4000
	) = PexafyPhotoDto(
		photoId = id,
		imageUrl = imageUrl,
		urls = PexafyUrlsDto(
			thumb = thumbnailUrl,
			small = "https://images.pexels.com/photos/$id.jpeg?w=400",
			regular = "https://images.pexels.com/photos/$id.jpeg?w=1280",
			large = "https://images.pexels.com/photos/$id.jpeg?w=1920",
			full = fullUrl
		),
		width = width,
		height = height,
		orientation = "portrait",
		source = "Pexels",
		licenseType = "free",
		sourceImageUrl = "https://www.pexels.com/photo/$id",
		photographerUsername = "JaneDoe",
		photographerFullName = "Jane Doe",
		photographerUrl = "https://www.pexels.com/@janedoe",
		attribution = PexafyAttributionDto(
			html = "<span>Photo by Jane Doe on Pexels</span>",
			plain = "Photo by Jane Doe on Pexels"
		)
	)

	private fun makePage(
		vararg photos: PexafyPhotoDto,
		hasMore: Boolean = false,
		nextCursor: String? = null
	) = PexafySearchResponseDto(
		success = true,
		data = photos.toList(),
		pagination = PexafyPaginationDto(
			perPage = 20,
			hasMore = hasMore,
			nextCursor = nextCursor
		)
	)

	private fun httpError(code: Int, errorCode: String, retryAfter: String? = null): HttpException {
		val body = """{"success":false,"data":null,"error":{"code":"$errorCode","message":"$errorCode"}}"""
			.toResponseBody("application/json".toMediaType())
		val rawResponse = okhttp3.Response.Builder()
			.code(code)
			.message("Error")
			.protocol(Protocol.HTTP_1_1)
			.request(
				Request.Builder()
					.url("https://api.pexafy.com/api/v1/search/photos")
					.build()
			)
			.apply {
				if (retryAfter != null) {
					header("Retry-After", retryAfter)
				}
			}
			.build()

		return HttpException(Response.error<Any>(body, rawResponse))
	}

	private fun everySearch() = coEvery {
		pexafyApiService.search(any(), any(), any(), any(), any(), any(), any())
	}

	@Before
	fun setUp() {
		currentTimeMs = 1_000_000L
		provider = PexafyProvider(pexafyApiService) { currentTimeMs }
	}

	@Test
	fun `next fails with MissingApiKeyException when Pexafy API key is blank`() = runTest {
		val result = provider.next(settingsWithKey(pexafyApiKey = "   "), portraitScreen, emptySet())

		assertTrue(result.exceptionOrNull() is MissingApiKeyException)
		coVerify(exactly = 0) { pexafyApiService.search(any(), any(), any(), any(), any(), any(), any()) }
	}

	@Test
	fun `next sends x-api-key header and qualifies returned wallpaper as Pexafy`() = runTest {
		everySearch() returns makePage(makePhoto("019e1a0d-1a4f-7d62-856f-e6775b49785d"))

		val result = provider.next(settingsWithKey(), portraitScreen, emptySet())

		assertTrue(result.isSuccess)
		val wallpaper = result.getOrThrow()
		assertEquals(WallpaperSource.PEXAFY, wallpaper.identity.source)
		assertEquals("019e1a0d-1a4f-7d62-856f-e6775b49785d", wallpaper.identity.id)
		assertEquals("pexafy_019e1a0d-1a4f-7d62-856f-e6775b49785d", wallpaper.identity.qualified)
		assertEquals("https://images.pexels.com/photos/019e1a0d-1a4f-7d62-856f-e6775b49785d.jpeg", wallpaper.directUrl)
		assertEquals("https://images.pexels.com/photos/019e1a0d-1a4f-7d62-856f-e6775b49785d.jpeg?w=200", wallpaper.thumbnailUrl)
		assertEquals("https://www.pexels.com/photo/019e1a0d-1a4f-7d62-856f-e6775b49785d", wallpaper.metadata?.sourceUrl)
		assertEquals("Pexels", wallpaper.metadata?.upstreamSource)
		assertEquals("free", wallpaper.metadata?.licenseType)
		assertEquals("Jane Doe", wallpaper.metadata?.photographerName)
		assertEquals("Photo by Jane Doe on Pexels", wallpaper.metadata?.attributionText)

		coVerify(exactly = 1) {
			pexafyApiService.search(
				apiKey = "pexafy_api_test_key",
				query = null,
				orientation = "portrait",
				colorHex = null,
				sortBy = "relevance",
				perPage = 20,
				cursor = null
			)
		}
	}

	@Test
	fun `falls back to urls full when top-level image_url is omitted on Free plan`() = runTest {
		everySearch() returns makePage(
			makePhoto(
				id = "free-tier-photo",
				imageUrl = null,
				fullUrl = "https://images.unsplash.com/photo-1?w=2400"
			)
		)

		val wallpaper = provider.next(settingsWithKey(), portraitScreen, emptySet()).getOrThrow()

		assertEquals("https://images.unsplash.com/photo-1?w=2400", wallpaper.directUrl)
	}

	@Test
	fun `blank search sends orientation filter without inventing default keywords`() = runTest {
		everySearch() returns makePage(makePhoto("p1"))

		provider.next(settingsWithKey(searchQuery = ""), portraitScreen, emptySet())
		provider.next(settingsWithKey(searchQuery = ""), landscapeScreen, emptySet())
		provider.next(settingsWithKey(searchQuery = ""), squareScreen, emptySet())

		coVerify(exactly = 1) { pexafyApiService.search(any(), null, "portrait", any(), any(), any(), any()) }
		coVerify(exactly = 1) { pexafyApiService.search(any(), null, "landscape", any(), any(), any(), any()) }
		coVerify(exactly = 1) { pexafyApiService.search(any(), null, "square", any(), any(), any(), any()) }
	}

	@Test
	fun `multiple keywords fan out into separate queries`() = runTest {
		everySearch() returns makePage(makePhoto("p1"))

		provider.next(settingsWithKey(searchQuery = "misty forest, mountain lake"), portraitScreen, emptySet())

		coVerify(exactly = 1) { pexafyApiService.search(any(), "misty forest", "portrait", any(), any(), any(), any()) }
		coVerify(exactly = 1) { pexafyApiService.search(any(), "mountain lake", "portrait", any(), any(), any(), any()) }
	}

	@Test
	fun `filterColor maps to hash-prefixed color_hex and sorting maps DATE_ADDED to newest`() = runTest {
		everySearch() returns makePage(makePhoto("p1"))

		provider.next(
			settingsWithKey(filterColor = "cc0000", sorting = Sorting.DATE_ADDED),
			portraitScreen,
			emptySet()
		)

		coVerify(exactly = 1) {
			pexafyApiService.search(
				apiKey = "pexafy_api_test_key",
				query = null,
				orientation = "portrait",
				colorHex = "#cc0000",
				sortBy = "newest",
				perPage = 20,
				cursor = null
			)
		}
	}

	@Test
	fun `avoidBlurryWallpapers drops undersized DTOs and admits Free-plan DTOs whose dimensions are null`() = runTest {
		val undersized = makePhoto("small", width = 800, height = 1200)
		val unknownDimensions = makePhoto("sizeless", width = null, height = null)
		everySearch() returns makePage(undersized, unknownDimensions)

		val result = provider.next(settingsWithKey(avoidBlurryWallpapers = true), portraitScreen, emptySet())

		assertEquals("sizeless", result.getOrThrow().identity.id)
	}

	@Test
	fun `blocked wallpapers and records without downloadable URLs are skipped`() = runTest {
		val unlinked = PexafyPhotoDto(photoId = "unlinked", imageUrl = null, urls = null)
		everySearch() returns makePage(unlinked, makePhoto("blocked-id"), makePhoto("usable-id"))

		val result = provider.next(settingsWithKey(), portraitScreen, setOf("blocked-id"))

		assertEquals("usable-id", result.getOrThrow().identity.id)
	}

	@Test
	fun `follows cursor to next page when cache empties within 5-minute TTL`() = runTest {
		coEvery {
			pexafyApiService.search(any(), any(), any(), any(), any(), any(), null)
		} returns makePage(makePhoto("page1-photo"), hasMore = true, nextCursor = "cursor-page-2")

		coEvery {
			pexafyApiService.search(any(), any(), any(), any(), any(), any(), "cursor-page-2")
		} returns makePage(makePhoto("page2-photo"), hasMore = false, nextCursor = null)

		val first = provider.next(settingsWithKey(), portraitScreen, emptySet()).getOrThrow()
		currentTimeMs += 60_000L
		val second = provider.next(settingsWithKey(), portraitScreen, emptySet()).getOrThrow()

		assertEquals("page1-photo", first.identity.id)
		assertEquals("page2-photo", second.identity.id)
		coVerify(exactly = 1) { pexafyApiService.search(any(), any(), any(), any(), any(), any(), null) }
		coVerify(exactly = 1) { pexafyApiService.search(any(), any(), any(), any(), any(), any(), "cursor-page-2") }
	}

	@Test
	fun `expired cursor after 5 minutes restarts without cursor and advances past already-served first-page photos`() = runTest {
		coEvery {
			pexafyApiService.search(any(), any(), any(), any(), any(), any(), null)
		} returnsMany listOf(
			makePage(makePhoto("page1-photo"), hasMore = true, nextCursor = "stale-cursor"),
			makePage(makePhoto("page1-photo"), hasMore = true, nextCursor = "fresh-cursor")
		)

		coEvery {
			pexafyApiService.search(any(), any(), any(), any(), any(), any(), "fresh-cursor")
		} returns makePage(makePhoto("page2-photo"), hasMore = false, nextCursor = null)

		val first = provider.next(settingsWithKey(), portraitScreen, emptySet()).getOrThrow()
		assertEquals("page1-photo", first.identity.id)

		// Advance past 5-minute cursor TTL
		currentTimeMs += 6 * 60 * 1_000L

		val second = provider.next(settingsWithKey(), portraitScreen, emptySet()).getOrThrow()
		assertEquals("page2-photo", second.identity.id)

		coVerify(exactly = 0) { pexafyApiService.search(any(), any(), any(), any(), any(), any(), "stale-cursor") }
		coVerify(exactly = 2) { pexafyApiService.search(any(), any(), any(), any(), any(), any(), null) }
		coVerify(exactly = 1) { pexafyApiService.search(any(), any(), any(), any(), any(), any(), "fresh-cursor") }
	}

	@Test
	fun `server-rejected cursor falls back to first page without failing the rotation`() = runTest {
		coEvery {
			pexafyApiService.search(any(), any(), any(), any(), any(), any(), null)
		} returnsMany listOf(
			makePage(makePhoto("p1"), hasMore = true, nextCursor = "server-expired-cursor"),
			makePage(makePhoto("p2"), hasMore = false, nextCursor = null)
		)

		coEvery {
			pexafyApiService.search(any(), any(), any(), any(), any(), any(), "server-expired-cursor")
		} throws httpError(400, "INVALID_PARAMS")

		assertEquals("p1", provider.next(settingsWithKey(), portraitScreen, emptySet()).getOrThrow().identity.id)
		assertEquals("p2", provider.next(settingsWithKey(), portraitScreen, emptySet()).getOrThrow().identity.id)
	}

	@Test
	fun `cached search results expire after 24 hours`() = runTest {
		everySearch() returnsMany listOf(
			makePage(makePhoto("day1-a"), makePhoto("day1-b")),
			makePage(makePhoto("day2-a"))
		)

		provider.next(settingsWithKey(), portraitScreen, emptySet()).getOrThrow()
		// Advance 24 hours + 1 second so the remaining cached item from day 1 must be discarded
		currentTimeMs += 24 * 60 * 60 * 1_000L + 1_000L

		val nextDay = provider.next(settingsWithKey(), portraitScreen, emptySet()).getOrThrow()
		assertEquals("day2-a", nextDay.identity.id)
		coVerify(exactly = 2) { pexafyApiService.search(any(), any(), any(), any(), any(), any(), any()) }
	}

	@Test
	fun `401 UNAUTHORIZED maps to InvalidApiKeyException`() = runTest {
		everySearch() throws httpError(401, "UNAUTHORIZED")

		val result = provider.next(settingsWithKey(), portraitScreen, emptySet())

		assertTrue(result.exceptionOrNull() is InvalidApiKeyException)
	}

	@Test
	fun `429 QUOTA_EXCEEDED maps to QuotaExceededException`() = runTest {
		everySearch() throws httpError(429, "QUOTA_EXCEEDED")

		val result = provider.next(settingsWithKey(), portraitScreen, emptySet())

		assertTrue(result.exceptionOrNull() is QuotaExceededException)
	}

	@Test
	fun `429 RATE_LIMITED maps to RateLimitedException with Retry-After and cools down until window elapses`() = runTest {
		everySearch() throws httpError(429, "RATE_LIMITED", retryAfter = "30") andThen makePage(makePhoto("after-cooldown"))

		val first = provider.next(settingsWithKey(), portraitScreen, emptySet())
		val error = first.exceptionOrNull() as RateLimitedException
		assertEquals(30L, error.retryAfterSeconds)

		// Calling again 10s later should short-circuit without hitting the API
		currentTimeMs += 10_000L
		val duringCooldown = provider.next(settingsWithKey(), portraitScreen, emptySet())
		val cooldownError = duringCooldown.exceptionOrNull() as RateLimitedException
		assertEquals(20L, cooldownError.retryAfterSeconds)
		coVerify(exactly = 1) { pexafyApiService.search(any(), any(), any(), any(), any(), any(), any()) }

		// After the 30s Retry-After window passes, the provider calls the API again
		currentTimeMs += 25_000L
		val recovered = provider.next(settingsWithKey(), portraitScreen, emptySet())
		assertEquals("after-cooldown", recovered.getOrThrow().identity.id)
		coVerify(exactly = 2) { pexafyApiService.search(any(), any(), any(), any(), any(), any(), any()) }
	}

	@Test
	fun `empty search results return NoResultsException without looping`() = runTest {
		everySearch() returns makePage()

		val result = provider.next(settingsWithKey(), portraitScreen, emptySet())

		assertTrue(result.exceptionOrNull() is NoResultsException)
		coVerify(exactly = 1) { pexafyApiService.search(any(), any(), any(), any(), any(), any(), any()) }
	}

	@Test
	fun `network failure surfaces IOException`() = runTest {
		everySearch() throws IOException("unreachable")

		val result = provider.next(settingsWithKey(), portraitScreen, emptySet())

		assertTrue(result.exceptionOrNull() is IOException)
	}

	@Test
	fun `recycles exhausted single-photo final page on next rotation without waiting for cursor TTL`() = runTest {
		coEvery {
			pexafyApiService.search(any(), any(), any(), any(), any(), any(), any())
		} returns makePage(makePhoto("single-photo"), hasMore = false, nextCursor = null)

		val first = provider.next(settingsWithKey(), portraitScreen, emptySet()).getOrThrow()
		assertEquals("single-photo", first.identity.id)

		// 1 minute later, cache was emptied by first call, but window was terminal (has_more=false).
		currentTimeMs += 60_000L
		val second = provider.next(settingsWithKey(), portraitScreen, emptySet()).getOrThrow()
		assertEquals("single-photo", second.identity.id)

		// Did not need to wait 5 minutes, and did not throw NoResultsException
		coVerify(exactly = 1) { pexafyApiService.search(any(), any(), any(), any(), any(), any(), any()) }
	}

	@Test
	fun `large keyword and color fan-out is strictly bounded per search`() = runTest {
		everySearch() returns makePage(makePhoto("p1"))

		provider.next(
			settingsWithKey(
				searchQuery = "w1, w2, w3, w4, w5",
				filterColor = "111111, 222222, 333333, 444444, 555555"
			),
			portraitScreen,
			emptySet()
		)

		coVerify(atMost = 5) { pexafyApiService.search(any(), any(), any(), any(), any(), any(), any()) }
	}

	@Test
	fun `rejected cursor followed by rate limit on reset retry maps to RateLimitedException with Retry-After`() = runTest {
		coEvery {
			pexafyApiService.search(any(), any(), any(), any(), any(), any(), null)
		} returns makePage(makePhoto("p1"), hasMore = true, nextCursor = "bad-cursor")

		provider.next(settingsWithKey(), portraitScreen, emptySet())

		coEvery {
			pexafyApiService.search(any(), any(), any(), any(), any(), any(), "bad-cursor")
		} throws httpError(400, "INVALID_PARAMS")

		coEvery {
			pexafyApiService.search(any(), any(), any(), any(), any(), any(), null)
		} throws httpError(429, "RATE_LIMITED", retryAfter = "45")

		val result = provider.next(settingsWithKey(), portraitScreen, emptySet())
		assertTrue(result.exceptionOrNull() is RateLimitedException)
		assertEquals(45L, (result.exceptionOrNull() as RateLimitedException).retryAfterSeconds)
	}

	@Test
	fun `Retrofit client sends x-api-key header, omits key from URL, and deserializes live schema`() = runTest {
		val server = MockWebServer()
		server.enqueue(
			MockResponse()
				.setResponseCode(200)
				.setHeader("Content-Type", "application/json")
				.setBody(sampleSearchResponseJson())
		)

		try {
			server.start()
			val json = Json { ignoreUnknownKeys = true }
			val api = Retrofit.Builder()
				.baseUrl(server.url("/"))
				.client(OkHttpClient())
				.addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
				.build()
				.create(PexafyApiService::class.java)
			val realProvider = PexafyProvider(api) { currentTimeMs }

			val wallpaper = realProvider.next(
				settingsWithKey(pexafyApiKey = "pexafy_api_live_secret", searchQuery = "red fox in snow"),
				landscapeScreen,
				emptySet()
			).getOrThrow()

			val recorded = server.takeRequest()
			assertEquals("pexafy_api_live_secret", recorded.getHeader("x-api-key"))
			assertFalse(recorded.path.orEmpty().contains("pexafy_api_live_secret"))
			assertTrue(recorded.path.orEmpty().startsWith("/api/v1/search/photos?"))
			assertEquals("pexafy_019e1a0d-1a4f-7d62-856f-e6775b49785d", wallpaper.identity.qualified)
			assertEquals("https://images.pexels.com/photos/20853949/pexels-photo-20853949.jpeg", wallpaper.directUrl)
			assertEquals("https://images.pexels.com/photos/20853949/pexels-photo-20853949.jpeg?w=200", wallpaper.thumbnailUrl)
		} finally {
			server.shutdown()
		}
	}

	private fun sampleSearchResponseJson() =
		"""
		{
		  "success": true,
		  "data": [
		    {
		      "photo_id": "019e1a0d-1a4f-7d62-856f-e6775b49785d",
		      "image_url": "https://images.pexels.com/photos/20853949/pexels-photo-20853949.jpeg",
		      "urls": {
		        "thumb": "https://images.pexels.com/photos/20853949/pexels-photo-20853949.jpeg?w=200",
		        "small": "https://images.pexels.com/photos/20853949/pexels-photo-20853949.jpeg?w=400",
		        "regular": "https://images.pexels.com/photos/20853949/pexels-photo-20853949.jpeg?w=1280",
		        "large": "https://images.pexels.com/photos/20853949/pexels-photo-20853949.jpeg?w=1920",
		        "full": "https://images.pexels.com/photos/20853949/pexels-photo-20853949.jpeg?w=2400"
		      },
		      "width": 8234,
		      "height": 5489,
		      "orientation": "landscape",
		      "source": "Pexels",
		      "license_type": "free",
		      "source_image_url": "https://www.pexels.com/photo/red-fox-walking-in-snow-20853949",
		      "photographer_username": "dip-kheto",
		      "photographer_full_name": "Dip Kheto",
		      "photographer_url": "https://www.pexels.com/search/users/Dip%20Kheto/",
		      "attribution": {
		        "html": "<span>Photo by Dip Kheto on Pexels</span>",
		        "plain": "Photo by Dip Kheto on Pexels"
		      }
		    }
		  ],
		  "meta": {
		    "request_id": "ad7140d9-2cc2-4134-8bae-6b90ba3dede2",
		    "took_ms": 86.42
		  },
		  "pagination": {
		    "next_cursor": "eyJycyI6IjBlYWNjM2Q5MjI0NTRjMmFiYTc4NjBhMGIwZjJkZDlkIiwicCI6MSwicHAiOjJ9",
		    "per_page": 20,
		    "has_more": true
		  },
		  "error": null
		}
		""".trimIndent()
}
