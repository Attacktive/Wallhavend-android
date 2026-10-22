package xyz.attacktive.wallhavend.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import xyz.attacktive.wallhavend.domain.model.AppSettings
import xyz.attacktive.wallhavend.domain.model.WallpaperSource
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

	private val _savingProvider = MutableStateFlow(false)
	val savingProvider = _savingProvider.asStateFlow()

	fun toggleSource(source: WallpaperSource) = saveProvider { settingsRepository.toggleSource(source) }

	fun saveWallhavenKey(key: String) = saveProvider { settingsRepository.saveWallhavenKey(key) }

	fun savePexafyKey(key: String, enable: Boolean) = saveProvider { settingsRepository.savePexafyKey(key, enable) }

	private fun saveProvider(save: suspend () -> Unit) {
		if (_savingProvider.value) {
			return
		}

		_savingProvider.value = true
		viewModelScope.launch {
			try {
				save()
				rotationScheduler.reconcile()
			} catch (error: CancellationException) {
				throw error
			} catch (error: Exception) {
				_saveError.value = error.message ?: "Failed to save settings"
			} finally {
				_savingProvider.value = false
			}
		}
	}

	fun save(settings: AppSettings) {
		viewModelScope.launch {
			runCatching {
				settingsRepository.savePreferences(settings)
				rotationScheduler.reconcile()
			}
			.onFailure { error -> _saveError.value = error.message ?: "Failed to save settings" }
		}
	}

	fun clearSaveError() {
		_saveError.value = null
	}
}
