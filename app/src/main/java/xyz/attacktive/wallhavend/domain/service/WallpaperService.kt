package xyz.attacktive.wallhavend.domain.service

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.util.DisplayMetrics
import android.view.Display
import android.view.WindowManager
import androidx.annotation.VisibleForTesting
import androidx.core.app.NotificationCompat
import dagger.hilt.android.AndroidEntryPoint
import retrofit2.HttpException
import xyz.attacktive.wallhavend.MainActivity
import xyz.attacktive.wallhavend.R
import xyz.attacktive.wallhavend.WallhavendApplication.Companion.NOTIFICATION_CHANNEL_ID
import xyz.attacktive.wallhavend.WallhavendApplication.Companion.NOTIFICATION_ID
import xyz.attacktive.wallhavend.domain.model.AppError
import xyz.attacktive.wallhavend.domain.model.AppSettings
import xyz.attacktive.wallhavend.domain.model.NoResultsException
import xyz.attacktive.wallhavend.domain.model.RotationMode
import xyz.attacktive.wallhavend.domain.model.ScreenInfo
import xyz.attacktive.wallhavend.domain.model.UnsupportedFormatException
import xyz.attacktive.wallhavend.domain.model.WallpaperIdentity
import xyz.attacktive.wallhavend.domain.model.WallpaperSource
import xyz.attacktive.wallhavend.domain.model.WallpaperTarget
import xyz.attacktive.wallhavend.domain.model.closestAspectRatio
import xyz.attacktive.wallhavend.domain.model.naturalDimensions
import xyz.attacktive.wallhavend.domain.repository.ServiceStateRepository
import xyz.attacktive.wallhavend.domain.repository.SettingsRepository
import xyz.attacktive.wallhavend.domain.repository.WallpaperRepository

@AndroidEntryPoint
class WallpaperService: Service() {
	@Inject
	lateinit var settingsRepository: SettingsRepository

	@Inject
	lateinit var wallpaperRepository: WallpaperRepository

	@Inject
	lateinit var fileManager: WallpaperFileManager

	@Inject
	lateinit var stateRepository: ServiceStateRepository

	@Inject
	lateinit var wallpaperMutationCoordinator: WallpaperMutationCoordinator

	@Inject
	lateinit var wallpaperApplier: WallpaperApplier

	@VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
	internal var serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

	@VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
	internal var networkStateProvider: () -> NetworkState = ::currentNetworkState

	@VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
	internal var screenInfoProvider: () -> ScreenInfo = ::screenInfo

	@VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
	internal var notificationRefresher: () -> Unit = ::updateNotification

	private var timerJob: Job? = null
	private val oneShotTracker = OneShotTracker()

	override fun onBind(intent: Intent?) = null

	override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
		startForeground(NOTIFICATION_ID, buildNotification())

		when (wallpaperServiceCommand(intent?.action)) {
			WallpaperServiceCommand.START -> startTimerLoop()
			WallpaperServiceCommand.STOP -> serviceScope.launch {
				settingsRepository.setAutoUpdateEnabled(false)
				stopSelf()
			}
			WallpaperServiceCommand.UPDATE_NOW -> launchOneShot(startId) { performUpdate(forceDownload = true) }
			WallpaperServiceCommand.ROLL_NOW -> launchOneShot(startId) { performUpdate() }
			WallpaperServiceCommand.APPLY_PATH -> launchOneShot(startId) {
				intent?.getStringExtra(EXTRA_PATH)
					?.let { applySpecificPath(it) }
			}
			WallpaperServiceCommand.RESTORE -> restoreTimerLoop(startId)
			WallpaperServiceCommand.UNKNOWN -> if (timerJob?.isActive != true) {
				stopSelfResult(startId)
			}
		}

		return START_STICKY
	}

	private fun restoreTimerLoop(startId: Int) {
		serviceScope.launch {
			if (currentAutoUpdateEnabled()) {
				startTimerLoop()
			} else {
				stopSelfResult(startId)
			}
		}
	}

	private fun launchOneShot(startId: Int, block: suspend () -> Unit) {
		oneShotTracker.start(startId)
		serviceScope.launch {
			try {
				block()
			} finally {
				finishOneShot()
			}
		}
	}

	private suspend fun finishOneShot() {
		val stopStartId = oneShotTracker.finish() ?: return
		when (oneShotCompletion(timerJob?.isActive == true, currentAutoUpdateEnabled())) {
			OneShotCompletion.KEEP_RUNNING -> Unit
			OneShotCompletion.RESTORE_TIMER -> startTimerLoop(performImmediately = false)
			OneShotCompletion.STOP_SERVICE -> stopSelfResult(stopStartId)
		}
	}

	private suspend fun currentAutoUpdateEnabled() = runCatching { settingsRepository.settings.first().autoUpdateEnabled }
		.getOrDefault(false)

	private fun startTimerLoop(performImmediately: Boolean = true) {
		if (timerJob?.isActive == true) {
			return
		}

		stateRepository.update { it.copy(isRunning = true) }

		timerJob = serviceScope.launch {
			settingsRepository.setAutoUpdateEnabled(true)
			if (performImmediately) {
				runCatching { performUpdate() }
			}

			intervalTicks(settingsRepository.settings.map { it.updateIntervalMinutes })
				.collect { runCatching { performUpdate() } }
		}
	}

	@VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
	internal suspend fun performUpdate(forceDownload: Boolean = false) {
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

	private suspend fun handleOnlineUpdate(settings: AppSettings) {
		wallpaperRepository.next(settings, screenInfoProvider())
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
					notificationRefresher()
				},
				onFailure = { throwable ->
					stateRepository.postError(AppError.WallpaperApplyFailed(throwable.message ?: "Unknown"))
				}
			)
	}

	private suspend fun onFetchError(throwable: Throwable, settings: AppSettings) {
		val source = settings.enabledSources.singleOrNull()
		val error = when (throwable) {
			// The hint only makes sense for Wallhaven: it's the Wallhaven server that yields zero results on certain ratios when an API key is present.
			is NoResultsException -> if (WallpaperSource.WALLHAVEN in settings.enabledSources && settings.apiKey.isNotBlank()) {
				AppError.NoResultsWithRatioHint
			} else {
				AppError.NoResults
			}
			is UnsupportedFormatException -> AppError.UnsupportedFormat
			is HttpException -> AppError.ApiError(throwable.code(), source)
			else -> AppError.NetworkError(throwable.message ?: throwable.javaClass.simpleName, source)
		}

		val providerFailure = error is AppError.ApiError || error is AppError.NetworkError
		stateRepository.postError(error, autoClear = !providerFailure)
		notificationRefresher()

		if (!providerFailure) {
			return
		}

		if (settings.rotationMode == RotationMode.PINNED_ONLY) {
			cyclePinnedOnly(settings)
		} else {
			cycleFromPool(settings)
		}
	}

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

				notificationRefresher()
			}
	}

	@VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
	internal suspend fun applySpecificPath(path: String) {
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

					notificationRefresher()
				}
		}
	}



	private fun screenInfo(): ScreenInfo {
		val windowManager = getSystemService(WindowManager::class.java)

		val (measuredWidth, measuredHeight) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
			val bounds = windowManager.currentWindowMetrics.bounds
			bounds.width() to bounds.height()
		} else {
			legacyScreenDimensions(windowManager)
		}

		// DisplayManager is the one rotation source that works from a service context on every supported API level; Context.getDisplay() rejects non-visual contexts like this one and WindowManager.defaultDisplay is deprecated.
		val rotation = getSystemService(DisplayManager::class.java)
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
		val capabilities = activeNetworkCapabilities()
		val online = capabilities?.isOnline() == true
		val onWifi = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true

		return NetworkState(online, onWifi)
	}

	private fun activeNetworkCapabilities(): NetworkCapabilities? {
		val connectivityManager = getSystemService(ConnectivityManager::class.java)

		return connectivityManager.activeNetwork
			?.let { connectivityManager.getNetworkCapabilities(it) }
	}

	private fun NetworkCapabilities.isOnline() =
		hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) && hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)

	private fun providerFailureText(source: WallpaperSource?) = source
		?.let { getString(R.string.service_status_source_request_failed, getString(it.nameRes)) }
		?: getString(R.string.service_status_request_failed)

	private fun buildNotification(): Notification {
		val state = stateRepository.state.value

		val contentText = when (val error = state.error) {
			is AppError.ApiError -> providerFailureText(error.source)
			is AppError.NetworkError -> providerFailureText(error.source)
			else -> state.lastUpdatedMs
				?.let { timestamp ->
					val formattedTime = SimpleDateFormat("HH:mm", Locale.getDefault())
						.format(Date(timestamp))

					getString(R.string.home_status_last_updated, formattedTime)
				}
				?: getString(R.string.service_status_never)
		}

		val openIntent = PendingIntent.getActivity(
			this,
			0,
			Intent(this, MainActivity::class.java)
				.setPackage(packageName),
			PendingIntent.FLAG_IMMUTABLE
		)

		val updateNowIntent = PendingIntent.getService(
			this,
			1,
			Intent(this, WallpaperService::class.java)
				.apply { action = ACTION_UPDATE_NOW },
			PendingIntent.FLAG_IMMUTABLE
		)

		val stopIntent = PendingIntent.getService(
			this, 2,
			Intent(this, WallpaperService::class.java)
				.apply { action = ACTION_STOP },
			PendingIntent.FLAG_IMMUTABLE
		)

		return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
			.setSmallIcon(R.drawable.ic_wallpaper)
			.setContentTitle(getString(R.string.app_name))
			.setContentText(contentText)
			.setContentIntent(openIntent)
			.setOngoing(true)
			.addAction(0, getString(R.string.home_action_download_now), updateNowIntent)
			.addAction(0, getString(R.string.home_action_stop), stopIntent)
			.build()
	}

	private fun updateNotification() {
		getSystemService(NotificationManager::class.java)
			.notify(NOTIFICATION_ID, buildNotification())
	}

	override fun onDestroy() {
		super.onDestroy()
		stateRepository.update { it.copy(isRunning = false) }
		serviceScope.cancel()
	}

	companion object {
		const val ACTION_START = "xyz.attacktive.wallhavend.START"
		const val ACTION_STOP = "xyz.attacktive.wallhavend.STOP"
		const val ACTION_UPDATE_NOW = "xyz.attacktive.wallhavend.UPDATE_NOW"
		const val ACTION_ROLL_NOW = "xyz.attacktive.wallhavend.ROLL_NOW"
		const val ACTION_APPLY_PATH = "xyz.attacktive.wallhavend.APPLY_PATH"
		const val EXTRA_PATH = "path"

		fun start(context: Context) {
			val intent = Intent(context, WallpaperService::class.java)
				.apply { action = ACTION_START }

			context.startForegroundService(intent)
		}

		fun stop(context: Context) {
			val intent = Intent(context, WallpaperService::class.java)
				.apply { action = ACTION_STOP }

			context.startForegroundService(intent)
		}

		fun updateNow(context: Context) {
			val intent = Intent(context, WallpaperService::class.java)
				.apply { action = ACTION_UPDATE_NOW }

			context.startForegroundService(intent)
		}

		fun rollNow(context: Context) {
			val intent = Intent(context, WallpaperService::class.java)
				.apply { action = ACTION_ROLL_NOW }

			context.startForegroundService(intent)
		}

		fun applyPath(context: Context, path: String) {
			val intent = Intent(context, WallpaperService::class.java)
				.apply {
					action = ACTION_APPLY_PATH
					putExtra(EXTRA_PATH, path)
				}

			context.startForegroundService(intent)
		}
	}
}

@VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
internal data class NetworkState(val online: Boolean, val onWifi: Boolean)

@VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
internal enum class WallpaperServiceCommand {
	START,
	STOP,
	UPDATE_NOW,
	ROLL_NOW,
	APPLY_PATH,
	RESTORE,
	UNKNOWN
}

@VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
internal fun wallpaperServiceCommand(action: String?) = when (action) {
	WallpaperService.ACTION_START -> WallpaperServiceCommand.START
	WallpaperService.ACTION_STOP -> WallpaperServiceCommand.STOP
	WallpaperService.ACTION_UPDATE_NOW -> WallpaperServiceCommand.UPDATE_NOW
	WallpaperService.ACTION_ROLL_NOW -> WallpaperServiceCommand.ROLL_NOW
	WallpaperService.ACTION_APPLY_PATH -> WallpaperServiceCommand.APPLY_PATH
	null -> WallpaperServiceCommand.RESTORE
	else -> WallpaperServiceCommand.UNKNOWN
}

@VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
internal class OneShotTracker {
	private val lock = Any()
	private var activeCount = 0
	private var latestStartId = 0

	fun start(startId: Int) {
		synchronized(lock) {
			activeCount++
			latestStartId = maxOf(latestStartId, startId)
		}
	}

	fun finish(): Int? = synchronized(lock) {
		check(activeCount > 0) { "No one-shot command is active" }
		activeCount--
		if (activeCount > 0) {
			return@synchronized null
		}

		val stopStartId = latestStartId
		latestStartId = 0

		stopStartId
	}
}

@VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
internal enum class OneShotCompletion {
	KEEP_RUNNING,
	RESTORE_TIMER,
	STOP_SERVICE
}

@VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
internal fun oneShotCompletion(timerRunning: Boolean, autoUpdateEnabled: Boolean) = when {
	timerRunning -> OneShotCompletion.KEEP_RUNNING
	autoUpdateEnabled -> OneShotCompletion.RESTORE_TIMER
	else -> OneShotCompletion.STOP_SERVICE
}

/**
 * Emits a tick each time the active interval elapses, reflecting changes to [intervalsMinutes] live.
 *
 * A new interval cancels the in-flight wait and restarts it at the new length, so shortening the
 * interval takes effect promptly instead of after the previous (possibly hours-long) delay finishes.
 * [distinctUntilChanged] stops unrelated settings writes, which re-emit the same interval, from
 * needlessly restarting the countdown.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
internal fun intervalTicks(intervalsMinutes: Flow<Int>): Flow<Unit> = intervalsMinutes
	.distinctUntilChanged()
	.flatMapLatest { intervalMinutes ->
		flow {
			while (true) {
				delay((intervalMinutes * 60_000L).milliseconds)
				emit(Unit)
			}
		}
	}
