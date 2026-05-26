package xyz.attacktive.wallhavend.ui.home

import java.io.File
import javax.inject.Inject
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import xyz.attacktive.wallhavend.domain.model.ServiceState
import xyz.attacktive.wallhavend.domain.repository.ServiceStateRepository
import xyz.attacktive.wallhavend.domain.repository.SettingsRepository
import xyz.attacktive.wallhavend.domain.service.WallpaperFileManager
import xyz.attacktive.wallhavend.domain.service.WallpaperService

@HiltViewModel
class HomeViewModel @Inject constructor(
	private val stateRepository: ServiceStateRepository,
	private val settingsRepository: SettingsRepository,
	private val fileManager: WallpaperFileManager,
	@param:ApplicationContext private val context: Context
): ViewModel() {
	val serviceState: StateFlow<ServiceState> = stateRepository.state
		.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ServiceState())

	init {
		viewModelScope.launch(Dispatchers.IO) {
			if (stateRepository.state.value.poolPaths.isEmpty()) {
				val (lastUpdatedMs, currentPath, previousPath) = settingsRepository.loadServiceState()
				val paths = fileManager.listAll().map { it.absolutePath }

				stateRepository.update {
					it.copy(
						poolPaths = paths,
						lastUpdatedMs = lastUpdatedMs,
						currentWallpaperPath = currentPath,
						previousWallpaperPath = previousPath
					)
				}
			}
		}
	}

	fun startService() = WallpaperService.start(context)
	fun stopService() = WallpaperService.stop(context)
	fun updateNow() = WallpaperService.updateNow(context)

	fun applyFromPool(path: String) {
		WallpaperService.applyPath(context, path)
	}

	fun deleteFromPool(path: String) {
		viewModelScope.launch(Dispatchers.IO) {
			File(path).delete()

			val state = stateRepository.state.value
			val newPaths = state.poolPaths - path
			val newCurrent = if (state.currentWallpaperPath == path) newPaths.firstOrNull() else state.currentWallpaperPath
			val newPrev = if (state.previousWallpaperPath == path) newPaths.getOrNull(1) else state.previousWallpaperPath

			stateRepository.update {
				it.copy(
					poolPaths = newPaths,
					currentWallpaperPath = newCurrent,
					previousWallpaperPath = newPrev
				)
			}

			settingsRepository.saveServiceState(
				state.lastUpdatedMs ?: System.currentTimeMillis(),
				newCurrent,
				newPrev
			)
		}
	}
}
