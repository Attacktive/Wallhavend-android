package xyz.attacktive.wallhavend

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import xyz.attacktive.wallhavend.domain.service.intervalTicks

@OptIn(ExperimentalCoroutinesApi::class)
class IntervalTicksTest {
	@Test
	fun `shortening the interval cancels the in-flight wait and applies promptly`() = runTest {
		val intervals = MutableStateFlow(360)
		var ticks = 0

		backgroundScope.launch { intervalTicks(intervals).collect { ticks++ } }
		testScheduler.runCurrent()

		// Five of the six hours pass: the wait has not elapsed yet.
		testScheduler.advanceTimeBy(5 * 60 * 60_000L)
		testScheduler.runCurrent()
		assertEquals("no tick before the interval elapses", 0, ticks)

		// Shorten to one minute while the six-hour wait is still in flight.
		intervals.value = 1
		testScheduler.runCurrent()

		// One minute later — not the remaining hour of the old wait — a tick fires.
		testScheduler.advanceTimeBy(60_000L)
		testScheduler.runCurrent()
		assertEquals("the shortened interval applies promptly", 1, ticks)

		// And the loop keeps ticking at the new interval.
		testScheduler.advanceTimeBy(60_000L)
		testScheduler.runCurrent()
		assertEquals("the loop keeps ticking at the new interval", 2, ticks)
	}

	@Test
	fun `re-emitting the same interval does not restart the in-flight wait`() = runTest {
		// An unrelated settings write re-emits the same interval nine minutes into a ten-minute wait.
		val intervals = flow {
			emit(10)
			delay(9 * 60_000L)
			emit(10)
			delay(2 * 60_000L)
		}

		var ticks = 0

		backgroundScope.launch { intervalTicks(intervals).collect { ticks++ } }
		testScheduler.runCurrent()

		testScheduler.advanceTimeBy(10 * 60_000L)
		testScheduler.runCurrent()
		assertEquals("a duplicate interval must not restart the wait", 1, ticks)
	}
}
