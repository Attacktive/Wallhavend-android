package xyz.attacktive.wallhavend

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import xyz.attacktive.wallhavend.domain.model.AppSettings
import xyz.attacktive.wallhavend.domain.model.Purity
import xyz.attacktive.wallhavend.domain.model.WallhavenCategory
import xyz.attacktive.wallhavend.domain.model.WallpaperTarget
import xyz.attacktive.wallhavend.domain.repository.SettingsRepository

class SettingsRepositoryTest {
	@get:Rule
	val tmpFolder = TemporaryFolder()

	private fun createRepo(): SettingsRepository {
		val dataStore = PreferenceDataStoreFactory.create(
			produceFile = { tmpFolder.newFile("test_prefs.preferences_pb") }
		)

		return SettingsRepository(dataStore, FakeAppLogger())
	}

	@Test
	fun `defaults are correct`() = runTest {
		val repo = createRepo()
		val settings = repo.settings.first()

		assertEquals(60, settings.updateIntervalMinutes)
		assertTrue(settings.unmeteredOnly)
		assertEquals(10, settings.poolSize)
		assertEquals(WallpaperTarget.HOME, settings.wallpaperTarget)
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

		val loaded = repo.settings.first { it == modified }

		assertEquals(modified, loaded)
	}

	@Test
	fun `save and reload 10x16 aspect ratio`() = runTest {
		val repo = createRepo()
		val settings = AppSettings(aspectRatio = "10x16")
		repo.save(settings)

		val loaded = repo.settings.first { it.aspectRatio == "10x16" }
		assertEquals("10x16", loaded.aspectRatio)
	}
}
