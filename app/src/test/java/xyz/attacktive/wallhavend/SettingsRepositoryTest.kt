package xyz.attacktive.wallhavend

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import xyz.attacktive.wallhavend.domain.model.AppSettings
import xyz.attacktive.wallhavend.domain.model.RotationMode
import xyz.attacktive.wallhavend.domain.model.WallpaperTarget
import xyz.attacktive.wallhavend.domain.model.query.Category
import xyz.attacktive.wallhavend.domain.model.query.Purity
import xyz.attacktive.wallhavend.domain.model.query.Sorting
import xyz.attacktive.wallhavend.domain.model.query.ToplistRange
import xyz.attacktive.wallhavend.domain.repository.SettingsRepository

class SettingsRepositoryTest {
	@get:Rule
	val tmpFolder = TemporaryFolder()

	private fun TestScope.createRepository(): SettingsRepository {
		val dataStore = PreferenceDataStoreFactory.create(
			scope = backgroundScope,
			produceFile = { tmpFolder.newFile("test_prefs.preferences_pb") }
		)

		return SettingsRepository(dataStore, FakeAppLogger())
	}

	@Test
	fun `defaults are correct`() = runTest {
		val repository = createRepository()
		val settings = repository.settings.first()

		assertEquals(60, settings.updateIntervalMinutes)
		assertEquals(RotationMode.FRESH_WIFI, settings.rotationMode)
		assertEquals(10, settings.poolSize)
		assertEquals(WallpaperTarget.HOME, settings.wallpaperTarget)
		assertEquals(setOf(Category.GENERAL), settings.categories)
		assertEquals(setOf(Purity.SFW), settings.purity)
		assertEquals(Sorting.RANDOM, settings.sorting)
		assertEquals(ToplistRange.ONE_MONTH, settings.toplistRange)
	}

	@Test
	fun `legacy unmetered_only key does not influence rotation mode`() = runTest {
		val dataStore = PreferenceDataStoreFactory.create(
			scope = backgroundScope,
			produceFile = { tmpFolder.newFile("legacy_prefs.preferences_pb") }
		)

		dataStore.edit { prefs ->
			prefs[booleanPreferencesKey("unmetered_only")] = false
		}

		val repository = SettingsRepository(dataStore, FakeAppLogger())

		assertEquals(RotationMode.FRESH_WIFI, repository.settings.first().rotationMode)
	}

	@Test
	fun `legacy pool size of 0 migrates to the new minimum of 1`() = runTest {
		val dataStore = PreferenceDataStoreFactory.create(
			scope = backgroundScope,
			produceFile = { tmpFolder.newFile("migrate_pool.preferences_pb") }
		)

		dataStore.edit { prefs -> prefs[intPreferencesKey("pool_size")] = 0 }

		val repository = SettingsRepository(dataStore, FakeAppLogger())

		assertEquals(1, repository.settings.first().poolSize)
	}

	@Test
	fun `save and reload settings round-trips correctly`() = runTest {
		val repository = createRepository()
		val modified = AppSettings(
			searchQuery = "mountains",
			categories = setOf(Category.GENERAL, Category.ANIME),
			purity = setOf(Purity.SFW, Purity.SKETCHY),
			updateIntervalMinutes = 30,
			wallpaperTarget = WallpaperTarget.HOME,
			rotationMode = RotationMode.FRESH_ANY,
			poolSize = 25,
			apiKey = "secret",
			autoStartOnBoot = true,
			sorting = Sorting.TOPLIST,
			toplistRange = ToplistRange.ONE_YEAR
		)

		repository.save(modified)

		val loaded = repository.settings.first { it == modified }

		assertEquals(modified, loaded)
	}

	@Test
	fun `legacy wifiOnly false migrates to fresh-any rotation mode`() = runTest {
		val dataStore = PreferenceDataStoreFactory.create(
			scope = backgroundScope,
			produceFile = { tmpFolder.newFile("migrate_any.preferences_pb") }
		)

		dataStore.edit { prefs -> prefs[booleanPreferencesKey("wifi_only")] = false }

		val repository = SettingsRepository(dataStore, FakeAppLogger())

		assertEquals(RotationMode.FRESH_ANY, repository.settings.first().rotationMode)
	}

	@Test
	fun `legacy wifiOnly true migrates to fresh-wifi rotation mode`() = runTest {
		val dataStore = PreferenceDataStoreFactory.create(
			scope = backgroundScope,
			produceFile = { tmpFolder.newFile("migrate_wifi.preferences_pb") }
		)

		dataStore.edit { prefs -> prefs[booleanPreferencesKey("wifi_only")] = true }

		val repository = SettingsRepository(dataStore, FakeAppLogger())

		assertEquals(RotationMode.FRESH_WIFI, repository.settings.first().rotationMode)
	}

	@Test
	fun `explicit rotation mode wins over legacy wifiOnly`() = runTest {
		val dataStore = PreferenceDataStoreFactory.create(
			scope = backgroundScope,
			produceFile = { tmpFolder.newFile("explicit_mode.preferences_pb") }
		)

		dataStore.edit { prefs ->
			prefs[booleanPreferencesKey("wifi_only")] = false
			prefs[stringPreferencesKey("rotation_mode")] = "PINNED_ONLY"
		}

		val repository = SettingsRepository(dataStore, FakeAppLogger())

		assertEquals(RotationMode.PINNED_ONLY, repository.settings.first().rotationMode)
	}

	@Test
	fun `rotation mode round-trips through save`() = runTest {
		val repository = createRepository()
		repository.save(AppSettings(searchQuery = "marker", rotationMode = RotationMode.PINNED_ONLY))

		val loaded = repository.settings.first { it.searchQuery == "marker" }

		assertEquals(RotationMode.PINNED_ONLY, loaded.rotationMode)
	}

	@Test
	fun `pin and unpin round-trip and survive a full settings save`() = runTest {
		val repository = createRepository()
		assertTrue(repository.settings.first().pinnedIds.isEmpty())

		repository.pin("abc123")
		repository.pin("def456")
		assertEquals(setOf("abc123", "def456"), repository.settings.first { it.pinnedIds.size == 2 }.pinnedIds)

		repository.save(AppSettings(searchQuery = "marker"))

		val afterSave = repository.settings.first { it.searchQuery == "marker" }
		assertEquals(setOf("abc123", "def456"), afterSave.pinnedIds)

		repository.unpin("abc123")
		assertEquals(setOf("def456"), repository.settings.first { it.pinnedIds == setOf("def456") }.pinnedIds)
	}

	@Test
	fun `block and unblock round-trip and survive a full settings save`() = runTest {
		val repository = createRepository()
		assertTrue(repository.settings.first().blockedIds.isEmpty())

		repository.block("abc123")
		repository.block("def456")
		assertEquals(setOf("abc123", "def456"), repository.settings.first { it.blockedIds.size == 2 }.blockedIds)

		repository.save(AppSettings(searchQuery = "marker"))

		val afterSave = repository.settings.first { it.searchQuery == "marker" }
		assertEquals(setOf("abc123", "def456"), afterSave.blockedIds)

		repository.unblock("abc123")
		assertEquals(setOf("def456"), repository.settings.first { it.blockedIds == setOf("def456") }.blockedIds)
	}

	@Test
	fun `avoidBlurryWallpapers defaults to false and round-trips`() = runTest {
		val repository = createRepository()
		assertFalse(repository.settings.first().avoidBlurryWallpapers)

		repository.save(AppSettings(searchQuery = "marker", avoidBlurryWallpapers = true))

		val loaded = repository.settings.first { it.searchQuery == "marker" }
		assertTrue(loaded.avoidBlurryWallpapers)
	}

	@Test
	fun `auto-update enabled defaults to false`() = runTest {
		val repository = createRepository()
		assertFalse(repository.loadAutoUpdateEnabled())
	}

	@Test
	fun `auto-update enabled survives a process restart`() = runTest {
		val file = tmpFolder.newFile("intent_prefs.preferences_pb")

		val firstScope = CoroutineScope(backgroundScope.coroutineContext + Job())
		val firstProcess = SettingsRepository(
			PreferenceDataStoreFactory.create(scope = firstScope, produceFile = { file }),
			FakeAppLogger()
		)

		firstProcess.setAutoUpdateEnabled(true)
		// Tearing down the DataStore's scope releases the file, simulating the OS killing the process.
		firstScope.coroutineContext.job.cancelAndJoin()

		val secondProcess = SettingsRepository(
			PreferenceDataStoreFactory.create(scope = backgroundScope, produceFile = { file }),
			FakeAppLogger()
		)

		assertTrue(secondProcess.loadAutoUpdateEnabled())
	}
}
