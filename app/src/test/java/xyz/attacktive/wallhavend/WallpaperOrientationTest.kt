package xyz.attacktive.wallhavend

import org.junit.Assert.assertEquals
import org.junit.Test
import xyz.attacktive.wallhavend.domain.model.ScreenInfo
import xyz.attacktive.wallhavend.domain.model.WallpaperOrientation
import xyz.attacktive.wallhavend.domain.model.forWallpaperOrientation

class WallpaperOrientationTest {
	private val portraitTablet = ScreenInfo("10x16", 1600, 2560)
	private val landscapeTablet = ScreenInfo("16x10", 2560, 1600)

	@Test
	fun `automatic keeps the natural orientation`() {
		assertEquals(portraitTablet, portraitTablet.forWallpaperOrientation(WallpaperOrientation.AUTOMATIC))
	}

	@Test
	fun `landscape swaps a portrait-natural tablet`() {
		assertEquals(landscapeTablet, portraitTablet.forWallpaperOrientation(WallpaperOrientation.LANDSCAPE))
	}

	@Test
	fun `portrait swaps a landscape-natural tablet`() {
		assertEquals(portraitTablet, landscapeTablet.forWallpaperOrientation(WallpaperOrientation.PORTRAIT))
	}

	@Test
	fun `an override matching the natural orientation changes nothing`() {
		assertEquals(portraitTablet, portraitTablet.forWallpaperOrientation(WallpaperOrientation.PORTRAIT))
		assertEquals(landscapeTablet, landscapeTablet.forWallpaperOrientation(WallpaperOrientation.LANDSCAPE))
	}
}
