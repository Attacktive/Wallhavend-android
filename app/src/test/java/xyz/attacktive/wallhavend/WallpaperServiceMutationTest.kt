package xyz.attacktive.wallhavend

import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import xyz.attacktive.wallhavend.domain.model.AppSettings
import xyz.attacktive.wallhavend.domain.model.RotationMode
import xyz.attacktive.wallhavend.domain.model.ScreenInfo
import xyz.attacktive.wallhavend.domain.model.Wallpaper
import xyz.attacktive.wallhavend.domain.model.WallpaperIdentity
import xyz.attacktive.wallhavend.domain.model.WallpaperSource
import xyz.attacktive.wallhavend.domain.model.WallpaperTarget
import xyz.attacktive.wallhavend.domain.repository.ServiceStateRepository
import xyz.attacktive.wallhavend.domain.repository.SettingsRepository
import xyz.attacktive.wallhavend.domain.repository.WallpaperRepository
import xyz.attacktive.wallhavend.domain.service.NetworkState
import xyz.attacktive.wallhavend.domain.service.WallpaperApplier
import xyz.attacktive.wallhavend.domain.service.WallpaperFileManager
import xyz.attacktive.wallhavend.domain.service.WallpaperMutationCoordinator
import xyz.attacktive.wallhavend.domain.service.WallpaperService

class WallpaperServiceMutationTest {
	@get:Rule
	val tmpFolder = TemporaryFolder()

	@Test
	fun `a manual update waits for a scheduled update through persistence`() = runTest {
		val first = File(tmpFolder.root, "first.jpg").also { it.writeText("first") }
		val second = File(tmpFolder.root, "second.jpg").also { it.writeText("second") }
		val firstEntered = CountDownLatch(1)
		val releaseFirst = CountDownLatch(1)
		val secondEntered = CountDownLatch(1)
		val listCalls = AtomicInteger()
		val fileManager = mockk<WallpaperFileManager>()
		every { fileManager.listAll() } answers {
			if (listCalls.getAndIncrement() == 0) {
				firstEntered.countDown()
				check(releaseFirst.await(5, TimeUnit.SECONDS))
				listOf(first)
			} else {
				secondEntered.countDown()
				listOf(second, first)
			}
		}

		val settingsRepository = settingsRepository(AppSettings(rotationMode = RotationMode.FRESH_ANY))
		val stateRepository = ServiceStateRepository()
		val applier = mockk<WallpaperApplier>()
		every { applier.apply(any(), WallpaperTarget.HOME) } returns Result.success(0)
		val service = service(
			fileManager = fileManager,
			settingsRepository = settingsRepository,
			stateRepository = stateRepository,
			wallpaperApplier = applier
		)

		val scheduled = async(Dispatchers.Default) { service.performUpdate(forceDownload = false) }
		assertTrue(firstEntered.await(5, TimeUnit.SECONDS))

		val manual = async(Dispatchers.Default) { service.performUpdate(forceDownload = true) }
		assertFalse(secondEntered.await(250, TimeUnit.MILLISECONDS))

		releaseFirst.countDown()
		scheduled.await()
		manual.await()

		assertTrue(secondEntered.await(5, TimeUnit.SECONDS))
		assertEquals(second.absolutePath, stateRepository.state.value.currentWallpaperPath)
		assertEquals(first.absolutePath, stateRepository.state.value.previousWallpaperPath)
		coVerifyOrder {
			settingsRepository.saveServiceState(any(), first.absolutePath, null)
			settingsRepository.saveServiceState(any(), second.absolutePath, first.absolutePath)
		}
	}

	@Test
	fun `queued apply keeps current and previous persistence in application order`() = runTest {
		val first = File(tmpFolder.root, "first.jpg").also { it.writeText("first") }
		val second = File(tmpFolder.root, "second.jpg").also { it.writeText("second") }
		val firstEntered = CountDownLatch(1)
		val releaseFirst = CountDownLatch(1)
		val secondApplied = CountDownLatch(1)
		val applier = mockk<WallpaperApplier>()

		every { applier.apply(first, WallpaperTarget.HOME) } answers {
			firstEntered.countDown()
			check(releaseFirst.await(5, TimeUnit.SECONDS))
			Result.success(0)
		}
		every { applier.apply(second, WallpaperTarget.HOME) } answers {
			secondApplied.countDown()
			Result.success(0)
		}

		val settingsRepository = settingsRepository()
		val stateRepository = ServiceStateRepository()
		val service = service(
			fileManager = mockk(relaxed = true),
			settingsRepository = settingsRepository,
			stateRepository = stateRepository,
			wallpaperApplier = applier
		)

		val firstApply = async(Dispatchers.Default) { service.applySpecificPath(first.absolutePath) }
		assertTrue(firstEntered.await(5, TimeUnit.SECONDS))

		val secondApply = async(Dispatchers.Default) { service.applySpecificPath(second.absolutePath) }
		assertFalse(secondApplied.await(250, TimeUnit.MILLISECONDS))

		releaseFirst.countDown()
		firstApply.await()
		secondApply.await()

		assertEquals(second.absolutePath, stateRepository.state.value.currentWallpaperPath)
		assertEquals(first.absolutePath, stateRepository.state.value.previousWallpaperPath)
		coVerifyOrder {
			settingsRepository.saveServiceState(any(), first.absolutePath, null)
			settingsRepository.saveServiceState(any(), second.absolutePath, first.absolutePath)
		}
	}

	@Test
	fun `online update trim waits for an in-flight wallpaper apply`() = runTest {
		val wallpapersDir = File(tmpFolder.root, "wallpapers").also { it.mkdirs() }
		val applying = File(wallpapersDir, "applying.jpg").also {
			it.writeText("applying")
			it.setLastModified(1)
		}
		val downloaded = File(wallpapersDir, "downloaded.jpg").also {
			it.writeText("downloaded")
			it.setLastModified(2)
		}
		val wallpaper = Wallpaper(
			identity = WallpaperIdentity(WallpaperSource.WALLHAVEN, "downloaded"),
			directUrl = "https://example.test/downloaded.jpg"
		)

		val firstEntered = CountDownLatch(1)
		val releaseFirst = CountDownLatch(1)
		val downloadedApplied = CountDownLatch(1)
		val applier = mockk<WallpaperApplier>()
		every { applier.apply(applying, WallpaperTarget.HOME) } answers {
			firstEntered.countDown()
			check(releaseFirst.await(5, TimeUnit.SECONDS))
			Result.success(0)
		}
		every { applier.apply(downloaded, WallpaperTarget.HOME) } answers {
			downloadedApplied.countDown()
			Result.success(0)
		}

		val wallpaperRepository = mockk<WallpaperRepository>()
		coEvery { wallpaperRepository.next(any(), any()) } returns Result.success(wallpaper to downloaded)

		val service = service(
			fileManager = WallpaperFileManager(wallpapersDir, mockk(relaxed = true)),
			settingsRepository = settingsRepository(AppSettings(rotationMode = RotationMode.FRESH_ANY, poolSize = 1)),
			wallpaperRepository = wallpaperRepository,
			wallpaperApplier = applier,
			networkState = NetworkState(online = true, onWifi = true)
		)

		val apply = async(Dispatchers.Default) { service.applySpecificPath(applying.absolutePath) }
		assertTrue(firstEntered.await(5, TimeUnit.SECONDS))

		val update = async(Dispatchers.Default) { service.performUpdate(forceDownload = true) }
		assertFalse(downloadedApplied.await(250, TimeUnit.MILLISECONDS))
		coVerify(exactly = 0) { wallpaperRepository.next(any(), any()) }
		assertTrue(applying.exists())

		releaseFirst.countDown()
		apply.await()
		update.await()

		assertTrue(downloadedApplied.await(5, TimeUnit.SECONDS))
		assertFalse(applying.exists())
		coVerify(exactly = 1) { wallpaperRepository.next(any(), any()) }
	}

	private fun service(
		fileManager: WallpaperFileManager,
		settingsRepository: SettingsRepository = settingsRepository(),
		stateRepository: ServiceStateRepository = ServiceStateRepository(),
		wallpaperMutationCoordinator: WallpaperMutationCoordinator = WallpaperMutationCoordinator(),
		wallpaperRepository: WallpaperRepository = mockk(relaxed = true),
		wallpaperApplier: WallpaperApplier = mockk(relaxed = true),
		networkState: NetworkState = NetworkState(online = false, onWifi = false)
	) = WallpaperService().apply {
		this.settingsRepository = settingsRepository
		this.wallpaperRepository = wallpaperRepository
		this.fileManager = fileManager
		this.stateRepository = stateRepository
		this.wallpaperMutationCoordinator = wallpaperMutationCoordinator
		this.wallpaperApplier = wallpaperApplier
		this.networkStateProvider = { networkState }
		this.screenInfoProvider = { ScreenInfo("9x16", 1080, 2400) }
		this.notificationRefresher = {}
	}

	private fun settingsRepository(settings: AppSettings = AppSettings(rotationMode = RotationMode.PINNED_ONLY)): SettingsRepository {
		val repository = mockk<SettingsRepository>()
		every { repository.settings } returns flowOf(settings)
		coEvery { repository.saveServiceState(any(), any(), any()) } returns Unit

		return repository
	}
}
