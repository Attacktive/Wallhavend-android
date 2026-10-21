package xyz.attacktive.wallhavend

import java.io.File
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import io.mockk.coEvery
import io.mockk.every
import io.mockk.coVerify
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import xyz.attacktive.wallhavend.domain.model.AppSettings
import xyz.attacktive.wallhavend.domain.model.AppError
import xyz.attacktive.wallhavend.domain.model.MissingApiKeyException
import xyz.attacktive.wallhavend.domain.model.QuotaExceededException
import xyz.attacktive.wallhavend.domain.model.RateLimitedException
import xyz.attacktive.wallhavend.domain.model.ScreenInfo
import xyz.attacktive.wallhavend.domain.model.Wallpaper
import xyz.attacktive.wallhavend.domain.model.WallpaperIdentity
import xyz.attacktive.wallhavend.domain.model.WallpaperSource
import xyz.attacktive.wallhavend.domain.service.NetworkState
import xyz.attacktive.wallhavend.domain.repository.ServiceStateRepository
import xyz.attacktive.wallhavend.domain.repository.SettingsRepository
import xyz.attacktive.wallhavend.domain.repository.WallpaperRepository
import xyz.attacktive.wallhavend.domain.service.WallpaperApplier
import xyz.attacktive.wallhavend.domain.service.WallpaperFileManager
import xyz.attacktive.wallhavend.domain.service.WallpaperMutationCoordinator
import xyz.attacktive.wallhavend.domain.service.WallpaperRotationExecutor

class WallpaperRotationExecutorRestoreTest {
	@get:Rule
	val tmpFolder = TemporaryFolder()

	@Test
	fun `manual mutation before restore does not hide the cached pool`() = runTest {
		val cached = File(tmpFolder.root, "cached.jpg").also { it.writeText("cached") }
		val settingsRepository = mockk<SettingsRepository>()
		every { settingsRepository.settings } returns flowOf(AppSettings())
		coEvery { settingsRepository.loadServiceState() } returns Triple(123L, cached.absolutePath, null)
		coEvery { settingsRepository.saveServiceState(any(), any(), any()) } returns Unit

		val fileManager = mockk<WallpaperFileManager>()
		every { fileManager.listAll() } returns listOf(cached)

		val applier = mockk<WallpaperApplier>()
		every { applier.apply(any(), any()) } returns Result.success(0)

		val stateRepository = ServiceStateRepository()
		val executor = WallpaperRotationExecutor(
			context = mockk(relaxed = true),
			settingsRepository = settingsRepository,
			wallpaperRepository = mockk<WallpaperRepository>(relaxed = true),
			fileManager = fileManager,
			stateRepository = stateRepository,
			wallpaperMutationCoordinator = WallpaperMutationCoordinator(),
			wallpaperApplier = applier
		)

		executor.applySpecificPath(cached.absolutePath)
		assertEquals(emptyList<String>(), stateRepository.state.value.poolPaths)

		executor.restoreState()

		assertEquals(listOf(cached.absolutePath), stateRepository.state.value.poolPaths)
		assertEquals(cached.absolutePath, stateRepository.state.value.currentWallpaperPath)
	}

	@Test
	fun `performUpdate maps Pexafy MissingApiKey, QuotaExceeded, and RateLimited exceptions to actionable AppErrors`() = runTest {
		val cached = File(tmpFolder.root, "cached.jpg").also { it.writeText("cached") }
		val settingsRepository = mockk<SettingsRepository>(relaxed = true)
		every { settingsRepository.settings } returns flowOf(AppSettings(enabledSources = setOf(WallpaperSource.PEXAFY)))

		val wallpaperRepository = mockk<WallpaperRepository>()
		val fileManager = mockk<WallpaperFileManager>()
		every { fileManager.listAll() } returns listOf(cached)

		val applier = mockk<WallpaperApplier>()
		every { applier.apply(any(), any()) } returns Result.success(0)

		val stateRepository = ServiceStateRepository()
		val executor = WallpaperRotationExecutor(
			context = mockk(relaxed = true),
			settingsRepository = settingsRepository,
			wallpaperRepository = wallpaperRepository,
			fileManager = fileManager,
			stateRepository = stateRepository,
			wallpaperMutationCoordinator = WallpaperMutationCoordinator(),
			wallpaperApplier = applier
		).apply {
			networkStateProvider = { NetworkState(online = true, onWifi = true) }
			screenInfoProvider = { ScreenInfo("9x16", 1080, 2400) }
		}

		coEvery { wallpaperRepository.next(any(), any()) } returns Result.failure(MissingApiKeyException(WallpaperSource.PEXAFY))
		executor.performUpdate(forceDownload = true)
		assertEquals(AppError.MissingApiKey(WallpaperSource.PEXAFY), stateRepository.state.value.error)

		coEvery { wallpaperRepository.next(any(), any()) } returns Result.failure(QuotaExceededException(WallpaperSource.PEXAFY))
		executor.performUpdate(forceDownload = true)
		assertEquals(AppError.QuotaExceeded(WallpaperSource.PEXAFY), stateRepository.state.value.error)

		coEvery { wallpaperRepository.next(any(), any()) } returns Result.failure(RateLimitedException(WallpaperSource.PEXAFY, 45L))
		executor.performUpdate(forceDownload = true)
		assertEquals(AppError.RateLimited(WallpaperSource.PEXAFY, 45L), stateRepository.state.value.error)
	}

	@Test
	fun `performUpdate remembers thumbnail URL when a Pexafy wallpaper is fetched`() = runTest {
		val downloaded = File(tmpFolder.root, "pexafy_p1.jpg").also { it.writeText("image") }
		val identity = WallpaperIdentity(WallpaperSource.PEXAFY, "p1")
		val wallpaper = Wallpaper(identity, "https://cdn/p1.jpg", thumbnailUrl = "https://cdn/p1-thumb.jpg")

		val settingsRepository = mockk<SettingsRepository>(relaxed = true)
		every { settingsRepository.settings } returns flowOf(AppSettings(enabledSources = setOf(WallpaperSource.PEXAFY)))

		val wallpaperRepository = mockk<WallpaperRepository>()
		coEvery { wallpaperRepository.next(any(), any()) } returns Result.success(wallpaper to downloaded)

		val fileManager = mockk<WallpaperFileManager>()
		every { fileManager.listAll() } returns listOf(downloaded)
		every { fileManager.trimToSize(any(), any()) } returns listOf(downloaded)

		val applier = mockk<WallpaperApplier>()
		every { applier.apply(any(), any()) } returns Result.success(0)

		val executor = WallpaperRotationExecutor(
			context = mockk(relaxed = true),
			settingsRepository = settingsRepository,
			wallpaperRepository = wallpaperRepository,
			fileManager = fileManager,
			stateRepository = ServiceStateRepository(),
			wallpaperMutationCoordinator = WallpaperMutationCoordinator(),
			wallpaperApplier = applier
		).apply {
			networkStateProvider = { NetworkState(online = true, onWifi = true) }
			screenInfoProvider = { ScreenInfo("9x16", 1080, 2400) }
		}

		executor.performUpdate(forceDownload = true)

		coVerify { settingsRepository.rememberThumbnail(identity, "https://cdn/p1-thumb.jpg") }
	}
}
