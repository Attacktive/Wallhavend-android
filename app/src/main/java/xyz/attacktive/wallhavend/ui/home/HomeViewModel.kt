package xyz.attacktive.wallhavend.ui.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import xyz.attacktive.wallhavend.domain.model.ServiceState
import xyz.attacktive.wallhavend.domain.repository.ServiceStateRepository
import xyz.attacktive.wallhavend.domain.service.WallpaperService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
	private val stateRepository: ServiceStateRepository,
	@ApplicationContext private val context: Context
) : ViewModel() {

	val serviceState: StateFlow<ServiceState> = stateRepository.state
		.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ServiceState())

	fun startService() = WallpaperService.start(context)
	fun stopService() = WallpaperService.stop(context)
	fun updateNow() = WallpaperService.updateNow(context)
	fun previous() = WallpaperService.previous(context)

	fun applyFromPool(path: String) {
		WallpaperService.applyPath(context, path)
	}
}
