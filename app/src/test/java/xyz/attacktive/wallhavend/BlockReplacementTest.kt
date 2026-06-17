package xyz.attacktive.wallhavend

import org.junit.Assert.assertEquals
import org.junit.Test
import xyz.attacktive.wallhavend.ui.home.BlockReplacement
import xyz.attacktive.wallhavend.ui.home.blockReplacement

class BlockReplacementTest {
	@Test
	fun `does nothing when the blocked wallpaper is not the current one`() {
		val replacement = blockReplacement(
			blockedPath = "/pool/b.jpg",
			currentPath = "/pool/a.jpg",
			remainingPool = listOf("/pool/a.jpg", "/pool/c.jpg")
		)

		assertEquals(BlockReplacement.None, replacement)
	}

	@Test
	fun `does nothing when there is no current wallpaper`() {
		val replacement = blockReplacement(
			blockedPath = "/pool/a.jpg",
			currentPath = null,
			remainingPool = listOf("/pool/b.jpg")
		)

		assertEquals(BlockReplacement.None, replacement)
	}

	@Test
	fun `applies the most recent remaining pool item when the current wallpaper is blocked`() {
		val replacement = blockReplacement(
			blockedPath = "/pool/a.jpg",
			currentPath = "/pool/a.jpg",
			remainingPool = listOf("/pool/b.jpg", "/pool/c.jpg")
		)

		assertEquals(BlockReplacement.ApplyFromPool("/pool/b.jpg"), replacement)
	}

	@Test
	fun `rolls when the current wallpaper is blocked and the pool is now empty`() {
		val replacement = blockReplacement(
			blockedPath = "/pool/a.jpg",
			currentPath = "/pool/a.jpg",
			remainingPool = emptyList()
		)

		assertEquals(BlockReplacement.Roll, replacement)
	}
}
