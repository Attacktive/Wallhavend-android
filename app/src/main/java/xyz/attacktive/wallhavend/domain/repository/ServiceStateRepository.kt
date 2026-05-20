package xyz.attacktive.wallhavend.domain.repository

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import xyz.attacktive.wallhavend.domain.model.ServiceState

@Singleton
class ServiceStateRepository @Inject constructor() {
	private val _state = MutableStateFlow(ServiceState())
	val state: StateFlow<ServiceState> = _state.asStateFlow()

	fun update(transform: (ServiceState) -> ServiceState) {
		_state.update(transform)
	}
}
