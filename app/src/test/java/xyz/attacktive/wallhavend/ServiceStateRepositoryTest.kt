package xyz.attacktive.wallhavend

import xyz.attacktive.wallhavend.domain.model.AppError
import xyz.attacktive.wallhavend.domain.model.ServiceState
import xyz.attacktive.wallhavend.domain.repository.ServiceStateRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ServiceStateRepositoryTest {

	@Test
	fun `initial state has sensible defaults`() {
		val repo = ServiceStateRepository()
		val state = repo.state.value
		assertFalse(state.isRunning)
		assertNull(state.error)
		assertNull(state.lastUpdatedMs)
		assertTrue(state.poolPaths.isEmpty())
	}

	@Test
	fun `update transforms state correctly`() {
		val repo = ServiceStateRepository()
		repo.update { it.copy(isRunning = true, lastUpdatedMs = 12345L) }
		assertTrue(repo.state.value.isRunning)
		assertEquals(12345L, repo.state.value.lastUpdatedMs)
	}

	@Test
	fun `update preserves unmodified fields`() {
		val repo = ServiceStateRepository()
		repo.update { it.copy(isRunning = true) }
		repo.update { it.copy(error = AppError.NoResults) }
		assertTrue(repo.state.value.isRunning)
		assertEquals(AppError.NoResults, repo.state.value.error)
	}
}
