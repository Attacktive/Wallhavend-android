package xyz.attacktive.wallhavend

import java.io.File
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import xyz.attacktive.wallhavend.domain.model.AppSettings
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
}
