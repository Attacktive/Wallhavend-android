package xyz.attacktive.wallhavend

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.attacktive.wallhavend.domain.model.RotationMode
import xyz.attacktive.wallhavend.domain.service.nextInCycle
import xyz.attacktive.wallhavend.domain.service.shouldDownload

class RotationDecisionsTest {
	@Test
	fun `offline never downloads, even when forced`() {
		RotationMode.entries.forEach { mode ->
			assertFalse(shouldDownload(mode, online = false, onWifi = false, forceDownload = false))
			assertFalse(shouldDownload(mode, online = false, onWifi = true, forceDownload = true))
		}
	}

	@Test
	fun `fresh-any downloads whenever online`() {
		assertTrue(shouldDownload(RotationMode.FRESH_ANY, online = true, onWifi = false, forceDownload = false))
		assertTrue(shouldDownload(RotationMode.FRESH_ANY, online = true, onWifi = true, forceDownload = false))
	}

	@Test
	fun `fresh-wifi downloads only on wifi`() {
		assertTrue(shouldDownload(RotationMode.FRESH_WIFI, online = true, onWifi = true, forceDownload = false))
		assertFalse(shouldDownload(RotationMode.FRESH_WIFI, online = true, onWifi = false, forceDownload = false))
	}

	@Test
	fun `force overrides the wifi and pinned rules when online`() {
		assertTrue(shouldDownload(RotationMode.FRESH_WIFI, online = true, onWifi = false, forceDownload = true))
		assertTrue(shouldDownload(RotationMode.PINNED_ONLY, online = true, onWifi = false, forceDownload = true))
	}

	@Test
	fun `pinned-only never auto-downloads`() {
		assertFalse(shouldDownload(RotationMode.PINNED_ONLY, online = true, onWifi = true, forceDownload = false))
	}

	@Test
	fun `nextInCycle returns null for an empty pool`() {
		assertNull(nextInCycle(emptyList(), current = null))
		assertNull(nextInCycle(emptyList(), current = "/a.jpg"))
	}

	@Test
	fun `nextInCycle starts at the first entry when nothing is current`() {
		assertEquals("/a.jpg", nextInCycle(listOf("/a.jpg", "/b.jpg"), current = null))
	}

	@Test
	fun `nextInCycle advances to the following entry`() {
		assertEquals("/b.jpg", nextInCycle(listOf("/a.jpg", "/b.jpg", "/c.jpg"), current = "/a.jpg"))
	}

	@Test
	fun `nextInCycle wraps around past the last entry`() {
		assertEquals("/a.jpg", nextInCycle(listOf("/a.jpg", "/b.jpg"), current = "/b.jpg"))
	}

	@Test
	fun `nextInCycle starts over when the current path is not in the pool`() {
		assertEquals("/a.jpg", nextInCycle(listOf("/a.jpg", "/b.jpg"), current = "/gone.jpg"))
	}
}
