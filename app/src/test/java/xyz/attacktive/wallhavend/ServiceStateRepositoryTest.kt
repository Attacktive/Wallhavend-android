package xyz.attacktive.wallhavend

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.attacktive.wallhavend.domain.model.AppError
import xyz.attacktive.wallhavend.domain.repository.ServiceStateRepository

class ServiceStateRepositoryTest {
	@Test
	fun `initial state has sensible defaults`() {
		val repository = ServiceStateRepository()
		val state = repository.state.value
		assertFalse(state.isRunning)
		assertNull(state.error)
		assertNull(state.lastUpdatedMs)
		assertTrue(state.poolPaths.isEmpty())
	}

	@Test
	fun `update transforms state correctly`() {
		val repository = ServiceStateRepository()
		repository.update { it.copy(isRunning = true, lastUpdatedMs = 12345L) }
		assertTrue(repository.state.value.isRunning)
		assertEquals(12345L, repository.state.value.lastUpdatedMs)
	}

	@Test
	fun `update preserves unmodified fields`() {
		val repository = ServiceStateRepository()
		repository.update { it.copy(isRunning = true) }
		repository.update { it.copy(error = AppError.NoResults) }
		assertTrue(repository.state.value.isRunning)
		assertEquals(AppError.NoResults, repository.state.value.error)
	}
}
