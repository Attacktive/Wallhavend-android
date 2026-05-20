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
	private val api = mockk<WallhavenApiService>()
	private val fileManager = mockk<WallpaperFileManager>()
	private lateinit var repo: WallhavenRepository

	private fun makeDto(id: String) = WallpaperDto(id, "https://wallhaven.cc/$id", "https://cdn/w/$id.jpg", "1920x1080", "image/jpeg")
	private fun makePage(count: Int) = SearchResponseDto(
		data = (1..count).map { makeDto("w$it") },
		meta = MetaDto(1, 1, 24, count)
	)

	private fun makeFile(id: String) = File("/tmp/$id.jpg")

	@Before
	fun setUp() {
		repo = WallhavenRepository(api, fileManager)
	}

	@Test
	fun `next fetches from API and returns first result`() = runTest {
		coEvery { api.search(any(), any(), any(), any(), any(), any(), any()) } returns makePage(5)
		coEvery { fileManager.download(any()) } answers {
			Result.success(makeFile(firstArg<Wallpaper>().id))
		}

		val result = repo.next(AppSettings())
		assertTrue(result.isSuccess)
		assertEquals("w1", result.getOrNull()?.first?.id)

		coVerify(exactly = 1) { api.search(any(), any(), any(), any(), any(), any(), any()) }
	}

	@Test
	fun `next reuses cache on second call`() = runTest {
		coEvery { api.search(any(), any(), any(), any(), any(), any(), any()) } returns makePage(5)
		coEvery { fileManager.download(any()) } answers {
			Result.success(makeFile(firstArg<Wallpaper>().id))
		}

		repo.next(AppSettings())
		repo.next(AppSettings())

		coVerify(exactly = 1) { api.search(any(), any(), any(), any(), any(), any(), any()) }
	}

	@Test
	fun `cache invalidates when query changes`() = runTest {
		coEvery { api.search(any(), any(), any(), any(), any(), any(), any()) } returns makePage(3)
		coEvery { fileManager.download(any()) } answers {
			Result.success(makeFile(firstArg<Wallpaper>().id))
		}

		repo.next(AppSettings(searchQuery = "mountains"))
		repo.next(AppSettings(searchQuery = "ocean"))

		coVerify(exactly = 2) { api.search(any(), any(), any(), any(), any(), any(), any()) }
	}

	@Test
	fun `returns NoResultsException when API returns empty list`() = runTest {
		coEvery { api.search(any(), any(), any(), any(), any(), any(), any()) } returns makePage(0)

		val result = repo.next(AppSettings())
		assertTrue(result.isFailure)
		assertTrue(result.exceptionOrNull() is NoResultsException)
	}
}
