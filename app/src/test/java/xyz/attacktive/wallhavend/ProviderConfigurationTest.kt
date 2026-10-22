package xyz.attacktive.wallhavend

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.attacktive.wallhavend.domain.model.AppSettings
import xyz.attacktive.wallhavend.domain.model.WallpaperSource
import xyz.attacktive.wallhavend.domain.model.configuredSources
import xyz.attacktive.wallhavend.domain.model.requiresPexafySetup
import xyz.attacktive.wallhavend.domain.model.withPexafyKey

class ProviderConfigurationTest {
	@Test
	fun `empty and whitespace-only keys cannot enable Pexafy`() {
		listOf("", " ", "\t\n").forEach { key ->
			assertThrows(IllegalArgumentException::class.java) { AppSettings().withPexafyKey(key, enable = true) }
		}
	}

	@Test
	fun `saving a key does not enable Pexafy without an explicit activation`() {
		val saved = AppSettings().withPexafyKey(" key ", enable = false)
		assertEquals("key", saved.pexafyApiKey)
		assertEquals(setOf(WallpaperSource.WALLHAVEN), saved.configuredSources)

		val enabled = saved.withPexafyKey(saved.pexafyApiKey, enable = true)
		assertEquals(setOf(WallpaperSource.WALLHAVEN, WallpaperSource.PEXAFY), enabled.configuredSources)
	}

	@Test
	fun `removing a key preserves every other selected source`() {
		val settings = AppSettings(enabledSources = WallpaperSource.entries.toSet(), pexafyApiKey = "key")
		val removed = settings.withPexafyKey(" ", enable = false)
		assertEquals(setOf(WallpaperSource.WALLHAVEN, WallpaperSource.OPENVERSE), removed.enabledSources)
		assertEquals("", removed.pexafyApiKey)
	}

	@Test
	fun `removing the sole source is rejected without silently adding Wallhaven`() {
		val settings = AppSettings(enabledSources = setOf(WallpaperSource.PEXAFY), pexafyApiKey = "key")
		assertThrows(IllegalArgumentException::class.java) { settings.withPexafyKey("", enable = false) }
		assertEquals(setOf(WallpaperSource.PEXAFY), settings.enabledSources)
		assertEquals("key", settings.pexafyApiKey)
	}

	@Test
	fun `a restored keyless Pexafy-only selection is pending setup rather than Wallhaven`() {
		val restored = AppSettings(enabledSources = setOf(WallpaperSource.PEXAFY), pexafyApiKey = " ")
		assertTrue(restored.configuredSources.isEmpty())
		assertTrue(restored.requiresPexafySetup)
		assertFalse(WallpaperSource.WALLHAVEN in restored.enabledSources)
	}

	@Test
	fun `anonymous providers remain configured without any credentials`() {
		val settings = AppSettings(enabledSources = setOf(WallpaperSource.WALLHAVEN, WallpaperSource.OPENVERSE))
		assertEquals(settings.enabledSources, settings.configuredSources)
		assertFalse(settings.requiresPexafySetup)
	}
}
