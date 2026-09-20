package xyz.attacktive.wallhavend

import org.junit.Assert.assertEquals
import org.junit.Test
import xyz.attacktive.wallhavend.domain.service.OneShotCompletion
import xyz.attacktive.wallhavend.domain.service.WallpaperService
import xyz.attacktive.wallhavend.domain.service.WallpaperServiceCommand
import xyz.attacktive.wallhavend.domain.service.oneShotCompletion
import xyz.attacktive.wallhavend.domain.service.wallpaperServiceCommand

class WallpaperServiceLifecycleTest {
	@Test
	fun `service actions distinguish an explicit start from sticky restoration`() {
		assertEquals(WallpaperServiceCommand.START, wallpaperServiceCommand(WallpaperService.ACTION_START))
		assertEquals(WallpaperServiceCommand.STOP, wallpaperServiceCommand(WallpaperService.ACTION_STOP))
		assertEquals(WallpaperServiceCommand.UPDATE_NOW, wallpaperServiceCommand(WallpaperService.ACTION_UPDATE_NOW))
		assertEquals(WallpaperServiceCommand.ROLL_NOW, wallpaperServiceCommand(WallpaperService.ACTION_ROLL_NOW))
		assertEquals(WallpaperServiceCommand.APPLY_PATH, wallpaperServiceCommand(WallpaperService.ACTION_APPLY_PATH))
		assertEquals(WallpaperServiceCommand.RESTORE, wallpaperServiceCommand(null))
		assertEquals(WallpaperServiceCommand.UNKNOWN, wallpaperServiceCommand("xyz.attacktive.wallhavend.UNKNOWN"))
	}

	@Test
	fun `one shot stops when automatic rotation is not active or persisted`() {
		assertEquals(
			OneShotCompletion.STOP_SERVICE,
			oneShotCompletion(timerRunning = false, autoUpdateEnabled = false)
		)
	}

	@Test
	fun `one shot keeps an active timer running`() {
		assertEquals(
			OneShotCompletion.KEEP_RUNNING,
			oneShotCompletion(timerRunning = true, autoUpdateEnabled = true)
		)
	}

	@Test
	fun `one shot restores a persisted timer after process recreation`() {
		assertEquals(
			OneShotCompletion.RESTORE_TIMER,
			oneShotCompletion(timerRunning = false, autoUpdateEnabled = true)
		)
	}
}
