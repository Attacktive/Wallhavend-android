package xyz.attacktive.wallhavend.domain.service

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex

@Singleton
class WallpaperMutationCoordinator @Inject constructor() {
	private val mutex = Mutex()

	suspend fun <T> serialize(block: suspend () -> T): T {
		mutex.lock()
		try {
			return block()
		} finally {
			mutex.unlock()
		}
	}
}
