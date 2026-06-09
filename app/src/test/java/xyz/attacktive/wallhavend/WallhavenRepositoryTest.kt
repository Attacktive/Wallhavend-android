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
import xyz.attacktive.wallhavend.domain.model.Wallpaper
import xyz.attacktive.wallhavend.domain.model.query.Sorting
import xyz.attacktive.wallhavend.domain.model.query.ToplistRange
import xyz.attacktive.wallhavend.domain.repository.WallhavenRepository
import xyz.attacktive.wallhavend.domain.service.WallpaperFileManager

class WallhavenRepositoryTest {
	private val wallhavenApiService = mockk<WallhavenApiService>()
	private val fileManager = mockk<WallpaperFileManager>()
	private lateinit var repository: WallhavenRepository

	private fun makeDto(id: String) = WallpaperDto(id, "https://wallhaven.cc/$id", "https://cdn/w/$id.jpg", "1920x1080", "image/jpeg")

	private fun makePage(count: Int, currentPage: Int = 1, lastPage: Int = 1) = SearchResponseDto(
		data = (1..count).map { makeDto(if (currentPage == 1 && lastPage == 1) "w$it" else "w-${currentPage}-${it}") },
		meta = MetaDto(currentPage, lastPage, 24, count)
	)

	private fun makeFile(id: String) = File("/tmp/$id.jpg")

	@Before
	fun setUp() {
		repository = WallhavenRepository(wallhavenApiService, fileManager)
	}

	@Test
	fun `next fetches from API and returns first result`() = runTest {
		coEvery { wallhavenApiService.search(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns makePage(5)
		coEvery { fileManager.download(any()) } answers {
			Result.success(makeFile(firstArg<Wallpaper>().id))
		}

		val result = repository.next(AppSettings(), "9x16")
		assertTrue(result.isSuccess)
		assertEquals("w1", result.getOrNull()?.first?.id)

		coVerify(exactly = 1) { wallhavenApiService.search(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }
	}

	@Test
	fun `next reuses cache on second call`() = runTest {
		coEvery { wallhavenApiService.search(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns makePage(5)
		coEvery { fileManager.download(any()) } answers {
			Result.success(makeFile(firstArg<Wallpaper>().id))
		}

		repository.next(AppSettings(), "9x16")
		repository.next(AppSettings(), "9x16")

		coVerify(exactly = 1) { wallhavenApiService.search(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }
	}

	@Test
	fun `cache invalidates when query changes`() = runTest {
		coEvery { wallhavenApiService.search(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns makePage(3)
		coEvery { fileManager.download(any()) } answers {
			Result.success(makeFile(firstArg<Wallpaper>().id))
		}

		repository.next(AppSettings(searchQuery = "mountains"), "9x16")
		repository.next(AppSettings(searchQuery = "ocean"), "9x16")

		coVerify(exactly = 2) { wallhavenApiService.search(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }
	}

	@Test
	fun `cache invalidates when filterColor changes`() = runTest {
		coEvery { wallhavenApiService.search(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns makePage(3)
		coEvery { fileManager.download(any()) } answers {
			Result.success(makeFile(firstArg<Wallpaper>().id))
		}

		repository.next(AppSettings(filterColor = "cc0000"), "9x16")
		repository.next(AppSettings(filterColor = "0066cc"), "9x16")

		coVerify(exactly = 2) { wallhavenApiService.search(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }
	}

	@Test
	fun `cache invalidates when sorting changes`() = runTest {
		coEvery { wallhavenApiService.search(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns makePage(3)
		coEvery { fileManager.download(any()) } answers {
			Result.success(makeFile(firstArg<Wallpaper>().id))
		}

		repository.next(AppSettings(sorting = Sorting.RANDOM), "9x16")
		repository.next(AppSettings(sorting = Sorting.VIEWS), "9x16")

		coVerify(exactly = 2) { wallhavenApiService.search(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }
	}

	@Test
	fun `cache invalidates when toplistRange changes`() = runTest {
		coEvery { wallhavenApiService.search(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns makePage(3)
		coEvery { fileManager.download(any()) } answers {
			Result.success(makeFile(firstArg<Wallpaper>().id))
		}

		repository.next(AppSettings(sorting = Sorting.TOPLIST, toplistRange = ToplistRange.ONE_MONTH), "9x16")
		repository.next(AppSettings(sorting = Sorting.TOPLIST, toplistRange = ToplistRange.ONE_YEAR), "9x16")

		coVerify(exactly = 2) { wallhavenApiService.search(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }
	}

	@Test
	fun `cache invalidates when aspect ratio changes`() = runTest {
		coEvery { wallhavenApiService.search(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns makePage(3)
		coEvery { fileManager.download(any()) } answers {
			Result.success(makeFile(firstArg<Wallpaper>().id))
		}

		repository.next(AppSettings(), "9x16")
		repository.next(AppSettings(), "16x9")

		coVerify(exactly = 2) { wallhavenApiService.search(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }
	}

	@Test
	fun `returns NoResultsException when API returns empty list`() = runTest {
		coEvery { wallhavenApiService.search(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns makePage(0)

		val result = repository.next(AppSettings(), "9x16")
		assertTrue(result.isFailure)
		assertTrue(result.exceptionOrNull() is NoResultsException)
	}

	@Test
	fun `pagination walks through pages and wraps back to page 1`() = runTest {
		// Page 1: lastPage = 2, returns 1 item
		coEvery {
			wallhavenApiService.search(
				query = any(), categories = any(), purity = any(), ratios = any(),
				sorting = any(), seed = any(), topRange = any(), page = 1, colors = any(), apiKey = any()
			)
		} returns makePage(count = 1, currentPage = 1, lastPage = 2)

		// Page 2: lastPage = 2, returns 1 item
		coEvery {
			wallhavenApiService.search(
				query = any(), categories = any(), purity = any(), ratios = any(),
				sorting = any(), seed = any(), topRange = any(), page = 2, colors = any(), apiKey = any()
			)
		} returns makePage(count = 1, currentPage = 2, lastPage = 2)

		coEvery { fileManager.download(any()) } answers {
			Result.success(makeFile(firstArg<Wallpaper>().id))
		}

		// First call - should request page 1
		val res1 = repository.next(AppSettings(sorting = Sorting.VIEWS), "9x16")
		assertEquals("w-1-1", res1.getOrNull()?.first?.id)

		// Second call - cache is empty, should request page 2
		val res2 = repository.next(AppSettings(sorting = Sorting.VIEWS), "9x16")
		assertEquals("w-2-1", res2.getOrNull()?.first?.id)

		// Third call - cache is empty, currentPage (3) > lastPage (2), should wrap back and request page 1
		val res3 = repository.next(AppSettings(sorting = Sorting.VIEWS), "9x16")
		assertEquals("w-1-1", res3.getOrNull()?.first?.id)

		// Verify page 1 was requested twice, page 2 was requested once
		coVerify(exactly = 2) {
			wallhavenApiService.search(
				query = any(), categories = any(), purity = any(), ratios = any(),
				sorting = any(), seed = any(), topRange = any(), page = 1, colors = any(), apiKey = any()
			)
		}

		coVerify(exactly = 1) {
			wallhavenApiService.search(
				query = any(), categories = any(), purity = any(), ratios = any(),
				sorting = any(), seed = any(), topRange = any(), page = 2, colors = any(), apiKey = any()
			)
		}
	}
}
