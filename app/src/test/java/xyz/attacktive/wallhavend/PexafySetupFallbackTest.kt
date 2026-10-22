package xyz.attacktive.wallhavend

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import io.mockk.Called
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import xyz.attacktive.wallhavend.data.api.PexafyApiService
import xyz.attacktive.wallhavend.domain.model.AppError
import xyz.attacktive.wallhavend.domain.model.MissingApiKeyException
import xyz.attacktive.wallhavend.domain.model.RotationMode
import xyz.attacktive.wallhavend.domain.model.ScreenInfo
import xyz.attacktive.wallhavend.domain.model.WallpaperSource
import xyz.attacktive.wallhavend.domain.model.WallpaperTarget
import xyz.attacktive.wallhavend.domain.model.requiresPexafySetup
import xyz.attacktive.wallhavend.domain.repository.PexafyProvider
import xyz.attacktive.wallhavend.domain.repository.ServiceStateRepository
import xyz.attacktive.wallhavend.domain.repository.SettingsRepository
import xyz.attacktive.wallhavend.domain.repository.WallpaperProvider
import xyz.attacktive.wallhavend.domain.repository.WallpaperRepository
import xyz.attacktive.wallhavend.domain.service.NetworkState
import xyz.attacktive.wallhavend.domain.service.WallpaperApplier
import xyz.attacktive.wallhavend.domain.service.WallpaperFileManager
import xyz.attacktive.wallhavend.domain.service.WallpaperMutationCoordinator
import xyz.attacktive.wallhavend.domain.service.WallpaperRotationExecutor

@RunWith(Parameterized::class)
class PexafySetupFallbackTest(private val apiKey: String) {
	@get:Rule
	val temporaryFolder = TemporaryFolder()

	@Test
	fun `repository reports missing configuration without consulting any provider`() = runTest {
		val fixture = fixture()
		val failure = fixture.wallpaperRepository.next(fixture.settingsRepository.settings.first(), SCREEN).exceptionOrNull()

		assertTrue(failure is MissingApiKeyException)
		assertEquals(WallpaperSource.PEXAFY, (failure as MissingApiKeyException).source)
		fixture.verifyNoNetwork()
	}

	@Test
	fun `online scheduled and manual updates rotate and persist cached wallpapers`() = runTest {
		val fixture = fixture()
		val first = fixture.cachedWallpaper("first", 1_000)
		val second = fixture.cachedWallpaper("second", 2_000)
		fixture.executor.restoreState()

		fixture.executor.performUpdate(forceDownload = false)

		assertEquals(first.absolutePath, fixture.stateRepository.state.value.currentWallpaperPath)
		assertEquals(first.absolutePath, fixture.settingsRepository.loadServiceState().second)
		assertEquals(AppError.MissingApiKey(WallpaperSource.PEXAFY), fixture.stateRepository.state.value.error)

		fixture.executor.performUpdate(forceDownload = true)

		assertEquals(second.absolutePath, fixture.stateRepository.state.value.currentWallpaperPath)
		assertEquals(first.absolutePath, fixture.stateRepository.state.value.previousWallpaperPath)
		assertEquals(second.absolutePath, fixture.settingsRepository.loadServiceState().second)
		assertEquals(first.absolutePath, fixture.settingsRepository.loadServiceState().third)
		assertEquals(setOf(WallpaperSource.PEXAFY), fixture.settingsRepository.settings.first().enabledSources)
		assertTrue(fixture.settingsRepository.settings.first().requiresPexafySetup)
		verify(exactly = 1) { fixture.wallpaperApplier.apply(first, WallpaperTarget.HOME) }

		verify(exactly = 1) { fixture.wallpaperApplier.apply(second, WallpaperTarget.HOME) }

		fixture.verifyNoNetwork()
	}

	@Test
	fun `an empty cache keeps the setup error without silently enabling another source`() = runTest {
		val fixture = fixture()

		fixture.executor.performUpdate(forceDownload = true)

		assertEquals(AppError.MissingApiKey(WallpaperSource.PEXAFY), fixture.stateRepository.state.value.error)
		assertNull(fixture.stateRepository.state.value.currentWallpaperPath)
		assertEquals(setOf(WallpaperSource.PEXAFY), fixture.settingsRepository.settings.first().enabledSources)
		verify { fixture.wallpaperApplier wasNot Called }

		fixture.verifyNoNetwork()
	}

	private suspend fun TestScope.fixture(): Fixture {
		val settingsFile = temporaryFolder.newFile("settings.preferences_pb")
		val credentialFile = temporaryFolder.newFile("credentials.preferences_pb")
		val settingsStore = PreferenceDataStoreFactory.create(scope = backgroundScope, produceFile = { settingsFile })
		val credentialStore = PreferenceDataStoreFactory.create(scope = backgroundScope, produceFile = { credentialFile })

		settingsStore.edit {
			it[stringSetPreferencesKey("enabled_sources")] = setOf(WallpaperSource.PEXAFY.key)
			it[stringPreferencesKey("rotation_mode")] = RotationMode.FRESH_ANY.name
		}

		// Restore the source preference without a credential, or retain a legacy whitespace-only key.
		if (apiKey.isNotEmpty()) {
			credentialStore.edit { it[stringPreferencesKey("pexafy_api_key")] = apiKey }
		}

		val settingsRepository = SettingsRepository(settingsStore, credentialStore, FakeAppLogger())
		val stateRepository = ServiceStateRepository()
		val wallpaperDirectory = temporaryFolder.newFolder("wallpapers")
		val client = mockk<OkHttpClient>()
		val fileManager = WallpaperFileManager(wallpaperDirectory, client)
		val api = mockk<PexafyApiService>()
		val unselectedProvider = mockk<WallpaperProvider>()
		every { unselectedProvider.source } returns WallpaperSource.WALLHAVEN

		val wallpaperRepository = WallpaperRepository(setOf(PexafyProvider(api), unselectedProvider), fileManager, stateRepository)
		val applier = mockk<WallpaperApplier>()
		every { applier.apply(any(), WallpaperTarget.HOME) } returns Result.success(0)

		val executor = WallpaperRotationExecutor(
			context = mockk(relaxed = true),
			settingsRepository = settingsRepository,
			wallpaperRepository = wallpaperRepository,
			fileManager = fileManager,
			stateRepository = stateRepository,
			wallpaperMutationCoordinator = WallpaperMutationCoordinator(),
			wallpaperApplier = applier
		)
		.apply {
			networkStateProvider = { NetworkState(online = true, onWifi = true) }
			screenInfoProvider = { SCREEN }
		}

		return Fixture(settingsRepository, stateRepository, wallpaperRepository, executor, applier, wallpaperDirectory, api, client, unselectedProvider)
	}

	private class Fixture(val settingsRepository: SettingsRepository, val stateRepository: ServiceStateRepository, val wallpaperRepository: WallpaperRepository, val executor: WallpaperRotationExecutor, val wallpaperApplier: WallpaperApplier, val wallpaperDirectory: File, val api: PexafyApiService, val client: OkHttpClient, val unselectedProvider: WallpaperProvider) {
		fun cachedWallpaper(name: String, modifiedAt: Long) = File(wallpaperDirectory, "pexafy_$name.jpg").apply {
			writeText(name)
			check(setLastModified(modifiedAt))
		}

		fun verifyNoNetwork() {
			verify { api wasNot Called }

			verify { client wasNot Called }

			coVerify(exactly = 0) { unselectedProvider.next(any(), any(), any()) }
		}
	}

	companion object {
		private val SCREEN = ScreenInfo("9x16", 1080, 2400)

		@JvmStatic
		@Parameterized.Parameters(name = "key case {index}")
		fun keys() = listOf("", " \t\n")
	}
}
