package xyz.attacktive.wallhavend

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import xyz.attacktive.wallhavend.domain.service.OneShotTracker
import xyz.attacktive.wallhavend.domain.service.WallpaperService
import xyz.attacktive.wallhavend.domain.service.WallpaperServiceCommand
import xyz.attacktive.wallhavend.domain.service.wallpaperServiceCommand

class WallpaperServiceLifecycleTest {
	@Test
	fun `service actions contain manual commands only`() {
		assertEquals(WallpaperServiceCommand.UPDATE_NOW, wallpaperServiceCommand(WallpaperService.ACTION_UPDATE_NOW))
		assertEquals(WallpaperServiceCommand.ROLL_NOW, wallpaperServiceCommand(WallpaperService.ACTION_ROLL_NOW))
		assertEquals(WallpaperServiceCommand.APPLY_PATH, wallpaperServiceCommand(WallpaperService.ACTION_APPLY_PATH))
		assertEquals(WallpaperServiceCommand.UNKNOWN, wallpaperServiceCommand(null))
		assertEquals(WallpaperServiceCommand.UNKNOWN, wallpaperServiceCommand("xyz.attacktive.wallhavend.UNKNOWN"))
	}

	@Test
	fun `one shot tracker waits for every in-flight command before stopping`() {
		val tracker = OneShotTracker()
		tracker.start(1)
		tracker.start(2)

		assertNull(tracker.finish())
		assertEquals(2, tracker.finish())
	}
}
