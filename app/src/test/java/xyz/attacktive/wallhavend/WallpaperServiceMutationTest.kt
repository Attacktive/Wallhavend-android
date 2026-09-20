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
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import xyz.attacktive.wallhavend.domain.model.AppSettings
import xyz.attacktive.wallhavend.domain.model.RotationMode
import xyz.attacktive.wallhavend.domain.model.WallpaperTarget
import xyz.attacktive.wallhavend.domain.repository.ServiceStateRepository
import xyz.attacktive.wallhavend.domain.repository.SettingsRepository
import xyz.attacktive.wallhavend.domain.repository.WallpaperRepository
import xyz.attacktive.wallhavend.domain.service.WallpaperApplier
import xyz.attacktive.wallhavend.domain.service.WallpaperFileManager
import xyz.attacktive.wallhavend.domain.service.WallpaperMutationCoordinator
import xyz.attacktive.wallhavend.domain.service.WallpaperService

class WallpaperServiceMutationTest {
	@get:Rule
	val tmpFolder = TemporaryFolder()

	@Test
	fun `a manual update waits for an in-flight scheduled update`() = runTest {
		val firstEntered = CountDownLatch(1)
		val releaseFirst = CountDownLatch(1)
		val secondEntered = CountDownLatch(1)
		val listCalls = AtomicInteger()
		val fileManager = mockk<WallpaperFileManager>()
		every { fileManager.listAll() } answers {
			if (listCalls.getAndIncrement() == 0) {
				firstEntered.countDown()
				check(releaseFirst.await(5, TimeUnit.SECONDS))
			} else {
				secondEntered.countDown()
			}

			emptyList()
		}

		val service = service(fileManager)

		val scheduled = async(Dispatchers.Default) { service.performUpdate(forceDownload = false) }
		assertTrue(firstEntered.await(5, TimeUnit.SECONDS))

		val manual = async(Dispatchers.Default) { service.performUpdate(forceDownload = true) }
		assertFalse(secondEntered.await(250, TimeUnit.MILLISECONDS))

		releaseFirst.countDown()
		scheduled.await()
		manual.await()

		assertTrue(secondEntered.await(5, TimeUnit.SECONDS))
		assertEquals(2, listCalls.get())
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
			Result.success(Unit)
		}
		every { applier.apply(second, WallpaperTarget.HOME) } answers {
			secondApplied.countDown()
			Result.success(Unit)
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
	fun `trimming waits for an in-flight wallpaper apply`() = runTest {
		val wallpapersDir = File(tmpFolder.root, "wallpapers").also { it.mkdirs() }
		val applying = File(wallpapersDir, "applying.jpg").also {
			it.writeText("applying")
			it.setLastModified(1)
		}
		File(wallpapersDir, "newer.jpg").also {
			it.writeText("newer")
			it.setLastModified(2)
		}

		val firstEntered = CountDownLatch(1)
		val releaseFirst = CountDownLatch(1)
		val applier = mockk<WallpaperApplier>()
		every { applier.apply(applying, WallpaperTarget.HOME) } answers {
			firstEntered.countDown()
			check(releaseFirst.await(5, TimeUnit.SECONDS))
			Result.success(Unit)
		}

		val fileManager = WallpaperFileManager(wallpapersDir, OkHttpClient())
		val coordinator = WallpaperMutationCoordinator()
		val service = service(
			fileManager = fileManager,
			wallpaperMutationCoordinator = coordinator,
			wallpaperApplier = applier
		)

		val apply = async(Dispatchers.Default) { service.applySpecificPath(applying.absolutePath) }
		assertTrue(firstEntered.await(5, TimeUnit.SECONDS))

		val trim = async(Dispatchers.Default) {
			coordinator.serialize { fileManager.trimToSize(1) }
		}

		Thread.sleep(100)
		assertTrue(applying.exists())

		releaseFirst.countDown()
		apply.await()
		trim.await()

		assertFalse(applying.exists())
	}

	private fun service(
		fileManager: WallpaperFileManager,
		settingsRepository: SettingsRepository = settingsRepository(),
		stateRepository: ServiceStateRepository = ServiceStateRepository(),
		wallpaperMutationCoordinator: WallpaperMutationCoordinator = WallpaperMutationCoordinator(),
		wallpaperApplier: WallpaperApplier = mockk(relaxed = true)
	) = WallpaperService().apply {
		this.settingsRepository = settingsRepository
		this.wallpaperRepository = mockk<WallpaperRepository>(relaxed = true)
		this.fileManager = fileManager
		this.stateRepository = stateRepository
		this.wallpaperMutationCoordinator = wallpaperMutationCoordinator
		this.wallpaperApplier = wallpaperApplier
		this.networkCapabilitiesProvider = { null }
	}

	private fun settingsRepository(): SettingsRepository {
		val repository = mockk<SettingsRepository>()
		every { repository.settings } returns flowOf(AppSettings(rotationMode = RotationMode.PINNED_ONLY))
		coEvery { repository.saveServiceState(any(), any(), any()) } returns Unit

		return repository
	}
}
