package xyz.attacktive.wallhavend

import org.junit.Assert.assertEquals
import org.junit.Test
import xyz.attacktive.wallhavend.ui.settings.toggleKeepingOne

class ToggleKeepingOneTest {
	@Test
	fun `adds an item that is not selected yet`() {
		assertEquals(setOf("a", "b"), setOf("a").toggleKeepingOne("b"))
	}

	@Test
	fun `removes a selected item when others remain`() {
		assertEquals(setOf("a"), setOf("a", "b").toggleKeepingOne("b"))
	}

	@Test
	fun `keeps the last selected item instead of emptying the set`() {
		assertEquals(setOf("a"), setOf("a").toggleKeepingOne("a"))
	}
}
