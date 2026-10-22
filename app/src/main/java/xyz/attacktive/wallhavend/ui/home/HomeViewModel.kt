package xyz.attacktive.wallhavend.ui.home

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import xyz.attacktive.wallhavend.R
import xyz.attacktive.wallhavend.domain.model.AppError
import xyz.attacktive.wallhavend.domain.model.RotationMode
import xyz.attacktive.wallhavend.domain.model.ServiceState
import xyz.attacktive.wallhavend.domain.model.WallpaperIdentity
import xyz.attacktive.wallhavend.domain.model.WallpaperSource
import xyz.attacktive.wallhavend.domain.model.providerSource
import xyz.attacktive.wallhavend.domain.model.requiresPexafySetup
import xyz.attacktive.wallhavend.domain.repository.ServiceStateRepository
import xyz.attacktive.wallhavend.domain.repository.SettingsRepository
import xyz.attacktive.wallhavend.domain.service.RotationScheduler
import xyz.attacktive.wallhavend.domain.service.WallpaperFileManager
import xyz.attacktive.wallhavend.domain.service.WallpaperMutationCoordinator
import xyz.attacktive.wallhavend.domain.service.WallpaperRotationExecutor
import xyz.attacktive.wallhavend.domain.service.WallpaperService

@HiltViewModel
class HomeViewModel @Inject constructor(
	private val stateRepository: ServiceStateRepository,
	private val settingsRepository: SettingsRepository,
	private val fileManager: WallpaperFileManager,
	private val wallpaperMutationCoordinator: WallpaperMutationCoordinator,
	private val wallpaperRotationExecutor: WallpaperRotationExecutor,
	private val rotationScheduler: RotationScheduler,
	@param:ApplicationContext private val context: Context
): ViewModel() {
	val serviceState = combine(stateRepository.state, settingsRepository.settings) { state, settings ->
		state.copy(
			error = state.error?.takeIf { it.providerSource == null },
			providerFailures = state.providerFailures.filterValues { it.isCurrent(settings) }
		)
	}
	.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ServiceState())

	val configurationError = settingsRepository.settings
		.map {
			if (it.requiresPexafySetup) {
				AppError.MissingApiKey(WallpaperSource.PEXAFY)
			} else {
				null
			}
		}
		.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

	val autoUpdateEnabled = settingsRepository.settings
		.map { it.autoUpdateEnabled }
		.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

	val pinnedIds = settingsRepository.settings
		.map { it.pinnedIds }
		.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

	val rotationMode = settingsRepository.settings
		.map { it.rotationMode }
		.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RotationMode.FRESH_WIFI)

	val wallpaperMetadata = settingsRepository.settings
		.map { it.wallpaperMetadata }
		.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

	private val _saveMessage = MutableSharedFlow<String>(extraBufferCapacity = 1)
	val saveMessage = _saveMessage.asSharedFlow()

	init {
		viewModelScope.launch(Dispatchers.IO) {
			wallpaperRotationExecutor.restoreState()
			rotationScheduler.reconcile()
		}
	}

	fun startService() {
		viewModelScope.launch(Dispatchers.IO) {
			if (rotationScheduler.start()) {
				WallpaperService.rollNow(context)
			}
		}
	}

	fun stopService() {
		viewModelScope.launch(Dispatchers.IO) {
			rotationScheduler.stop()
		}
	}

	fun updateNow() = WallpaperService.updateNow(context)

	fun applyFromPool(path: String) {
		WallpaperService.applyPath(context, path)
	}

	fun togglePin(identity: WallpaperIdentity) {
		viewModelScope.launch(Dispatchers.IO) {
			wallpaperMutationCoordinator.serialize {
				if (identity.matches(pinnedIds.value)) {
					settingsRepository.unpin(identity)
				} else {
					settingsRepository.pin(identity)
				}
			}
		}
	}

	fun deleteFromPool(path: String) {
		viewModelScope.launch(Dispatchers.IO) {
			wallpaperMutationCoordinator.serialize {
				evictFromPool(path)
				settingsRepository.forgetMetadata(WallpaperIdentity.parse(File(path).nameWithoutExtension))
			}
		}
	}

	fun blockFromPool(identity: WallpaperIdentity, path: String) {
		viewModelScope.launch(Dispatchers.IO) {
			val replacement = wallpaperMutationCoordinator.serialize {
				val currentBeforeBlock = stateRepository.state.value.currentWallpaperPath
				settingsRepository.block(identity)
				evictFromPool(path)

				blockReplacement(path, currentBeforeBlock, stateRepository.state.value.poolPaths)
			}

			when (replacement) {
				is BlockReplacement.ApplyFromPool -> WallpaperService.applyPath(context, replacement.path)
				BlockReplacement.Roll -> WallpaperService.rollNow(context)
				BlockReplacement.None -> Unit
			}
		}
	}

	/**
	 * Everything the wallpaper occupies on disk goes, not just the path the caller happened to hold.
	 * An install that predates source-qualified filenames can carry the same wallpaper twice, and the local pool is cycled by listing files rather than by consulting the blocklist, so leaving a copy behind would keep serving a wallpaper the user just blocked.
	 */
	private suspend fun evictFromPool(path: String) {
		val identity = WallpaperIdentity.parse(File(path).nameWithoutExtension)
		settingsRepository.unpin(identity)

		val evicted = fileManager.listAll()
			.filter { WallpaperIdentity.parse(it.nameWithoutExtension) == identity }
			.map { it.absolutePath }
			.toSet() + path

		evicted.forEach { File(it).delete() }

		val state = stateRepository.state.value
		val newPaths = state.poolPaths - evicted

		val currentPath = state.currentWallpaperPath
		val newCurrent = if (currentPath != null && currentPath in evicted) {
			newPaths.firstOrNull()
		} else {
			currentPath
		}

		val previousPath = state.previousWallpaperPath
		val newPrev = if (previousPath != null && previousPath in evicted) {
			newPaths.getOrNull(1)
		} else {
			previousPath
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

				val identity = WallpaperIdentity.parse(file.nameWithoutExtension)
				val metadata = settingsRepository.settings.first().wallpaperMetadata[identity.qualified]
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
					MediaStoreExporter(context.contentResolver).save(file, mimeType, metadata)
				} else {
					val destinationDirectory = File(
						Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
						"Wallhavend"
					)

					destinationDirectory.mkdirs()
					val destinationFile = File(destinationDirectory, file.name)
					file.copyTo(destinationFile, overwrite = true)
					metadata?.galleryDescription?.let { description ->
						embedExifDescription(destinationFile, description)
					}

					MediaScannerConnection.scanFile(context, arrayOf(destinationFile.absolutePath), arrayOf(mimeType), null)
				}
			}
			.onSuccess { _saveMessage.emit(context.getString(R.string.home_msg_saved_to_pictures)) }
			.onFailure { exception -> _saveMessage.emit(context.getString(R.string.home_msg_save_failed, exception.message)) }
		}
	}
}
