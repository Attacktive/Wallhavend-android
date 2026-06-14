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

@HiltViewModel
class SettingsViewModel @Inject constructor(private val settingsRepository: SettingsRepository): ViewModel() {
	val settings = settingsRepository.settings
		.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

	private val _saveError = MutableStateFlow<String?>(null)
	val saveError = _saveError.asStateFlow()

	fun save(settings: AppSettings) {
		viewModelScope.launch {
			runCatching { settingsRepository.save(settings) }
				.onFailure { e -> _saveError.value = e.message ?: "Failed to save settings" }
		}
	}

	fun clearSaveError() {
		_saveError.value = null
	}
}
