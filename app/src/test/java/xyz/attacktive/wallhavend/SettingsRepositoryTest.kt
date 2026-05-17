package xyz.attacktive.wallhavend

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import xyz.attacktive.wallhavend.domain.model.AppSettings
import xyz.attacktive.wallhavend.domain.model.Purity
import xyz.attacktive.wallhavend.domain.model.WallhavenCategory
import xyz.attacktive.wallhavend.domain.model.WallpaperTarget
import xyz.attacktive.wallhavend.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SettingsRepositoryTest {

	@get:Rule
	val tmpFolder = TemporaryFolder()

	private fun createRepo(): SettingsRepository {
		val dataStore = PreferenceDataStoreFactory.create(
			produceFile = { tmpFolder.newFile("test_prefs.preferences_pb") }
		)
		return SettingsRepository(dataStore)
	}

	@Test
	fun `defaults are correct`() = runTest {
		val repo = createRepo()
		val settings = repo.settings.first()
		assertEquals(60, settings.updateIntervalMinutes)
		assertTrue(settings.unmeteredOnly)
		assertEquals(10, settings.poolSize)
		assertEquals(WallpaperTarget.BOTH, settings.wallpaperTarget)
		assertEquals(setOf(WallhavenCategory.GENERAL), settings.categories)
		assertEquals(setOf(Purity.SFW), settings.purity)
	}

	@Test
	fun `save and reload settings round-trips correctly`() = runTest {
		val repo = createRepo()
		val modified = AppSettings(
			searchQuery = "mountains",
			categories = setOf(WallhavenCategory.GENERAL, WallhavenCategory.ANIME),
			purity = setOf(Purity.SFW, Purity.SKETCHY),
			aspectRatio = "16x9",
			updateIntervalMinutes = 30,
			wallpaperTarget = WallpaperTarget.HOME,
			unmeteredOnly = false,
			poolSize = 25,
			apiKey = "secret",
			autoStartOnBoot = true
		)
		repo.save(modified)
		val loaded = repo.settings.first()
		assertEquals(modified, loaded)
	}
}
