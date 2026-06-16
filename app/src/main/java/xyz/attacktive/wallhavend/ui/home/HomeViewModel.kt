package xyz.attacktive.wallhavend.ui.home

import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import xyz.attacktive.wallhavend.R
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
	val serviceState = stateRepository.state
		.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ServiceState())

	private val _saveMessage = MutableSharedFlow<String>(extraBufferCapacity = 1)
	val saveMessage = _saveMessage.asSharedFlow()

	init {
		viewModelScope.launch(Dispatchers.IO) {
			if (stateRepository.state.value.poolPaths.isEmpty()) {
				val (lastUpdatedMs, currentPath, previousPath) = settingsRepository.loadServiceState()

				val paths = fileManager.listAll()
					.map { it.absolutePath }

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
			evictFromPool(path)
		}
	}

	fun blockFromPool(id: String, path: String) {
		viewModelScope.launch(Dispatchers.IO) {
			settingsRepository.block(id)
			evictFromPool(path)
		}
	}

	private suspend fun evictFromPool(path: String) {
		File(path).delete()

		val state = stateRepository.state.value
		val newPaths = state.poolPaths - path

		val newCurrent = if (state.currentWallpaperPath == path) {
			newPaths.firstOrNull()
		} else {
			state.currentWallpaperPath
		}

		val newPrev = if (state.previousWallpaperPath == path) {
			newPaths.getOrNull(1)
		} else {
			state.previousWallpaperPath
		}

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

	fun saveToPictures(path: String) {
		viewModelScope.launch(Dispatchers.IO) {
			runCatching {
				val file = File(path)
				val mimeType = when (file.extension.lowercase()) {
					"jpg", "jpeg" -> "image/jpeg"
					"png" -> "image/png"
					else -> "image/*"
				}

				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
					val values = ContentValues().apply {
						put(MediaStore.Images.Media.DISPLAY_NAME, file.name)
						put(MediaStore.Images.Media.MIME_TYPE, mimeType)
						put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Wallhavend")
					}

					val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
						?: error("Failed to create MediaStore entry")

					context.contentResolver.openOutputStream(uri)
						?.use { outputStream -> file.inputStream().use { inputStream -> inputStream.copyTo(outputStream) } }
						?: error("Failed to open output stream")
				} else {
					val destinationDirectory = File(
						Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
						"Wallhavend"
					)

					destinationDirectory.mkdirs()
					val destinationFile = File(destinationDirectory, file.name)
					file.copyTo(destinationFile, overwrite = true)
					MediaScannerConnection.scanFile(context, arrayOf(destinationFile.absolutePath), arrayOf(mimeType), null)
				}
			}
			.onSuccess { _saveMessage.emit(context.getString(R.string.home_msg_saved_to_pictures)) }
			.onFailure { exception -> _saveMessage.emit(context.getString(R.string.home_msg_save_failed, exception.message)) }
		}
	}
}
