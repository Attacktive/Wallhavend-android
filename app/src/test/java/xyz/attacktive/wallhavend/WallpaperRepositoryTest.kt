package xyz.attacktive.wallhavend

import java.io.File
import java.io.IOException
import kotlinx.coroutines.test.runTest
import io.mockk.coEvery
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.attacktive.wallhavend.domain.model.AppSettings
import xyz.attacktive.wallhavend.domain.model.NoResultsException
import xyz.attacktive.wallhavend.domain.model.ScreenInfo
import xyz.attacktive.wallhavend.domain.model.Wallpaper
import xyz.attacktive.wallhavend.domain.model.WallpaperIdentity
import xyz.attacktive.wallhavend.domain.model.WallpaperSource
import xyz.attacktive.wallhavend.domain.repository.WallpaperProvider
import xyz.attacktive.wallhavend.domain.repository.WallpaperRepository
import xyz.attacktive.wallhavend.domain.service.WallpaperFileManager

class WallpaperRepositoryTest {
	private val fileManager = mockk<WallpaperFileManager>()

	private val screen = ScreenInfo("9x16", 1080, 2400)

	/**
	 * Sources are consulted in random order, so a single run may or may not exercise the fall-back path.
	 * The assertions hold for every order, and repeating makes it near-certain that each one gets covered.
	 */
	private val shuffleAttempts = 25

	private fun makeWallpaper(id: String) = Wallpaper(
		identity = WallpaperIdentity(WallpaperSource.WALLHAVEN, id),
		directUrl = "https://cdn/w/$id.jpg",
		resolution = "1920x1080"
	)

	private fun createRepository(vararg providers: WallpaperProvider) = WallpaperRepository(providers.toSet(), fileManager)

	@Test
	fun `next returns the picked wallpaper alongside its downloaded file`() = runTest {
		val wallpaper = makeWallpaper("abc123")
		coEvery { fileManager.download(wallpaper) } returns Result.success(File("/tmp/wallhaven_abc123.jpg"))

		val repository = createRepository(FakeWallpaperProvider(Result.success(wallpaper)))
		val result = repository.next(AppSettings(), screen)

		assertTrue(result.isSuccess)
		assertEquals(wallpaper, result.getOrNull()?.first)
		assertEquals("wallhaven_abc123.jpg", result.getOrNull()?.second?.name)
	}

	@Test
	fun `a source that fails to search hands over to another one`() = runTest {
		val wallpaper = makeWallpaper("good")
		coEvery { fileManager.download(wallpaper) } returns Result.success(File("/tmp/wallhaven_good.jpg"))

		val repository = createRepository(
			FakeWallpaperProvider(Result.failure(IOException("search is down"))),
			FakeWallpaperProvider(Result.success(wallpaper))
		)

		repeat(shuffleAttempts) {
			assertEquals(wallpaper, repository.next(AppSettings(), screen).getOrNull()?.first)
		}
	}

	@Test
	fun `a source whose download fails hands over to another one`() = runTest {
		val rotted = makeWallpaper("rotted")
		val alive = makeWallpaper("alive")
		coEvery { fileManager.download(rotted) } returns Result.failure(IOException("HTTP 404"))
		coEvery { fileManager.download(alive) } returns Result.success(File("/tmp/wallhaven_alive.jpg"))

		val repository = createRepository(
			FakeWallpaperProvider(Result.success(rotted)),
			FakeWallpaperProvider(Result.success(alive))
		)

		repeat(shuffleAttempts) {
			assertEquals(alive, repository.next(AppSettings(), screen).getOrNull()?.first)
		}
	}

	@Test
	fun `an update fails only once every source has`() = runTest {
		val repository = createRepository(
			FakeWallpaperProvider(Result.failure(NoResultsException())),
			FakeWallpaperProvider(Result.failure(NoResultsException()))
		)

		val result = repository.next(AppSettings(), screen)

		assertTrue(result.isFailure)
		assertTrue(result.exceptionOrNull() is NoResultsException)
	}

	@Test
	fun `an empty pool from one source loses the error report to a real failure from another`() = runTest {
		val repository = createRepository(
			FakeWallpaperProvider(Result.failure(NoResultsException())),
			FakeWallpaperProvider(Result.failure(IOException("search is down")))
		)

		repeat(shuffleAttempts) {
			assertTrue(repository.next(AppSettings(), screen).exceptionOrNull() is IOException)
		}
	}

	@Test
	fun `a source the user has not enabled is never consulted`() = runTest {
		val wallhaven = FakeWallpaperProvider(Result.failure(NoResultsException()), WallpaperSource.WALLHAVEN)
		val openverse = FakeWallpaperProvider(Result.failure(NoResultsException()), WallpaperSource.OPENVERSE)

		createRepository(wallhaven, openverse).next(AppSettings(enabledSources = setOf(WallpaperSource.WALLHAVEN)), screen)

		assertTrue(wallhaven.consulted)
		assertFalse(openverse.consulted)
	}

	@Test
	fun `enabling a second source brings it into the rotation`() = runTest {
		val wallpaper = makeWallpaper("abc123")
		coEvery { fileManager.download(wallpaper) } returns Result.success(File("/tmp/wallhaven_abc123.jpg"))

		val openverse = FakeWallpaperProvider(Result.success(wallpaper), WallpaperSource.OPENVERSE)
		val repository = createRepository(FakeWallpaperProvider(Result.failure(NoResultsException())), openverse)

		repeat(shuffleAttempts) {
			repository.next(AppSettings(enabledSources = setOf(WallpaperSource.WALLHAVEN, WallpaperSource.OPENVERSE)), screen)
		}

		assertTrue(openverse.consulted)
	}

	@Test
	fun `blocked ids reach a source stripped of the qualifying prefix`() = runTest {
		val provider = FakeWallpaperProvider(Result.failure(NoResultsException()))
		val blockedIds = setOf("wallhaven_abc123", "def456")

		createRepository(provider).next(AppSettings(blockedIds = blockedIds), screen)

		assertEquals(setOf("abc123", "def456"), provider.receivedBlockedIds)
	}
}

/** Stands in for a source: hands back a fixed outcome and records whether — and with which blocked ids — it was consulted. */
private class FakeWallpaperProvider(private val result: Result<Wallpaper>, override val source: WallpaperSource = WallpaperSource.WALLHAVEN): WallpaperProvider {
	var receivedBlockedIds: Set<String>? = null
		private set

	var consulted = false
		private set

	override suspend fun next(settings: AppSettings, screenInfo: ScreenInfo, blockedIds: Set<String>): Result<Wallpaper> {
		consulted = true
		receivedBlockedIds = blockedIds

		return result
	}
}
