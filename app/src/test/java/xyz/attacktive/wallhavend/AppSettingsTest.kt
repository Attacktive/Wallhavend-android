package xyz.attacktive.wallhavend

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import xyz.attacktive.wallhavend.domain.model.POOL_SIZE_OPTIONS

class AppSettingsTest {
	@Test
	fun `pool size options never offer 0 so a fetched wallpaper always survives to be pinned`() {
		assertFalse(0 in POOL_SIZE_OPTIONS)
		assertEquals(1, POOL_SIZE_OPTIONS.min())
	}
}
