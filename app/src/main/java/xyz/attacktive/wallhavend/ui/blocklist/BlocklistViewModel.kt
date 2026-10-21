package xyz.attacktive.wallhavend.ui.blocklist

import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import xyz.attacktive.wallhavend.domain.model.WallpaperIdentity
import xyz.attacktive.wallhavend.domain.repository.SettingsRepository
import xyz.attacktive.wallhavend.domain.model.WallpaperMetadata

@HiltViewModel
class BlocklistViewModel @Inject constructor(private val settingsRepository: SettingsRepository): ViewModel() {
	val blockedWallpapers = settingsRepository.settings
		.map { settings ->
			settings.blockedIds
				.map { WallpaperIdentity.parse(it) }
				.sortedBy { it.id }
		}
		.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

	val wallpaperMetadata = settingsRepository.settings
		.map { it.wallpaperMetadata }
		.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

	fun unblock(identity: WallpaperIdentity) {
		viewModelScope.launch {
			settingsRepository.unblock(identity)
		}
	}
}
