package xyz.attacktive.wallhavend

import java.io.File
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
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
import xyz.attacktive.wallhavend.domain.model.Wallpaper
import xyz.attacktive.wallhavend.domain.model.query.Sorting
import xyz.attacktive.wallhavend.domain.model.query.ToplistRange
import xyz.attacktive.wallhavend.domain.repository.WallhavenRepository
import xyz.attacktive.wallhavend.domain.service.WallpaperFileManager

class WallhavenRepositoryTest {
	private val wallhavenApiService = mockk<WallhavenApiService>()
	private val fileManager = mockk<WallpaperFileManager>()
	private lateinit var repository: WallhavenRepository

	private val portraitScreen = ScreenInfo("9x16", 1080, 2400)
	private val landscapeScreen = ScreenInfo("16x9", 2400, 1080)

	private fun makeDto(id: String) = WallpaperDto(id, "https://wallhaven.cc/$id", "https://cdn/w/$id.jpg", "1920x1080", "image/jpeg")

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

	private fun makeFile(id: String) = File("/tmp/$id.jpg")

	@Before
	fun setUp() {
		repository = WallhavenRepository(wallhavenApiService, fileManager)
	}

	@Test
	fun `next fetches from API and returns first result`() = runTest {
		coEvery { wallhavenApiService.search(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns makePage(5)
		coEvery { fileManager.download(any()) } answers {
			Result.success(makeFile(firstArg<Wallpaper>().id))
		}

		val result = repository.next(AppSettings(), portraitScreen)
		assertTrue(result.isSuccess)
		assertEquals("w1", result.getOrNull()?.first?.id)

		coVerify(exactly = 1) { wallhavenApiService.search(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }
	}

	@Test
	fun `next reuses cache on second call`() = runTest {
		coEvery { wallhavenApiService.search(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns makePage(5)
		coEvery { fileManager.download(any()) } answers {
			Result.success(makeFile(firstArg<Wallpaper>().id))
		}

		repository.next(AppSettings(), portraitScreen)
		repository.next(AppSettings(), portraitScreen)

		coVerify(exactly = 1) { wallhavenApiService.search(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }
	}

	@Test
	fun `cache invalidates when query changes`() = runTest {
		coEvery { wallhavenApiService.search(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns makePage(3)
		coEvery { fileManager.download(any()) } answers {
			Result.success(makeFile(firstArg<Wallpaper>().id))
		}

		repository.next(AppSettings(searchQuery = "mountains"), portraitScreen)
		repository.next(AppSettings(searchQuery = "ocean"), portraitScreen)

		coVerify(exactly = 2) { wallhavenApiService.search(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }
	}

	@Test
	fun `cache invalidates when filterColor changes`() = runTest {
		coEvery { wallhavenApiService.search(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns makePage(3)
		coEvery { fileManager.download(any()) } answers {
			Result.success(makeFile(firstArg<Wallpaper>().id))
		}

		repository.next(AppSettings(filterColor = "cc0000"), portraitScreen)
		repository.next(AppSettings(filterColor = "0066cc"), portraitScreen)

		coVerify(exactly = 2) { wallhavenApiService.search(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }
	}

	@Test
	fun `cache invalidates when sorting changes`() = runTest {
		coEvery { wallhavenApiService.search(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns makePage(3)
		coEvery { fileManager.download(any()) } answers {
			Result.success(makeFile(firstArg<Wallpaper>().id))
		}

		repository.next(AppSettings(sorting = Sorting.RANDOM), portraitScreen)
		repository.next(AppSettings(sorting = Sorting.VIEWS), portraitScreen)

		coVerify(exactly = 2) { wallhavenApiService.search(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }
	}

	@Test
	fun `cache invalidates when toplistRange changes`() = runTest {
		coEvery { wallhavenApiService.search(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns makePage(3)
		coEvery { fileManager.download(any()) } answers {
			Result.success(makeFile(firstArg<Wallpaper>().id))
		}

		repository.next(AppSettings(sorting = Sorting.TOPLIST, toplistRange = ToplistRange.ONE_MONTH), portraitScreen)
		repository.next(AppSettings(sorting = Sorting.TOPLIST, toplistRange = ToplistRange.ONE_YEAR), portraitScreen)

		coVerify(exactly = 2) { wallhavenApiService.search(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }
	}

	@Test
	fun `cache invalidates when aspect ratio changes`() = runTest {
		coEvery { wallhavenApiService.search(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns makePage(3)
		coEvery { fileManager.download(any()) } answers {
			Result.success(makeFile(firstArg<Wallpaper>().id))
		}

		repository.next(AppSettings(), portraitScreen)
		repository.next(AppSettings(), landscapeScreen)

		coVerify(exactly = 2) { wallhavenApiService.search(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }
	}

	@Test
	fun `cache invalidates when avoidBlurryWallpapers changes`() = runTest {
		coEvery { wallhavenApiService.search(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns makePage(3)
		coEvery { fileManager.download(any()) } answers {
			Result.success(makeFile(firstArg<Wallpaper>().id))
		}

		repository.next(AppSettings(avoidBlurryWallpapers = false), portraitScreen)
		repository.next(AppSettings(avoidBlurryWallpapers = true), portraitScreen)

		coVerify(exactly = 2) { wallhavenApiService.search(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }
	}

	@Test
	fun `atleast is sent as screen resolution when avoidBlurryWallpapers is on`() = runTest {
		coEvery { wallhavenApiService.search(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns makePage(3)
		coEvery { fileManager.download(any()) } answers {
			Result.success(makeFile(firstArg<Wallpaper>().id))
		}

		repository.next(AppSettings(avoidBlurryWallpapers = true), portraitScreen)

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
		coEvery { fileManager.download(any()) } answers {
			Result.success(makeFile(firstArg<Wallpaper>().id))
		}

		repository.next(AppSettings(avoidBlurryWallpapers = false), portraitScreen)

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

		val result = repository.next(AppSettings(), portraitScreen)
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

		coEvery { fileManager.download(any()) } answers {
			Result.success(makeFile(firstArg<Wallpaper>().id))
		}

		// First call - should request page 1
		val res1 = repository.next(AppSettings(sorting = Sorting.VIEWS), portraitScreen)
		assertEquals("w-1-1", res1.getOrNull()?.first?.id)

		// Second call - cache is empty, should request page 2
		val res2 = repository.next(AppSettings(sorting = Sorting.VIEWS), portraitScreen)
		assertEquals("w-2-1", res2.getOrNull()?.first?.id)

		// Third call - cache is empty, currentPage (3) > lastPage (2), should wrap back and request page 1
		val res3 = repository.next(AppSettings(sorting = Sorting.VIEWS), portraitScreen)
		assertEquals("w-1-1", res3.getOrNull()?.first?.id)

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

		coEvery { fileManager.download(any()) } answers {
			Result.success(makeFile(firstArg<Wallpaper>().id))
		}

		val result = repository.next(AppSettings(searchQuery = "ocean mountains"), portraitScreen)
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

		coEvery { fileManager.download(any()) } answers {
			Result.success(makeFile(firstArg<Wallpaper>().id))
		}

		val ids = mutableListOf<String>()
		repeat(5) {
			val result = repository.next(AppSettings(searchQuery = "ocean mountains"), portraitScreen)
			ids.add(result.getOrNull()!!.first.id)
		}

		assertEquals(5, ids.size)
		assertTrue(ids.any { it.startsWith("ocean") })
		assertTrue(ids.any { it.startsWith("mountains") })
		coVerify(exactly = 2) { wallhavenApiService.search(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }
	}

	@Test
	fun `comma semicolon and pipe delimiters also split the query`() = runTest {
		coEvery { wallhavenApiService.search(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns makePage(1)
		coEvery { fileManager.download(any()) } answers {
			Result.success(makeFile(firstArg<Wallpaper>().id))
		}

		for (delimiter in listOf(",", ";", "|")) {
			val repo = WallhavenRepository(wallhavenApiService, fileManager)
			repo.next(AppSettings(searchQuery = "ocean${delimiter}mountains"), portraitScreen)
		}

		coVerify(exactly = 6) { wallhavenApiService.search(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }
	}
}
