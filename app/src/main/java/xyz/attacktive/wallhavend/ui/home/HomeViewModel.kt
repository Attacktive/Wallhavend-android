package xyz.attacktive.wallhavend.ui.home

import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
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
import xyz.attacktive.wallhavend.domain.model.RotationMode
import xyz.attacktive.wallhavend.domain.model.ServiceState
import xyz.attacktive.wallhavend.domain.model.WallpaperIdentity
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

	val pinnedIds = settingsRepository.settings
		.map { it.pinnedIds }
		.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

	val rotationMode = settingsRepository.settings
		.map { it.rotationMode }
		.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RotationMode.FRESH_WIFI)

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

			reconcileAutoUpdate()
		}
	}

	/**
	 * A fresh process resets the in-memory running flag to false, so re-derive it from the persisted
	 * intent and revive the service when the user had auto-update on. The screen is foregrounded when
	 * this runs, so launching the foreground service is permitted.
	 */
	private suspend fun reconcileAutoUpdate() {
		val enabled = settingsRepository.settings.first().autoUpdateEnabled
		val alreadyRunning = stateRepository.state.value.isRunning
		if (enabled && !alreadyRunning) {
			stateRepository.update { it.copy(isRunning = true) }
			WallpaperService.start(context)
		}
	}

	fun startService() = WallpaperService.start(context)
	fun stopService() = WallpaperService.stop(context)
	fun updateNow() = WallpaperService.updateNow(context)

	fun applyFromPool(path: String) {
		WallpaperService.applyPath(context, path)
	}

	fun togglePin(identity: WallpaperIdentity) {
		viewModelScope.launch(Dispatchers.IO) {
			if (identity.matches(pinnedIds.value)) {
				settingsRepository.unpin(identity)
			} else {
				settingsRepository.pin(identity)
			}
		}
	}

	fun deleteFromPool(path: String) {
		viewModelScope.launch(Dispatchers.IO) {
			evictFromPool(path)
		}
	}

	fun blockFromPool(identity: WallpaperIdentity, path: String) {
		viewModelScope.launch(Dispatchers.IO) {
			val currentBeforeBlock = stateRepository.state.value.currentWallpaperPath
			settingsRepository.block(identity)
			evictFromPool(path)

			when (val replacement = blockReplacement(path, currentBeforeBlock, stateRepository.state.value.poolPaths)) {
				is BlockReplacement.ApplyFromPool -> WallpaperService.applyPath(context, replacement.path)
				BlockReplacement.Roll -> WallpaperService.rollNow(context)
				BlockReplacement.None -> Unit
			}
		}
	}

	private suspend fun evictFromPool(path: String) {
		val file = File(path)
		settingsRepository.unpin(WallpaperIdentity.parse(file.nameWithoutExtension))
		file.delete()

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
