package xyz.attacktive.wallhavend

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.attacktive.wallhavend.domain.service.WallpaperMutationCoordinator

@OptIn(ExperimentalCoroutinesApi::class)
class WallpaperMutationCoordinatorTest {
	@Test
	fun `a second wallpaper mutation waits for the first one to finish`() = runTest {
		val coordinator = WallpaperMutationCoordinator()
		val firstEntered = CompletableDeferred<Unit>()
		val releaseFirst = CompletableDeferred<Unit>()
		val secondEntered = CompletableDeferred<Unit>()
		var activeMutations = 0
		var maximumActiveMutations = 0

		launch {
			coordinator.serialize {
				activeMutations++
				maximumActiveMutations = maxOf(maximumActiveMutations, activeMutations)
				firstEntered.complete(Unit)
				releaseFirst.await()
				activeMutations--
			}
		}

		firstEntered.await()

		launch {
			coordinator.serialize {
				activeMutations++
				maximumActiveMutations = maxOf(maximumActiveMutations, activeMutations)
				secondEntered.complete(Unit)
				activeMutations--
			}
		}

		runCurrent()

		assertFalse(secondEntered.isCompleted)
		assertEquals(1, activeMutations)

		releaseFirst.complete(Unit)
		advanceUntilIdle()

		assertTrue(secondEntered.isCompleted)
		assertEquals(1, maximumActiveMutations)
		assertEquals(0, activeMutations)
	}
}
