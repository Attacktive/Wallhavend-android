package xyz.attacktive.wallhavend.ui.picker

import javax.inject.Inject
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import xyz.attacktive.wallhavend.domain.service.WallpaperFileManager

@HiltViewModel
class WallpaperPickerViewModel @Inject constructor(fileManager: WallpaperFileManager): ViewModel() {
	val wallpapers = fileManager.listAll()
}
