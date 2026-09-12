package xyz.attacktive.wallhavend.ui.picker

import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import xyz.attacktive.wallhavend.domain.service.WallpaperFileManager

@HiltViewModel
class WallpaperPickerViewModel @Inject constructor(private val fileManager: WallpaperFileManager): ViewModel() {
	private val _wallpapers = MutableStateFlow<List<File>>(emptyList())
	val wallpapers: StateFlow<List<File>> = _wallpapers.asStateFlow()

	init {
		refresh()
	}

	fun refresh() = viewModelScope.launch(Dispatchers.IO) {
		_wallpapers.value = fileManager.listAll()
	}
}
