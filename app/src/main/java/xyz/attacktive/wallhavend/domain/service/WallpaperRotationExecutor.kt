package xyz.attacktive.wallhavend.domain.service

import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import android.content.Context
import android.hardware.display.DisplayManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.util.DisplayMetrics
import android.view.Display
import android.view.WindowManager
import androidx.annotation.VisibleForTesting
import dagger.hilt.android.qualifiers.ApplicationContext
import retrofit2.HttpException
import xyz.attacktive.wallhavend.domain.model.AppError
import xyz.attacktive.wallhavend.domain.model.AppSettings
import xyz.attacktive.wallhavend.domain.model.NoResultsException
import xyz.attacktive.wallhavend.domain.model.RotationMode
import xyz.attacktive.wallhavend.domain.model.ScreenInfo
import xyz.attacktive.wallhavend.domain.model.UnsupportedFormatException
import xyz.attacktive.wallhavend.domain.model.WallpaperIdentity
import xyz.attacktive.wallhavend.domain.model.WallpaperSource
import xyz.attacktive.wallhavend.domain.model.closestAspectRatio
import xyz.attacktive.wallhavend.domain.model.forWallpaperOrientation
import xyz.attacktive.wallhavend.domain.model.naturalDimensions
import xyz.attacktive.wallhavend.domain.repository.ServiceStateRepository
import xyz.attacktive.wallhavend.domain.repository.SettingsRepository
import xyz.attacktive.wallhavend.domain.repository.WallpaperRepository

@Singleton
class WallpaperRotationExecutor @Inject constructor(
	@param:ApplicationContext private val context: Context,
	private val settingsRepository: SettingsRepository,
	private val wallpaperRepository: WallpaperRepository,
	private val fileManager: WallpaperFileManager,
	private val stateRepository: ServiceStateRepository,
	private val wallpaperMutationCoordinator: WallpaperMutationCoordinator,
	private val wallpaperApplier: WallpaperApplier
) {
	private var stateRestored = false

	@VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
	internal var networkStateProvider: () -> NetworkState = ::currentNetworkState

	@VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
	internal var screenInfoProvider: () -> ScreenInfo = ::screenInfo

	suspend fun restoreState() {
		wallpaperMutationCoordinator.serialize {
			if (stateRestored) {
				return@serialize
			}

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
			stateRestored = true
		}
	}

	suspend fun performUpdate(forceDownload: Boolean = false) {
		wallpaperMutationCoordinator.serialize {
			val settings = settingsRepository.settings.first()
			val networkState = networkStateProvider()
			stateRepository.update { it.copy(isOnline = networkState.online) }

			if (shouldDownload(settings.rotationMode, networkState.online, networkState.onWifi, forceDownload)) {
				handleOnlineUpdate(settings)
			} else if (settings.rotationMode == RotationMode.PINNED_ONLY) {
				cyclePinnedOnly(settings)
			} else {
				cycleFromPool(settings)
			}
		}
	}

	suspend fun applySpecificPath(path: String) {
		wallpaperMutationCoordinator.serialize {
			val file = File(path)
			if (!file.exists()) {
				return@serialize
			}

			val settings = settingsRepository.settings.first()
			wallpaperApplier.apply(file, settings.wallpaperTarget)
				.onSuccess {
					val state = stateRepository.state.value
					val newPreviousPath = if (state.currentWallpaperPath != path) {
						state.currentWallpaperPath
					} else {
						state.previousWallpaperPath
					}

					stateRepository.update {
						it.copy(currentWallpaperPath = path, previousWallpaperPath = newPreviousPath)
					}

					val lastUpdatedMs = state.lastUpdatedMs ?: System.currentTimeMillis()
					settingsRepository.saveServiceState(lastUpdatedMs, path, newPreviousPath)
				}
		}
	}

	private suspend fun handleOnlineUpdate(settings: AppSettings) {
		val screenInfo = screenInfoProvider().forWallpaperOrientation(settings.wallpaperOrientation)

		wallpaperRepository.next(settings, screenInfo)
			.fold(
				onSuccess = { (_, file) -> onWallpaperFetched(file, settings) },
				onFailure = { throwable -> onFetchError(throwable, settings) }
			)
	}

	private suspend fun onWallpaperFetched(file: File, settings: AppSettings) {
		wallpaperApplier.apply(file, settings.wallpaperTarget)
			.fold(
				onSuccess = {
					val remaining = fileManager.trimToSize(settings.poolSize, settings.pinnedIds)
					val paths = remaining.map { it.absolutePath }
					val now = System.currentTimeMillis()

					stateRepository.update { state ->
						state.copy(
							lastUpdatedMs = now,
							currentWallpaperPath = paths.firstOrNull(),
							previousWallpaperPath = paths.getOrNull(1),
							poolPaths = paths,
							error = null
						)
					}

					settingsRepository.saveServiceState(now, paths.firstOrNull(), paths.getOrNull(1))
				},
				onFailure = { throwable ->
					stateRepository.postError(AppError.WallpaperApplyFailed(throwable.message ?: "Unknown"))
				}
			)
	}

	private suspend fun onFetchError(throwable: Throwable, settings: AppSettings) {
		val error = appErrorFor(throwable, settings)
		val providerFailure = error is AppError.ApiError || error is AppError.NetworkError
		stateRepository.postError(error, autoClear = !providerFailure)

		if (!providerFailure) {
			return
		}

		if (settings.rotationMode == RotationMode.PINNED_ONLY) {
			cyclePinnedOnly(settings)
		} else {
			cycleFromPool(settings)
		}
	}

	private fun appErrorFor(throwable: Throwable, settings: AppSettings): AppError {
		val source = settings.enabledSources.singleOrNull()

		return when (throwable) {
			// The hint only makes sense for Wallhaven: it's the Wallhaven server that yields zero results on certain ratios when an API key is present.
			is NoResultsException -> if (WallpaperSource.WALLHAVEN in settings.enabledSources && settings.apiKey.isNotBlank()) {
				AppError.NoResultsWithRatioHint
			} else {
				AppError.NoResults
			}
			is UnsupportedFormatException -> AppError.UnsupportedFormat
			is HttpException -> AppError.ApiError(throwable.code(), source)
			else -> AppError.NetworkError(throwable.messageOrType(), source)
		}
	}

	private fun Throwable.messageOrType() = message ?: javaClass.simpleName

	private suspend fun cycleFromPool(settings: AppSettings) {
		val pool = fileManager.listAll()
			.reversed()
			.map { it.absolutePath }

		cycle(pool, settings)
	}

	private suspend fun cyclePinnedOnly(settings: AppSettings) {
		val pool = fileManager.listAll()
			.filter {
				WallpaperIdentity.parse(it.nameWithoutExtension)
					.matches(settings.pinnedIds)
			}
			.reversed()
			.map { it.absolutePath }

		cycle(pool, settings)
	}

	private suspend fun cycle(pool: List<String>, settings: AppSettings) {
		val next = nextInCycle(pool, stateRepository.state.value.currentWallpaperPath) ?: return
		val file = File(next)
		if (!file.exists()) {
			return
		}

		wallpaperApplier.apply(file, settings.wallpaperTarget)
			.onSuccess {
				val state = stateRepository.state.value
				stateRepository.update {
					it.copy(
						currentWallpaperPath = next,
						previousWallpaperPath = state.currentWallpaperPath
					)
				}

				val lastUpdatedMs = state.lastUpdatedMs ?: System.currentTimeMillis()
				settingsRepository.saveServiceState(lastUpdatedMs, next, state.currentWallpaperPath)
			}
	}

	private fun screenInfo(): ScreenInfo {
		val windowManager = context.getSystemService(WindowManager::class.java)
		val (measuredWidth, measuredHeight) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
			val bounds = windowManager.currentWindowMetrics.bounds

			bounds.width() to bounds.height()
		} else {
			legacyScreenDimensions(windowManager)
		}

		val rotation = context.getSystemService(DisplayManager::class.java)
			.getDisplay(Display.DEFAULT_DISPLAY)
			.rotation

		val (width, height) = naturalDimensions(measuredWidth, measuredHeight, rotation)

		return ScreenInfo(closestAspectRatio(width, height), width, height)
	}

	@Suppress("DEPRECATION")
	private fun legacyScreenDimensions(windowManager: WindowManager): Pair<Int, Int> {
		val displayMetrics = DisplayMetrics()
		windowManager.defaultDisplay.getRealMetrics(displayMetrics)

		return displayMetrics.widthPixels to displayMetrics.heightPixels
	}

	private fun currentNetworkState(): NetworkState {
		val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
		val capabilities = connectivityManager.activeNetwork
			?.let { connectivityManager.getNetworkCapabilities(it) }

		val online = capabilities?.isOnline() == true
		val onWifi = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true

		return NetworkState(online, onWifi)
	}

	private fun NetworkCapabilities.isOnline() =
		hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) && hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}

@VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
internal data class NetworkState(val online: Boolean, val onWifi: Boolean)
