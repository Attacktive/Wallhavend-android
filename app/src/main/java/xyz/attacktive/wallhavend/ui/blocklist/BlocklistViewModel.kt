package xyz.attacktive.wallhavend.ui.blocklist

import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import xyz.attacktive.wallhavend.domain.repository.SettingsRepository

@HiltViewModel
class BlocklistViewModel @Inject constructor(private val settingsRepository: SettingsRepository): ViewModel() {
	val blockedIds = settingsRepository.settings
		.map { settings -> settings.blockedIds.sorted() }
		.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

	fun unblock(id: String) {
		viewModelScope.launch {
			settingsRepository.unblock(id)
		}
	}
}
