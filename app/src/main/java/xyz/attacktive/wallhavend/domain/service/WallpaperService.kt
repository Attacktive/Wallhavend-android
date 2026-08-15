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
import android.app.WallpaperManager
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.util.DisplayMetrics
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
import xyz.attacktive.wallhavend.domain.model.WallpaperTarget
import xyz.attacktive.wallhavend.domain.model.closestAspectRatio
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

	@VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
	internal var serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

	private var timerJob: Job? = null
	private var errorClearJob: Job? = null

	override fun onBind(intent: Intent?) = null

	override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
		startForeground(NOTIFICATION_ID, buildNotification())

		when (intent?.action) {
			ACTION_STOP -> serviceScope.launch {
				settingsRepository.setAutoUpdateEnabled(false)
				stopSelf()
			}
			ACTION_UPDATE_NOW -> serviceScope.launch { performUpdate(forceDownload = true) }
			ACTION_ROLL_NOW -> serviceScope.launch { performUpdate() }
			ACTION_APPLY_PATH -> {
				val path = intent.getStringExtra(EXTRA_PATH)
				if (path != null) {
					serviceScope.launch { applySpecificPath(path) }
				}
			}
			else -> startTimerLoop()
		}

		return START_STICKY
	}

	private fun startTimerLoop() {
		if (timerJob?.isActive == true) {
			return
		}

		stateRepository.update { it.copy(isRunning = true) }

		timerJob = serviceScope.launch {
			settingsRepository.setAutoUpdateEnabled(true)
			runCatching { performUpdate() }

			intervalTicks(settingsRepository.settings.map { it.updateIntervalMinutes })
				.collect { runCatching { performUpdate() } }
		}
	}

	private suspend fun performUpdate(forceDownload: Boolean = false) {
		val settings = settingsRepository.settings.first()

		val capabilities = activeNetworkCapabilities()
		val online = capabilities?.isOnline() == true
		stateRepository.update { it.copy(isOnline = online) }

		val onWifi = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true

		if (shouldDownload(settings.rotationMode, online, onWifi, forceDownload)) {
			handleOnlineUpdate(settings)
		} else if (settings.rotationMode == RotationMode.PINNED_ONLY) {
			cyclePinnedOnly(settings)
		} else {
			cycleFromPool(settings)
		}
	}

	private suspend fun handleOnlineUpdate(settings: AppSettings) {
		wallpaperRepository.next(settings, screenInfo())
			.fold(
				onSuccess = { (_, file) -> onWallpaperFetched(file, settings) },
				onFailure = { throwable -> onFetchError(throwable, settings) }
			)
	}

	private suspend fun onWallpaperFetched(file: File, settings: AppSettings) {
		applyWallpaper(file, settings.wallpaperTarget)
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
					updateNotification()
				},
				onFailure = { throwable ->
					postError(AppError.WallpaperApplyFailed(throwable.message ?: "Unknown"))
				}
			)
	}

	private fun onFetchError(throwable: Throwable, settings: AppSettings) {
		val error = when (throwable) {
			// Wallhaven server bug: certain ratios yield zero results when an API key is present
			is NoResultsException -> if (settings.apiKey.isNotBlank()) {
				AppError.NoResultsWithRatioHint
			} else {
				AppError.NoResults
			}
			is UnsupportedFormatException -> AppError.UnsupportedFormat
			is HttpException -> AppError.ApiError(throwable.code())
			else -> AppError.NetworkError(throwable.message ?: throwable.javaClass.simpleName)
		}

		postError(error)
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

		applyWallpaper(file, settings.wallpaperTarget)
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

				updateNotification()
			}
	}

	private suspend fun applySpecificPath(path: String) {
		val file = File(path)
		if (!file.exists()) {
			return
		}

		val settings = settingsRepository.settings.first()

		applyWallpaper(file, settings.wallpaperTarget)
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

				updateNotification()
			}
	}

	private fun applyWallpaper(file: File, target: WallpaperTarget) = runCatching {
		val flags = when (target) {
			WallpaperTarget.HOME -> WallpaperManager.FLAG_SYSTEM
			WallpaperTarget.LOCK -> WallpaperManager.FLAG_LOCK
			WallpaperTarget.BOTH -> WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
		}

		file.inputStream()
			.use { input ->
				getSystemService(WallpaperManager::class.java)
					.setStream(input, null, true, flags)
			}
	}

	@VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
	internal fun postError(error: AppError) {
		stateRepository.update { it.copy(error = error) }

		errorClearJob?.cancel()
		errorClearJob = serviceScope.launch {
			delay(10_000.milliseconds)
			stateRepository.update { it.copy(error = null) }
		}
	}

	private fun screenInfo(): ScreenInfo {
		val windowManager = getSystemService(WindowManager::class.java)

		val (width, height) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
			val bounds = windowManager.currentWindowMetrics.bounds
			bounds.width() to bounds.height()
		} else {
			legacyScreenDimensions(windowManager)
		}

		return ScreenInfo(closestAspectRatio(width, height), width, height)
	}

	@Suppress("DEPRECATION")
	private fun legacyScreenDimensions(windowManager: WindowManager): Pair<Int, Int> {
		val displayMetrics = DisplayMetrics()
		windowManager.defaultDisplay.getRealMetrics(displayMetrics)

		return displayMetrics.widthPixels to displayMetrics.heightPixels
	}

	private fun activeNetworkCapabilities(): NetworkCapabilities? {
		val connectivityManager = getSystemService(ConnectivityManager::class.java)

		return connectivityManager.activeNetwork
			?.let { connectivityManager.getNetworkCapabilities(it) }
	}

	private fun NetworkCapabilities.isOnline() =
		hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) && hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)

	private fun buildNotification(): Notification {
		val state = stateRepository.state.value

		val lastUpdated = state.lastUpdatedMs
			?.let { timestamp ->
				val formattedTime = SimpleDateFormat("HH:mm", Locale.getDefault())
					.format(Date(timestamp))

				getString(R.string.home_status_last_updated, formattedTime)
			}
			?: getString(R.string.service_status_never)

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
			.setContentText(lastUpdated)
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
		const val ACTION_STOP = "xyz.attacktive.wallhavend.STOP"
		const val ACTION_UPDATE_NOW = "xyz.attacktive.wallhavend.UPDATE_NOW"
		const val ACTION_ROLL_NOW = "xyz.attacktive.wallhavend.ROLL_NOW"
		const val ACTION_APPLY_PATH = "xyz.attacktive.wallhavend.APPLY_PATH"
		const val EXTRA_PATH = "path"

		fun start(context: Context) {
			val intent = Intent(context, WallpaperService::class.java)
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
