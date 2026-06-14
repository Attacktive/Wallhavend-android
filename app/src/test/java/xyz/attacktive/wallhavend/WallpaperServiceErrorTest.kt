package xyz.attacktive.wallhavend

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import xyz.attacktive.wallhavend.domain.model.AppError
import xyz.attacktive.wallhavend.domain.repository.ServiceStateRepository
import xyz.attacktive.wallhavend.domain.service.WallpaperService

@OptIn(ExperimentalCoroutinesApi::class)
class WallpaperServiceErrorTest {
	@Test
	fun `a newer error survives the older error's auto-clear timer`() = runTest {
		val stateRepository = ServiceStateRepository()
		val service = WallpaperService()
		service.stateRepository = stateRepository
		service.serviceScope = CoroutineScope(StandardTestDispatcher(testScheduler))

		service.postError(AppError.NoResults)
		testScheduler.advanceTimeBy(5_000)

		service.postError(AppError.UnsupportedFormat)
		testScheduler.advanceTimeBy(6_000)
		testScheduler.runCurrent()

		// The first error's 10s timer has now elapsed; it must not clear the newer error.
		assertEquals(AppError.UnsupportedFormat, stateRepository.state.value.error)

		testScheduler.advanceTimeBy(5_000)
		testScheduler.runCurrent()

		// The newer error's own 10s timer has elapsed, so it clears.
		assertNull(stateRepository.state.value.error)
	}
}
