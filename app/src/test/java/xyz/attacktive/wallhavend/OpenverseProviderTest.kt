package xyz.attacktive.wallhavend

import java.io.IOException
import kotlinx.coroutines.test.runTest
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import xyz.attacktive.wallhavend.data.api.OpenverseApiService
import xyz.attacktive.wallhavend.data.api.dto.OpenverseImageDto
import xyz.attacktive.wallhavend.data.api.dto.OpenverseSearchResponseDto
import xyz.attacktive.wallhavend.domain.model.AppSettings
import xyz.attacktive.wallhavend.domain.model.NoResultsException
import xyz.attacktive.wallhavend.domain.model.ScreenInfo
import xyz.attacktive.wallhavend.domain.model.WallpaperSource
import xyz.attacktive.wallhavend.domain.model.query.LicenseFilter
import xyz.attacktive.wallhavend.domain.model.query.Purity
import xyz.attacktive.wallhavend.domain.repository.OpenverseProvider

class OpenverseProviderTest {
	private val openverseApiService = mockk<OpenverseApiService>()
	private lateinit var provider: OpenverseProvider

	private val portraitScreen = ScreenInfo("9x16", 1080, 2400)
	private val landscapeScreen = ScreenInfo("16x9", 2400, 1080)
	private val squareScreen = ScreenInfo("1x1", 1440, 1440)

	/** Comfortably larger than any screen here, so a result only ever gets dropped when a test means it to. */
	private fun makeImage(id: String, url: String? = "https://cdn/$id.jpg", width: Int? = 4000, height: Int? = 4000) = OpenverseImageDto(
		id = id,
		url = url,
		width = width,
		height = height
	)

	private fun makePage(vararg images: OpenverseImageDto, pageCount: Int = 1, page: Int = 1) = OpenverseSearchResponseDto(pageCount = pageCount, page = page, results = images.toList())

	private fun everySearch() = coEvery {
		openverseApiService.search(any(), any(), any(), any(), any(), any(), any(), any(), any())
	}

	@Before
	fun setUp() {
		provider = OpenverseProvider(openverseApiService)
	}

	@Test
	fun `next returns a result qualified as an Openverse wallpaper`() = runTest {
		everySearch() returns makePage(makeImage("abc-123"))

		val result = provider.next(AppSettings(), portraitScreen, emptySet())

		assertTrue(result.isSuccess)
		assertEquals(WallpaperSource.OPENVERSE, result.getOrNull()?.identity?.source)
		assertEquals("abc-123", result.getOrNull()?.identity?.id)
		assertEquals("https://cdn/abc-123.jpg", result.getOrNull()?.directUrl)
	}

	@Test
	fun `an empty result set gives up without another page`() = runTest {
		everySearch() returns makePage()

		val result = provider.next(AppSettings(), portraitScreen, emptySet())

		assertTrue(result.exceptionOrNull() is NoResultsException)
		coVerify(exactly = 1) { openverseApiService.search(any(), any(), any(), any(), any(), any(), any(), any(), any()) }
	}

	@Test
	fun `a failing search surfaces its own error`() = runTest {
		everySearch() throws IOException("search is down")

		val result = provider.next(AppSettings(), portraitScreen, emptySet())

		assertTrue(result.exceptionOrNull() is IOException)
	}

	@Test
	fun `a blocked wallpaper is skipped`() = runTest {
		everySearch() returns makePage(makeImage("blocked"), makeImage("free"))

		val result = provider.next(AppSettings(), portraitScreen, setOf("blocked"))

		assertEquals("free", result.getOrNull()?.identity?.id)
	}

	@Test
	fun `a second wallpaper comes out of the cache rather than another search`() = runTest {
		everySearch() returns makePage(makeImage("w1"), makeImage("w2"))

		provider.next(AppSettings(), portraitScreen, emptySet())
		provider.next(AppSettings(), portraitScreen, emptySet())

		coVerify(exactly = 1) { openverseApiService.search(any(), any(), any(), any(), any(), any(), any(), any(), any()) }
	}

	@Test
	fun `changing a setting throws the cache away`() = runTest {
		everySearch() returns makePage(makeImage("w1"), makeImage("w2"))

		provider.next(AppSettings(), portraitScreen, emptySet())
		provider.next(AppSettings(licenseFilter = LicenseFilter.PERMISSIVE), portraitScreen, emptySet())

		coVerify(exactly = 2) { openverseApiService.search(any(), any(), any(), any(), any(), any(), any(), any(), any()) }
	}

	@Test
	fun `each keyword gets its own search`() = runTest {
		everySearch() returns makePage(makeImage("w1"))

		provider.next(AppSettings(searchQuery = "mountains, rivers"), portraitScreen, emptySet())

		coVerify(exactly = 1) { openverseApiService.search("mountains", any(), any(), any(), any(), any(), any(), any(), any()) }
		coVerify(exactly = 1) { openverseApiService.search("rivers", any(), any(), any(), any(), any(), any(), any(), any()) }
	}

	@Test
	fun `an empty query searches once with no keyword`() = runTest {
		everySearch() returns makePage(makeImage("w1"))

		provider.next(AppSettings(), portraitScreen, emptySet())

		coVerify(exactly = 1) { openverseApiService.search(null, any(), any(), any(), any(), any(), any(), any(), any()) }
	}

	@Test
	fun `the licence tier reaches the query`() = runTest {
		everySearch() returns makePage(makeImage("w1"))

		provider.next(AppSettings(licenseFilter = LicenseFilter.ANY_COMMERCIAL), portraitScreen, emptySet())

		coVerify { openverseApiService.search(any(), "cc0,pdm,by,by-sa,by-nd", any(), any(), any(), any(), any(), any(), any()) }
	}

	@Test
	fun `the licence defaults to the tier that carries no obligations`() = runTest {
		everySearch() returns makePage(makeImage("w1"))

		provider.next(AppSettings(), portraitScreen, emptySet())

		coVerify { openverseApiService.search(any(), "cc0,pdm", any(), any(), any(), any(), any(), any(), any()) }
	}

	@Test
	fun `the screen's shape picks one of the three ratios Openverse knows`() = runTest {
		everySearch() returns makePage(makeImage("w1"))

		provider.next(AppSettings(), portraitScreen, emptySet())
		provider.next(AppSettings(), landscapeScreen, emptySet())
		provider.next(AppSettings(), squareScreen, emptySet())

		coVerify(exactly = 1) { openverseApiService.search(any(), any(), any(), any(), "tall", any(), any(), any(), any()) }
		coVerify(exactly = 1) { openverseApiService.search(any(), any(), any(), any(), "wide", any(), any(), any(), any()) }
		coVerify(exactly = 1) { openverseApiService.search(any(), any(), any(), any(), "square", any(), any(), any(), any()) }
	}

	@Test
	fun `only NSFW asks Openverse to widen to mature results`() = runTest {
		everySearch() returns makePage(makeImage("w1"))

		provider.next(AppSettings(purity = setOf(Purity.SFW, Purity.SKETCHY)), portraitScreen, emptySet())
		provider.next(AppSettings(purity = setOf(Purity.SFW, Purity.NSFW)), portraitScreen, emptySet())

		coVerify(exactly = 1) { openverseApiService.search(any(), any(), any(), any(), any(), any(), false, any(), any()) }
		coVerify(exactly = 1) { openverseApiService.search(any(), any(), any(), any(), any(), any(), true, any(), any()) }
	}

	@Test
	fun `only JPEG and PNG are asked for, and only from the curated sources`() = runTest {
		everySearch() returns makePage(makeImage("w1"))

		provider.next(AppSettings(), portraitScreen, emptySet())

		coVerify { openverseApiService.search(any(), any(), "flickr,wikimedia,nasa,spacex,rawpixel,stocksnap", "jpg,png", any(), any(), any(), any(), any()) }
	}

	@Test
	fun `avoiding blurry wallpapers asks for large ones and drops the ones that are too small anyway`() = runTest {
		everySearch() returns makePage(makeImage("small", width = 800, height = 600), makeImage("big"))

		val result = provider.next(AppSettings(avoidBlurryWallpapers = true), portraitScreen, emptySet())

		assertEquals("big", result.getOrNull()?.identity?.id)
		coVerify { openverseApiService.search(any(), any(), any(), any(), any(), "large", any(), any(), any()) }
	}

	@Test
	fun `a result that never reported its size is dropped only when the size matters`() = runTest {
		val sizeless = makeImage("sizeless", width = null, height = null)
		everySearch() returns makePage(sizeless, makeImage("sized"))

		assertEquals("sized", provider.next(AppSettings(avoidBlurryWallpapers = true), portraitScreen, emptySet()).getOrNull()?.identity?.id)

		everySearch() returns makePage(sizeless)

		assertTrue(provider.next(AppSettings(), portraitScreen, emptySet()).isSuccess)
	}

	@Test
	fun `a result without a direct url is skipped for one that has one`() = runTest {
		everySearch() returns makePage(makeImage("url-less", url = null), makeImage("linked"))

		val result = provider.next(AppSettings(), portraitScreen, emptySet())

		assertEquals("linked", result.getOrNull()?.identity?.id)
	}

	@Test
	fun `a page of only url-less results gives up after the bounded refetch`() = runTest {
		everySearch() returns makePage(makeImage("url-less", url = null))

		val result = provider.next(AppSettings(), portraitScreen, emptySet())

		assertTrue(result.exceptionOrNull() is NoResultsException)
		coVerify(exactly = 5) { openverseApiService.search(any(), any(), any(), any(), any(), any(), any(), any(), any()) }
	}

	@Test
	fun `a page that filters down to nothing earns another page rather than a no-results`() = runTest {
		val tooSmall = makePage(makeImage("small", width = 800, height = 600))
		everySearch() returnsMany listOf(tooSmall, makePage(makeImage("big")))

		val result = provider.next(AppSettings(avoidBlurryWallpapers = true), portraitScreen, emptySet())

		assertEquals("big", result.getOrNull()?.identity?.id)
		coVerify(exactly = 2) { openverseApiService.search(any(), any(), any(), any(), any(), any(), any(), any(), any()) }
	}

	@Test
	fun `a search that never yields anything usable gives up instead of paging forever`() = runTest {
		everySearch() returns makePage(makeImage("small", width = 800, height = 600))

		val result = provider.next(AppSettings(avoidBlurryWallpapers = true), portraitScreen, emptySet())

		assertTrue(result.exceptionOrNull() is NoResultsException)
		coVerify(exactly = 5) { openverseApiService.search(any(), any(), any(), any(), any(), any(), any(), any(), any()) }
	}

	@Test
	fun `the first page is page one and every later one stays inside the anonymous depth cap`() = runTest {
		val requestedPages = mutableListOf<Int>()
		coEvery {
			openverseApiService.search(any(), any(), any(), any(), any(), any(), any(), capture(requestedPages), any())
		} returns makePage(makeImage("w1"), pageCount = 500)

		repeat(30) { provider.next(AppSettings(), portraitScreen, emptySet()) }

		assertEquals(30, requestedPages.size)
		assertEquals(1, requestedPages.first())
		assertTrue(requestedPages.all { it in 1..12 })
		assertFalse("a random page should not keep landing on the first one", requestedPages.all { it == 1 })
	}

	@Test
	fun `a page count below the depth cap is what bounds the paging`() = runTest {
		val requestedPages = mutableListOf<Int>()
		coEvery {
			openverseApiService.search(any(), any(), any(), any(), any(), any(), any(), capture(requestedPages), any())
		} returns makePage(makeImage("w1"), pageCount = 3)

		repeat(20) { provider.next(AppSettings(), portraitScreen, emptySet()) }

		assertTrue(requestedPages.all { it in 1..3 })
	}

	@Test
	fun `the narrowest keyword decides how deep the shared page number may go`() = runTest {
		val requestedPages = mutableListOf<Int>()
		coEvery {
			openverseApiService.search("wide", any(), any(), any(), any(), any(), any(), any(), any())
		} returns makePage(makeImage("w1"), pageCount = 500)

		coEvery {
			openverseApiService.search("narrow", any(), any(), any(), any(), any(), any(), capture(requestedPages), any())
		} returns makePage(makeImage("n1"), pageCount = 2)

		repeat(20) { provider.next(AppSettings(searchQuery = "wide, narrow"), portraitScreen, emptySet()) }

		assertTrue(requestedPages.all { it in 1..2 })
	}
}
