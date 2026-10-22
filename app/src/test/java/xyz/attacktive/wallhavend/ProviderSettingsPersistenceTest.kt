package xyz.attacktive.wallhavend

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.attacktive.wallhavend.domain.model.AppSettings
import xyz.attacktive.wallhavend.domain.model.WallpaperSource
import xyz.attacktive.wallhavend.domain.model.configuredSources
import xyz.attacktive.wallhavend.domain.model.requiresPexafySetup
import xyz.attacktive.wallhavend.domain.repository.SettingsRepository

class ProviderSettingsPersistenceTest {
	private val settingsStore = FailingStore()
	private val credentialStore = FailingStore()
	private val repository = SettingsRepository(settingsStore, credentialStore, FakeAppLogger())
	private val pexafyKey = stringPreferencesKey("pexafy_api_key")

	@Test
	fun `source activation rejects missing and whitespace credentials`() = runTest {
		listOf("", " ", "\t\n").forEach { key ->
			credentialStore.edit { it[pexafyKey] = key }
			assertTrue(runCatching { repository.toggleSource(WallpaperSource.PEXAFY) }.exceptionOrNull() is IllegalArgumentException)
			assertEquals(setOf(WallpaperSource.WALLHAVEN), repository.settings.first().enabledSources)
		}
	}

	@Test
	fun `explicit activation persists credentials separately and survives repository reload`() = runTest {
		repository.savePexafyKey(" key ", enable = true)
		val reloaded = SettingsRepository(settingsStore, credentialStore, FakeAppLogger()).settings.first()
		assertEquals("key", reloaded.pexafyApiKey)
		assertEquals(setOf(WallpaperSource.WALLHAVEN, WallpaperSource.PEXAFY), reloaded.configuredSources)
		assertNull(settingsStore.data.first()[pexafyKey])
		assertEquals("key", credentialStore.data.first()[pexafyKey])
	}

	@Test
	fun `key storage without activation and anonymous Wallhaven remain supported`() = runTest {
		repository.savePexafyKey("key", enable = false)
		repository.saveWallhavenKey("")
		assertEquals(setOf(WallpaperSource.WALLHAVEN), repository.settings.first().configuredSources)
		repository.toggleSource(WallpaperSource.PEXAFY)
		assertTrue(WallpaperSource.PEXAFY in repository.settings.first().configuredSources)
	}

	@Test
	fun `removal preserves both other providers`() = runTest {
		repository.toggleSource(WallpaperSource.OPENVERSE)
		repository.savePexafyKey("key", enable = true)
		repository.savePexafyKey("", enable = false)
		val loaded = repository.settings.first()
		assertEquals(setOf(WallpaperSource.WALLHAVEN, WallpaperSource.OPENVERSE), loaded.enabledSources)
		assertEquals("", loaded.pexafyApiKey)
	}

	@Test
	fun `sole provider key removal needs an explicit replacement`() = runTest {
		repository.savePexafyKey("key", enable = true)
		repository.toggleSource(WallpaperSource.WALLHAVEN)
		assertTrue(runCatching { repository.savePexafyKey("", enable = false) }.exceptionOrNull() is IllegalArgumentException)
		assertEquals(setOf(WallpaperSource.PEXAFY), repository.settings.first().enabledSources)
		assertEquals("key", repository.settings.first().pexafyApiKey)

		repository.toggleSource(WallpaperSource.OPENVERSE)
		repository.savePexafyKey("", enable = false)
		assertEquals(setOf(WallpaperSource.OPENVERSE), repository.settings.first().enabledSources)
	}

	@Test
	fun `failed credential write never activates Pexafy`() = runTest {
		credentialStore.failNextWrite = true
		assertTrue(runCatching { repository.savePexafyKey("key", enable = true) }.exceptionOrNull() is IOException)
		assertEquals(setOf(WallpaperSource.WALLHAVEN), repository.settings.first().enabledSources)
		assertEquals("", repository.settings.first().pexafyApiKey)
	}

	@Test
	fun `failed activation leaves a saved key but inactive provider and can be retried`() = runTest {
		settingsStore.failNextWrite = true
		assertTrue(runCatching { repository.savePexafyKey("key", enable = true) }.exceptionOrNull() is IOException)
		assertEquals("key", repository.settings.first().pexafyApiKey)
		assertFalse(WallpaperSource.PEXAFY in repository.settings.first().enabledSources)

		repository.savePexafyKey("key", enable = true)
		assertTrue(WallpaperSource.PEXAFY in repository.settings.first().configuredSources)
	}

	@Test
	fun `failed disable retains the active credential`() = runTest {
		repository.savePexafyKey("key", enable = true)
		settingsStore.failNextWrite = true
		assertTrue(runCatching { repository.savePexafyKey("", enable = false) }.exceptionOrNull() is IOException)
		assertEquals("key", repository.settings.first().pexafyApiKey)
		assertTrue(WallpaperSource.PEXAFY in repository.settings.first().configuredSources)
	}

	@Test
	fun `failed credential deletion leaves Pexafy disabled and can be retried`() = runTest {
		repository.savePexafyKey("key", enable = true)
		credentialStore.failNextWrite = true
		assertTrue(runCatching { repository.savePexafyKey("", enable = false) }.exceptionOrNull() is IOException)
		assertEquals("key", repository.settings.first().pexafyApiKey)
		assertEquals(setOf(WallpaperSource.WALLHAVEN), repository.settings.first().enabledSources)

		repository.savePexafyKey("", enable = false)
		assertEquals("", repository.settings.first().pexafyApiKey)
	}

	@Test
	fun `stale ordinary settings cannot restore a removed key or source`() = runTest {
		repository.savePexafyKey("key", enable = true)
		val stale = repository.settings.first()
		repository.savePexafyKey("", enable = false)
		repository.savePreferences(stale.copy(searchQuery = "mountains"))
		val loaded = repository.settings.first()
		assertEquals("mountains", loaded.searchQuery)
		assertEquals("", loaded.pexafyApiKey)
		assertEquals(setOf(WallpaperSource.WALLHAVEN), loaded.enabledSources)
	}

	@Test
	fun `stale ordinary settings cannot erase a newly saved key`() = runTest {
		val stale = repository.settings.first()
		repository.savePexafyKey("key", enable = true)
		repository.savePreferences(stale.copy(filterColor = "ffffff"))
		val loaded = repository.settings.first()
		assertEquals("key", loaded.pexafyApiKey)
		assertEquals("ffffff", loaded.filterColor)
		assertTrue(WallpaperSource.PEXAFY in loaded.configuredSources)
	}

	@Test
	fun `concurrent source edits preserve each selection`() = runTest {
		repository.savePexafyKey("key", enable = false)
		val first = launch { repository.toggleSource(WallpaperSource.PEXAFY) }
		val second = launch { repository.toggleSource(WallpaperSource.OPENVERSE) }
		first.join()
		second.join()
		assertEquals(WallpaperSource.entries.toSet(), repository.settings.first().configuredSources)
	}

	@Test
	fun `legacy keyless sole selection remains pending until an explicit choice`() = runTest {
		settingsStore.edit { it[stringSetPreferencesKey("enabled_sources")] = setOf(WallpaperSource.PEXAFY.key) }
		val loaded = repository.settings.first()
		assertTrue(loaded.requiresPexafySetup)
		assertTrue(loaded.configuredSources.isEmpty())
		repository.savePreferences(loaded.copy(searchQuery = "forest"))
		assertTrue(repository.settings.first().requiresPexafySetup)

		repository.toggleSource(WallpaperSource.OPENVERSE)
		assertEquals(setOf(WallpaperSource.OPENVERSE), repository.settings.first().configuredSources)
	}

	private class FailingStore: DataStore<Preferences> {
		override val data = MutableStateFlow<Preferences>(emptyPreferences())
		private val mutex = Mutex()
		var failNextWrite = false

		override suspend fun updateData(transform: suspend (Preferences) -> Preferences) = mutex.withLock {
			if (failNextWrite) {
				failNextWrite = false

				throw IOException("Simulated storage failure")
			}

			transform(data.value).also { data.value = it }
		}
	}
}
