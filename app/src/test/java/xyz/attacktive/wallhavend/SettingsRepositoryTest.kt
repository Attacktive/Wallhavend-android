package xyz.attacktive.wallhavend

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import xyz.attacktive.wallhavend.domain.model.AppSettings
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
		assertTrue(settings.wifiOnly)
		assertEquals(10, settings.poolSize)
		assertEquals(WallpaperTarget.HOME, settings.wallpaperTarget)
		assertEquals(setOf(Category.GENERAL), settings.categories)
		assertEquals(setOf(Purity.SFW), settings.purity)
		assertEquals(Sorting.RANDOM, settings.sorting)
		assertEquals(ToplistRange.ONE_MONTH, settings.toplistRange)
	}

	@Test
	fun `wifiOnly is not read from legacy unmetered_only key`() = runTest {
		val dataStore = PreferenceDataStoreFactory.create(
			scope = backgroundScope,
			produceFile = { tmpFolder.newFile("legacy_prefs.preferences_pb") }
		)

		dataStore.edit { prefs ->
			prefs[booleanPreferencesKey("unmetered_only")] = false
		}

		val repository = SettingsRepository(dataStore, FakeAppLogger())

		assertTrue(repository.settings.first().wifiOnly)
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
			wifiOnly = false,
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
	fun `avoidBlurryWallpapers defaults to false and round-trips`() = runTest {
		val repository = createRepository()
		assertFalse(repository.settings.first().avoidBlurryWallpapers)

		repository.save(AppSettings(searchQuery = "marker", avoidBlurryWallpapers = true))

		val loaded = repository.settings.first { it.searchQuery == "marker" }
		assertTrue(loaded.avoidBlurryWallpapers)
	}
}
