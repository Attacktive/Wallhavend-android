package xyz.attacktive.wallhavend

import kotlinx.coroutines.test.runTest
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import xyz.attacktive.wallhavend.data.api.WallhavenApiService
import xyz.attacktive.wallhavend.data.api.dto.MetaDto
import xyz.attacktive.wallhavend.data.api.dto.SearchResponseDto
import xyz.attacktive.wallhavend.data.api.dto.WallpaperDto
import xyz.attacktive.wallhavend.domain.model.AppSettings
import xyz.attacktive.wallhavend.domain.model.NoResultsException
import xyz.attacktive.wallhavend.domain.model.ScreenInfo
import xyz.attacktive.wallhavend.domain.model.WallpaperSource
import xyz.attacktive.wallhavend.domain.model.query.Sorting
import xyz.attacktive.wallhavend.domain.model.query.ToplistRange
import xyz.attacktive.wallhavend.domain.repository.WallhavenProvider

class WallhavenProviderTest {
	private val wallhavenApiService = mockk<WallhavenApiService>()
	private lateinit var provider: WallhavenProvider

	private val portraitScreen = ScreenInfo("9x16", 1080, 2400)
	private val landscapeScreen = ScreenInfo("16x9", 2400, 1080)

	private fun makeDto(id: String) = WallpaperDto(id, "https://wallhaven.cc/$id", "https://cdn/w/$id.jpg", "1920x1080")

	private fun makePage(count: Int, currentPage: Int = 1, lastPage: Int = 1, idPrefix: String = "w"): SearchResponseDto {
		val data = (1..count)
			.map {
				val id = if (currentPage == 1 && lastPage == 1) {
					"$idPrefix$it"
				} else {
					"$idPrefix-${currentPage}-${it}"
				}

				makeDto(id)
			}

		return SearchResponseDto(data, MetaDto(currentPage, lastPage, 24, count))
	}

	@Before
	fun setUp() {
		provider = WallhavenProvider(wallhavenApiService)
	}

	@Test
	fun `next fetches from API and returns first result`() = runTest {
		coEvery { wallhavenApiService.search(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns makePage(5)

		val result = provider.next(AppSettings(), portraitScreen, emptySet())
		assertTrue(result.isSuccess)
		assertEquals("w1", result.getOrNull()?.identity?.id)

		coVerify(exactly = 1) { wallhavenApiService.search(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }
	}

	@Test
	fun `next qualifies the returned wallpaper with its source`() = runTest {
		coEvery { wallhavenApiService.search(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns makePage(1)

		val identity = provider.next(AppSettings(), portraitScreen, emptySet()).getOrNull()?.identity

		assertEquals(WallpaperSource.WALLHAVEN, identity?.source)
		assertEquals("wallhaven_w1", identity?.qualified)
	}

	@Test
	fun `next reuses cache on second call`() = runTest {
		coEvery { wallhavenApiService.search(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns makePage(5)

		provider.next(AppSettings(), portraitScreen, emptySet())
		provider.next(AppSettings(), portraitScreen, emptySet())

		coVerify(exactly = 1) { wallhavenApiService.search(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }
	}

	@Test
	fun `cache invalidates when query changes`() = runTest {
		coEvery { wallhavenApiService.search(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns makePage(3)

		provider.next(AppSettings(searchQuery = "mountains"), portraitScreen, emptySet())
		provider.next(AppSettings(searchQuery = "ocean"), portraitScreen, emptySet())

		coVerify(exactly = 2) { wallhavenApiService.search(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }
	}

	@Test
	fun `cache invalidates when filterColor changes`() = runTest {
		coEvery { wallhavenApiService.search(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns makePage(3)

		provider.next(AppSettings(filterColor = "cc0000"), portraitScreen, emptySet())
		provider.next(AppSettings(filterColor = "0066cc"), portraitScreen, emptySet())

		coVerify(exactly = 2) { wallhavenApiService.search(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }
	}

	@Test
	fun `cache invalidates when sorting changes`() = runTest {
		coEvery { wallhavenApiService.search(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns makePage(3)

		provider.next(AppSettings(sorting = Sorting.RANDOM), portraitScreen, emptySet())
		provider.next(AppSettings(sorting = Sorting.VIEWS), portraitScreen, emptySet())

		coVerify(exactly = 2) { wallhavenApiService.search(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }
	}

	@Test
	fun `cache invalidates when toplistRange changes`() = runTest {
		coEvery { wallhavenApiService.search(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns makePage(3)

		provider.next(AppSettings(sorting = Sorting.TOPLIST, toplistRange = ToplistRange.ONE_MONTH), portraitScreen, emptySet())
		provider.next(AppSettings(sorting = Sorting.TOPLIST, toplistRange = ToplistRange.ONE_YEAR), portraitScreen, emptySet())

		coVerify(exactly = 2) { wallhavenApiService.search(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }
	}

	@Test
	fun `cache invalidates when aspect ratio changes`() = runTest {
		coEvery { wallhavenApiService.search(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns makePage(3)

		provider.next(AppSettings(), portraitScreen, emptySet())
		provider.next(AppSettings(), landscapeScreen, emptySet())

		coVerify(exactly = 2) { wallhavenApiService.search(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }
	}

	@Test
	fun `cache invalidates when avoidBlurryWallpapers changes`() = runTest {
		coEvery { wallhavenApiService.search(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns makePage(3)

		provider.next(AppSettings(avoidBlurryWallpapers = false), portraitScreen, emptySet())
		provider.next(AppSettings(avoidBlurryWallpapers = true), portraitScreen, emptySet())

		coVerify(exactly = 2) { wallhavenApiService.search(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }
	}

	@Test
	fun `atleast is sent as screen resolution when avoidBlurryWallpapers is on`() = runTest {
		coEvery { wallhavenApiService.search(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns makePage(3)

		provider.next(AppSettings(avoidBlurryWallpapers = true), portraitScreen, emptySet())

		coVerify(exactly = 1) {
			wallhavenApiService.search(
				query = any(), categories = any(), purity = any(), ratios = any(), atleast = "1080x2400",
				sorting = any(), seed = any(), topRange = any(), page = any(), colors = any(), apiKey = any()
			)
		}
	}

	@Test
	fun `atleast is omitted when avoidBlurryWallpapers is off`() = runTest {
		coEvery { wallhavenApiService.search(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns makePage(3)

		provider.next(AppSettings(avoidBlurryWallpapers = false), portraitScreen, emptySet())

		coVerify(exactly = 1) {
			wallhavenApiService.search(
				query = any(), categories = any(), purity = any(), ratios = any(), atleast = null,
				sorting = any(), seed = any(), topRange = any(), page = any(), colors = any(), apiKey = any()
			)
		}
	}

	@Test
	fun `returns NoResultsException when API returns empty list`() = runTest {
		coEvery { wallhavenApiService.search(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns makePage(0)

		val result = provider.next(AppSettings(), portraitScreen, emptySet())
		assertTrue(result.isFailure)
		assertTrue(result.exceptionOrNull() is NoResultsException)
	}

	@Test
	fun `pagination walks through pages and wraps back to page 1`() = runTest {
		// Page 1: lastPage = 2, returns 1 item
		coEvery {
			wallhavenApiService.search(
				query = any(), categories = any(), purity = any(), ratios = any(), atleast = any(),
				sorting = any(), seed = any(), topRange = any(), page = 1, colors = any(), apiKey = any()
			)
		} returns makePage(count = 1, currentPage = 1, lastPage = 2)

		// Page 2: lastPage = 2, returns 1 item
		coEvery {
			wallhavenApiService.search(
				query = any(), categories = any(), purity = any(), ratios = any(), atleast = any(),
				sorting = any(), seed = any(), topRange = any(), page = 2, colors = any(), apiKey = any()
			)
		} returns makePage(count = 1, currentPage = 2, lastPage = 2)

		// First call - should request page 1
		val res1 = provider.next(AppSettings(sorting = Sorting.VIEWS), portraitScreen, emptySet())
		assertEquals("w-1-1", res1.getOrNull()?.identity?.id)

		// Second call - cache is empty, should request page 2
		val res2 = provider.next(AppSettings(sorting = Sorting.VIEWS), portraitScreen, emptySet())
		assertEquals("w-2-1", res2.getOrNull()?.identity?.id)

		// Third call - cache is empty, currentPage (3) > lastPage (2), should wrap back and request page 1
		val res3 = provider.next(AppSettings(sorting = Sorting.VIEWS), portraitScreen, emptySet())
		assertEquals("w-1-1", res3.getOrNull()?.identity?.id)

		// Verify page 1 was requested twice, page 2 was requested once
		coVerify(exactly = 2) {
			wallhavenApiService.search(
				query = any(), categories = any(), purity = any(), ratios = any(), atleast = any(),
				sorting = any(), seed = any(), topRange = any(), page = 1, colors = any(), apiKey = any()
			)
		}

		coVerify(exactly = 1) {
			wallhavenApiService.search(
				query = any(), categories = any(), purity = any(), ratios = any(), atleast = any(),
				sorting = any(), seed = any(), topRange = any(), page = 2, colors = any(), apiKey = any()
			)
		}
	}

	@Test
	fun `multi-keyword query fans out parallel API calls`() = runTest {
		coEvery {
			wallhavenApiService.search(
				query = "ocean", categories = any(), purity = any(), ratios = any(), atleast = any(),
				sorting = any(), seed = any(), topRange = any(), page = any(), colors = any(), apiKey = any()
			)
		} returns makePage(2, idPrefix = "ocean")

		coEvery {
			wallhavenApiService.search(
				query = "mountains", categories = any(), purity = any(), ratios = any(), atleast = any(),
				sorting = any(), seed = any(), topRange = any(), page = any(), colors = any(), apiKey = any()
			)
		} returns makePage(2, idPrefix = "mountains")

		val result = provider.next(AppSettings(searchQuery = "ocean,mountains"), portraitScreen, emptySet())
		assertTrue(result.isSuccess)

		coVerify(exactly = 1) {
			wallhavenApiService.search(
				query = "ocean", categories = any(), purity = any(), ratios = any(), atleast = any(),
				sorting = any(), seed = any(), topRange = any(), page = any(), colors = any(), apiKey = any()
			)
		}

		coVerify(exactly = 1) {
			wallhavenApiService.search(
				query = "mountains", categories = any(), purity = any(), ratios = any(), atleast = any(),
				sorting = any(), seed = any(), topRange = any(), page = any(), colors = any(), apiKey = any()
			)
		}
	}

	@Test
	fun `multi-keyword merges results into combined pool`() = runTest {
		coEvery {
			wallhavenApiService.search(
				query = "ocean", categories = any(), purity = any(), ratios = any(), atleast = any(),
				sorting = any(), seed = any(), topRange = any(), page = any(), colors = any(), apiKey = any()
			)
		} returns makePage(3, idPrefix = "ocean")

		coEvery {
			wallhavenApiService.search(
				query = "mountains", categories = any(), purity = any(), ratios = any(), atleast = any(),
				sorting = any(), seed = any(), topRange = any(), page = any(), colors = any(), apiKey = any()
			)
		} returns makePage(2, idPrefix = "mountains")

		val ids = mutableListOf<String>()
		repeat(5) {
			val result = provider.next(AppSettings(searchQuery = "ocean,mountains"), portraitScreen, emptySet())
			ids.add(result.getOrNull()!!.identity.id)
		}

		assertEquals(5, ids.size)
		assertTrue(ids.any { it.startsWith("ocean") })
		assertTrue(ids.any { it.startsWith("mountains") })
		coVerify(exactly = 2) { wallhavenApiService.search(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }
	}

	@Test
	fun `next skips blocked ids`() = runTest {
		coEvery { wallhavenApiService.search(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns makePage(5)

		val result = provider.next(AppSettings(), portraitScreen, setOf("w1", "w2"))
		assertEquals("w3", result.getOrNull()?.identity?.id)
	}

	@Test
	fun `next returns NoResultsException when every candidate is blocked`() = runTest {
		coEvery { wallhavenApiService.search(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns makePage(3)

		val result = provider.next(AppSettings(), portraitScreen, setOf("w1", "w2", "w3"))
		assertTrue(result.isFailure)
		assertTrue(result.exceptionOrNull() is NoResultsException)
	}

	@Test
	fun `next advances to the next page when a whole page is blocked`() = runTest {
		coEvery {
			wallhavenApiService.search(
				query = any(), categories = any(), purity = any(), ratios = any(), atleast = any(),
				sorting = any(), seed = any(), topRange = any(), page = 1, colors = any(), apiKey = any()
			)
		} returns makePage(count = 1, currentPage = 1, lastPage = 2)

		coEvery {
			wallhavenApiService.search(
				query = any(), categories = any(), purity = any(), ratios = any(), atleast = any(),
				sorting = any(), seed = any(), topRange = any(), page = 2, colors = any(), apiKey = any()
			)
		} returns makePage(count = 1, currentPage = 2, lastPage = 2)

		val result = provider.next(AppSettings(sorting = Sorting.VIEWS), portraitScreen, setOf("w-1-1"))
		assertEquals("w-2-1", result.getOrNull()?.identity?.id)

		coVerify(exactly = 1) {
			wallhavenApiService.search(
				query = any(), categories = any(), purity = any(), ratios = any(), atleast = any(),
				sorting = any(), seed = any(), topRange = any(), page = 2, colors = any(), apiKey = any()
			)
		}
	}

	@Test
	fun `a multi-word query stays one phrase instead of splitting on whitespace`() = runTest {
		coEvery { wallhavenApiService.search(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns makePage(1)

		provider.next(AppSettings(searchQuery = "milky way"), portraitScreen, emptySet())

		coVerify(exactly = 1) {
			wallhavenApiService.search(
				query = "milky way", categories = any(), purity = any(), ratios = any(), atleast = any(),
				sorting = any(), seed = any(), topRange = any(), page = any(), colors = any(), apiKey = any()
			)
		}
	}

	@Test
	fun `whitespace around an explicit delimiter is trimmed off each keyword`() = runTest {
		coEvery { wallhavenApiService.search(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns makePage(1)

		provider.next(AppSettings(searchQuery = "milky way, deep space"), portraitScreen, emptySet())

		coVerify(exactly = 1) {
			wallhavenApiService.search(
				query = "milky way", categories = any(), purity = any(), ratios = any(), atleast = any(),
				sorting = any(), seed = any(), topRange = any(), page = any(), colors = any(), apiKey = any()
			)
		}

		coVerify(exactly = 1) {
			wallhavenApiService.search(
				query = "deep space", categories = any(), purity = any(), ratios = any(), atleast = any(),
				sorting = any(), seed = any(), topRange = any(), page = any(), colors = any(), apiKey = any()
			)
		}
	}

	@Test
	fun `comma semicolon and pipe delimiters also split the query`() = runTest {
		coEvery { wallhavenApiService.search(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns makePage(1)

		for (delimiter in listOf(",", ";", "|")) {
			val freshProvider = WallhavenProvider(wallhavenApiService)
			freshProvider.next(AppSettings(searchQuery = "ocean${delimiter}mountains"), portraitScreen, emptySet())
		}

		coVerify(exactly = 6) { wallhavenApiService.search(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }
	}
}
