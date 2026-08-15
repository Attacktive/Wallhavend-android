package xyz.attacktive.wallhavend

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.attacktive.wallhavend.domain.model.WallpaperIdentity
import xyz.attacktive.wallhavend.domain.model.WallpaperSource

class WallpaperIdentityTest {
	@Test
	fun `qualified prefixes the id with its source`() {
		val identity = WallpaperIdentity(WallpaperSource.WALLHAVEN, "abc123")

		assertEquals("wallhaven_abc123", identity.qualified)
	}

	@Test
	fun `a file name is the qualified id plus the extension`() {
		val identity = WallpaperIdentity(WallpaperSource.WALLHAVEN, "abc123")

		assertEquals("wallhaven_abc123.jpg", identity.toFileName("jpg"))
	}

	@Test
	fun `parse round-trips the qualified form`() {
		val identity = WallpaperIdentity(WallpaperSource.WALLHAVEN, "abc123")

		assertEquals(identity, WallpaperIdentity.parse(identity.qualified))
	}

	@Test
	fun `parse reads a bare id as Wallhaven, which is what carries older installs over`() {
		val identity = WallpaperIdentity.parse("abc123")

		assertEquals(WallpaperSource.WALLHAVEN, identity.source)
		assertEquals("abc123", identity.id)
	}

	@Test
	fun `parse keeps an unrecognised prefix as part of the id rather than inventing a source`() {
		val identity = WallpaperIdentity.parse("someday_abc123")

		assertEquals(WallpaperSource.WALLHAVEN, identity.source)
		assertEquals("someday_abc123", identity.id)
	}

	@Test
	fun `parse ignores a source prefix with nothing after it`() {
		val identity = WallpaperIdentity.parse("wallhaven_")

		assertEquals("wallhaven_", identity.id)
	}

	@Test
	fun `a legacy file name and its qualified form are the same wallpaper`() {
		assertEquals(WallpaperIdentity.parse("abc123"), WallpaperIdentity.parse("wallhaven_abc123"))
	}

	@Test
	fun `matches accepts both the qualified and the legacy bare form`() {
		val identity = WallpaperIdentity(WallpaperSource.WALLHAVEN, "abc123")

		assertTrue(identity.matches(setOf("wallhaven_abc123")))
		assertTrue(identity.matches(setOf("abc123")))
		assertFalse(identity.matches(setOf("wallhaven_def456", "def456")))
	}

	@Test
	fun `persistedForms covers every spelling a removal has to clear`() {
		val identity = WallpaperIdentity(WallpaperSource.WALLHAVEN, "abc123")

		assertEquals(setOf("wallhaven_abc123", "abc123"), identity.persistedForms)
	}

	@Test
	fun `a Wallhaven identity points at its own page and thumbnail`() {
		val identity = WallpaperIdentity(WallpaperSource.WALLHAVEN, "abc123")

		assertEquals("https://wallhaven.cc/w/abc123", identity.pageUrl)
		assertEquals("https://th.wallhaven.cc/lg/ab/abc123.jpg", identity.thumbnailUrl)
	}
}
