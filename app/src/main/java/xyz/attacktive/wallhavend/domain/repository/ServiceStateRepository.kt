package xyz.attacktive.wallhavend.domain.repository

import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import androidx.annotation.VisibleForTesting
import xyz.attacktive.wallhavend.domain.model.AppError
import xyz.attacktive.wallhavend.domain.model.ServiceState

@Singleton
class ServiceStateRepository @Inject constructor() {
	private val _state = MutableStateFlow(ServiceState())
	val state = _state.asStateFlow()

	@VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
	internal var repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

	private var errorClearJob: Job? = null

	fun update(transform: (ServiceState) -> ServiceState) {
		_state.update(transform)
	}

	fun postError(error: AppError) {
		update { it.copy(error = error) }

		errorClearJob?.cancel()
		errorClearJob = repositoryScope.launch {
			delay(10_000.milliseconds)
			update { it.copy(error = null) }
		}
	}
}
