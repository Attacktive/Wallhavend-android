package xyz.attacktive.wallhavend

import org.junit.Assert.assertEquals
import org.junit.Test
import android.view.Surface
import xyz.attacktive.wallhavend.domain.model.naturalDimensions

class NaturalDimensionsTest {
	@Test
	fun `an upright display is reported as measured`() {
		assertEquals(1080 to 2400, naturalDimensions(1080, 2400, Surface.ROTATION_0))
	}

	@Test
	fun `a quarter turn clockwise swaps the measurement back`() {
		assertEquals(1080 to 2400, naturalDimensions(2400, 1080, Surface.ROTATION_90))
	}

	@Test
	fun `a quarter turn counterclockwise swaps the measurement back`() {
		assertEquals(1080 to 2400, naturalDimensions(2400, 1080, Surface.ROTATION_270))
	}

	@Test
	fun `an upside-down display is reported as measured`() {
		assertEquals(1080 to 2400, naturalDimensions(1080, 2400, Surface.ROTATION_180))
	}

	@Test
	fun `a landscape-natural display stays landscape rather than being forced portrait`() {
		assertEquals(1280 to 800, naturalDimensions(1280, 800, Surface.ROTATION_0))
	}
}
