package xyz.attacktive.wallhavend.ui.settings

import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import xyz.attacktive.wallhavend.domain.model.AppSettings
import xyz.attacktive.wallhavend.domain.repository.SettingsRepository
import xyz.attacktive.wallhavend.domain.service.RotationScheduler

@HiltViewModel
class SettingsViewModel @Inject constructor(
	private val settingsRepository: SettingsRepository,
	private val rotationScheduler: RotationScheduler
): ViewModel() {
	val settings = settingsRepository.settings
		.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

	private val _saveError = MutableStateFlow<String?>(null)
	val saveError = _saveError.asStateFlow()

	fun save(settings: AppSettings) {
		viewModelScope.launch {
			runCatching {
				settingsRepository.save(settings)
				rotationScheduler.reconcile()
			}
				.onFailure { error -> _saveError.value = error.message ?: "Failed to save settings" }
		}
	}

	fun clearSaveError() {
		_saveError.value = null
	}
}
