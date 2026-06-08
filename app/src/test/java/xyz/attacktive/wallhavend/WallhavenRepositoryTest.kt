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
import xyz.attacktive.wallhavend.domain.repository.WallhavenRepository
import xyz.attacktive.wallhavend.domain.service.WallpaperFileManager

class WallhavenRepositoryTest {
	private val wallhavenApiService = mockk<WallhavenApiService>()
	private val fileManager = mockk<WallpaperFileManager>()
	private lateinit var repository: WallhavenRepository

	private fun makeDto(id: String) = WallpaperDto(id, "https://wallhaven.cc/$id", "https://cdn/w/$id.jpg", "1920x1080", "image/jpeg")

	private fun makePage(count: Int) = SearchResponseDto(
		data = (1..count).map { makeDto("w$it") },
		meta = MetaDto(1, 1, 24, count)
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

		val result = repository.next(AppSettings())
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

		repository.next(AppSettings())
		repository.next(AppSettings())

		coVerify(exactly = 1) { wallhavenApiService.search(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }
	}

	@Test
	fun `cache invalidates when query changes`() = runTest {
		coEvery { wallhavenApiService.search(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns makePage(3)
		coEvery { fileManager.download(any()) } answers {
			Result.success(makeFile(firstArg<Wallpaper>().id))
		}

		repository.next(AppSettings(searchQuery = "mountains"))
		repository.next(AppSettings(searchQuery = "ocean"))

		coVerify(exactly = 2) { wallhavenApiService.search(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }
	}

	@Test
	fun `cache invalidates when filterColor changes`() = runTest {
		coEvery { wallhavenApiService.search(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns makePage(3)
		coEvery { fileManager.download(any()) } answers {
			Result.success(makeFile(firstArg<Wallpaper>().id))
		}

		repository.next(AppSettings(filterColor = "cc0000"))
		repository.next(AppSettings(filterColor = "0066cc"))

		coVerify(exactly = 2) { wallhavenApiService.search(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }
	}

	@Test
	fun `cache invalidates when sorting changes`() = runTest {
		coEvery { wallhavenApiService.search(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns makePage(3)
		coEvery { fileManager.download(any()) } answers {
			Result.success(makeFile(firstArg<Wallpaper>().id))
		}

		repository.next(AppSettings(sorting = "random"))
		repository.next(AppSettings(sorting = "views"))

		coVerify(exactly = 2) { wallhavenApiService.search(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }
	}

	@Test
	fun `cache invalidates when toplistRange changes`() = runTest {
		coEvery { wallhavenApiService.search(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns makePage(3)
		coEvery { fileManager.download(any()) } answers {
			Result.success(makeFile(firstArg<Wallpaper>().id))
		}

		repository.next(AppSettings(sorting = "toplist", toplistRange = "1M"))
		repository.next(AppSettings(sorting = "toplist", toplistRange = "1y"))

		coVerify(exactly = 2) { wallhavenApiService.search(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }
	}

	@Test
	fun `returns NoResultsException when API returns empty list`() = runTest {
		coEvery { wallhavenApiService.search(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns makePage(0)

		val result = repository.next(AppSettings())
		assertTrue(result.isFailure)
		assertTrue(result.exceptionOrNull() is NoResultsException)
	}
}
