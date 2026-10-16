package xyz.attacktive.wallhavend

import kotlinx.coroutines.test.runTest
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test
import xyz.attacktive.wallhavend.data.api.WallhavenApiService
import xyz.attacktive.wallhavend.data.api.dto.WallhavenMetaDto
import xyz.attacktive.wallhavend.data.api.dto.WallhavenSearchResponseDto
import xyz.attacktive.wallhavend.data.api.dto.WallhavenWallpaperDto
import xyz.attacktive.wallhavend.domain.model.AppSettings
import xyz.attacktive.wallhavend.domain.model.ScreenInfo
import xyz.attacktive.wallhavend.domain.model.query.Sorting
import xyz.attacktive.wallhavend.domain.repository.InMemoryWallhavenSelectionStore
import xyz.attacktive.wallhavend.domain.repository.WallhavenProvider
import xyz.attacktive.wallhavend.domain.repository.WallhavenSelectionStore

class WallhavenProviderProcessPersistenceTest {
	private val wallhavenApiService = mockk<WallhavenApiService>()
	private val portraitScreen = ScreenInfo("9x16", 1080, 2400)

	private fun makePage(count: Int, currentPage: Int = 1, lastPage: Int = 1, idPrefix: String = "w"): WallhavenSearchResponseDto {
		val data = (1..count)
			.map {
				val id = if (currentPage == 1 && lastPage == 1) {
					"$idPrefix$it"
				} else {
					"$idPrefix-$currentPage-$it"
				}

				WallhavenWallpaperDto(id, "https://cdn/w/$id.jpg")
			}

		return WallhavenSearchResponseDto(data, WallhavenMetaDto(currentPage, lastPage, 24, count))
	}

	private fun provider(store: WallhavenSelectionStore) = WallhavenProvider(wallhavenApiService, store)

	private fun stubSearch(
		response: WallhavenSearchResponseDto,
		query: String? = null,
		page: Int? = null
	) {
		coEvery {
			wallhavenApiService.search(
				query = query ?: any(),
				categories = any(),
				purity = any(),
				ratios = any(),
				atleast = any(),
				sorting = any(),
				seed = any(),
				topRange = any(),
				page = page ?: any(),
				colors = any(),
				apiKey = any()
			)
		} returns response
	}

	private fun verifySearch(exactly: Int, page: Int? = null) {
		coVerify(exactly = exactly) {
			wallhavenApiService.search(
				query = any(),
				categories = any(),
				purity = any(),
				ratios = any(),
				atleast = any(),
				sorting = any(),
				seed = any(),
				topRange = any(),
				page = page ?: any(),
				colors = any(),
				apiKey = any()
			)
		}
	}

	@Test
	fun `cached results survive provider recreation`() = runTest {
		val store = InMemoryWallhavenSelectionStore()
		stubSearch(makePage(5))

		val first = provider(store).next(AppSettings(), portraitScreen, emptySet())
		val second = provider(store).next(AppSettings(), portraitScreen, emptySet())

		assertEquals("w1", first.getOrNull()?.identity?.id)
		assertEquals("w2", second.getOrNull()?.identity?.id)
		verifySearch(exactly = 1)
	}

	@Test
	fun `page cursor survives provider recreation and wraps after last page`() = runTest {
		val store = InMemoryWallhavenSelectionStore()
		stubSearch(
			response = makePage(count = 1, currentPage = 1, lastPage = 2),
			page = 1
		)
		stubSearch(
			response = makePage(count = 1, currentPage = 2, lastPage = 2),
			page = 2
		)

		val first = provider(store).next(AppSettings(sorting = Sorting.VIEWS), portraitScreen, emptySet())
		val second = provider(store).next(AppSettings(sorting = Sorting.VIEWS), portraitScreen, emptySet())
		val third = provider(store).next(AppSettings(sorting = Sorting.VIEWS), portraitScreen, emptySet())

		assertEquals("w-1-1", first.getOrNull()?.identity?.id)
		assertEquals("w-2-1", second.getOrNull()?.identity?.id)
		assertEquals("w-1-1", third.getOrNull()?.identity?.id)
		verifySearch(exactly = 2, page = 1)
		verifySearch(exactly = 1, page = 2)
	}

	@Test
	fun `blocked cached results stay filtered across provider recreation`() = runTest {
		val store = InMemoryWallhavenSelectionStore()
		stubSearch(makePage(3))

		val first = provider(store).next(AppSettings(), portraitScreen, emptySet())
		val second = provider(store).next(AppSettings(), portraitScreen, setOf("w2"))

		assertEquals("w1", first.getOrNull()?.identity?.id)
		assertEquals("w3", second.getOrNull()?.identity?.id)
		verifySearch(exactly = 1)
	}

	@Test
	fun `query change invalidates persisted selection state`() = runTest {
		val store = InMemoryWallhavenSelectionStore()
		stubSearch(
			response = makePage(5, idPrefix = "mountain"),
			query = "mountains"
		)
		stubSearch(
			response = makePage(5, idPrefix = "ocean"),
			query = "ocean"
		)

		val first = provider(store).next(AppSettings(searchQuery = "mountains", sorting = Sorting.VIEWS), portraitScreen, emptySet())
		val second = provider(store).next(AppSettings(searchQuery = "ocean", sorting = Sorting.VIEWS), portraitScreen, emptySet())

		assertEquals("mountain1", first.getOrNull()?.identity?.id)
		assertEquals("ocean1", second.getOrNull()?.identity?.id)
		verifySearch(exactly = 2)
	}
}
