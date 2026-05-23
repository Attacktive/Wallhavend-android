package xyz.attacktive.wallhavend.domain.service

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.WallpaperManager
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.core.app.NotificationCompat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import retrofit2.HttpException
import xyz.attacktive.wallhavend.MainActivity
import xyz.attacktive.wallhavend.R
import xyz.attacktive.wallhavend.WallhavendApplication.Companion.NOTIFICATION_CHANNEL_ID
import xyz.attacktive.wallhavend.WallhavendApplication.Companion.NOTIFICATION_ID
import xyz.attacktive.wallhavend.domain.model.AppError
import xyz.attacktive.wallhavend.domain.model.NoResultsException
import xyz.attacktive.wallhavend.domain.model.UnsupportedFormatException
import xyz.attacktive.wallhavend.domain.model.WallpaperTarget
import xyz.attacktive.wallhavend.domain.repository.ServiceStateRepository
import xyz.attacktive.wallhavend.domain.repository.SettingsRepository
import xyz.attacktive.wallhavend.domain.repository.WallhavenRepository

@AndroidEntryPoint
class WallpaperService: Service() {
	@Inject lateinit var settingsRepository: SettingsRepository
	@Inject lateinit var wallhavenRepository: WallhavenRepository
	@Inject lateinit var fileManager: WallpaperFileManager
	@Inject lateinit var stateRepository: ServiceStateRepository

	private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
	private var timerJob: Job? = null

	override fun onBind(intent: Intent?) = null

	override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
		startForeground(NOTIFICATION_ID, buildNotification())
		when (intent?.action) {
			ACTION_STOP -> stopSelf()
			ACTION_UPDATE_NOW -> serviceScope.launch { performUpdate(forceDownload = true) }
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
			runCatching { performUpdate() }
			while (true) {
				val intervalMs = settingsRepository.settings.first().updateIntervalMinutes * 60_000L
				delay(intervalMs)
				runCatching { performUpdate() }
			}
		}
	}

	private suspend fun performUpdate(forceDownload: Boolean = false) {
		val settings = settingsRepository.settings.first()

		val online = isOnline()
		stateRepository.update { it.copy(isOnline = online) }

		val canDownload = online && (forceDownload || !settings.wifiOnly || isOnWifi())

		if (canDownload) {
			val result = wallhavenRepository.next(settings)
			result.fold(
				onSuccess = { (_, file) ->
					val applyResult = applyWallpaper(file, settings.wallpaperTarget)
					applyResult.fold(
						onSuccess = {
							val remaining = if (settings.poolSize == 0) {
								fileManager.trimToSize(0)
								emptyList()
							} else {
								fileManager.trimToSize(settings.poolSize)
							}

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
				},
				onFailure = { throwable ->
					val error = when (throwable) {
						// Wallhaven server bug: certain ratios yield zero results when an API key is present
						is NoResultsException -> if (settings.apiKey.isNotBlank() && settings.aspectRatio.isNotBlank()) {
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
			)
		} else {
			val current = stateRepository.state.value.currentWallpaperPath
			val next = fileManager.listAll()
				.map { it.absolutePath }
				.firstOrNull { it != current }
				?: return

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

					updateNotification()
				}
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
				val newPrev = if (state.currentWallpaperPath != path) {
					state.currentWallpaperPath
				} else {
					state.previousWallpaperPath
				}

				stateRepository.update {
					it.copy(currentWallpaperPath = path, previousWallpaperPath = newPrev)
				}

				updateNotification()
			}
	}

	private fun applyWallpaper(file: File, target: WallpaperTarget) = runCatching {
		val bitmap = BitmapFactory.decodeFile(file.absolutePath)
			?: error("Failed to decode bitmap from ${file.name}")

		val flags = when (target) {
			WallpaperTarget.HOME -> WallpaperManager.FLAG_SYSTEM
			WallpaperTarget.LOCK -> WallpaperManager.FLAG_LOCK
			WallpaperTarget.BOTH -> WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
		}

		getSystemService(WallpaperManager::class.java).setBitmap(bitmap, null, true, flags)
		bitmap.recycle()
	}

	private fun postError(error: AppError) {
		stateRepository.update { it.copy(error = error) }

		serviceScope.launch {
			delay(10_000)
			stateRepository.update { it.copy(error = null) }
		}
	}

	private fun isOnline(): Boolean {
		val cm = getSystemService(ConnectivityManager::class.java)

		return cm.activeNetwork?.let { cm.getNetworkCapabilities(it) }
			?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
	}

	private fun isOnWifi(): Boolean {
		val cm = getSystemService(ConnectivityManager::class.java)

		return cm.activeNetwork
			?.let { cm.getNetworkCapabilities(it) }
			?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
	}

	private fun buildNotification(): Notification {
		val state = stateRepository.state.value

		val lastUpdated = state.lastUpdatedMs?.let {
			"Last: ${SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(it))}"
		} ?: "Never updated"

		val openIntent = PendingIntent.getActivity(
			this, 0,
			Intent(this, MainActivity::class.java),
			PendingIntent.FLAG_IMMUTABLE
		)

		val updateNowIntent = PendingIntent.getService(
			this, 1,
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
			.setContentTitle("Wallhavend")
			.setContentText(lastUpdated)
			.setContentIntent(openIntent)
			.setOngoing(true)
			.addAction(0, "Download now", updateNowIntent)
			.addAction(0, "Stop", stopIntent)
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
		const val ACTION_APPLY_PATH = "xyz.attacktive.wallhavend.APPLY_PATH"
		const val EXTRA_PATH = "path"

		fun start(context: Context) {
			context.startForegroundService(Intent(context, WallpaperService::class.java))
		}

		fun stop(context: Context) {
			context.startForegroundService(Intent(context, WallpaperService::class.java)
				.apply { action = ACTION_STOP }
			)
		}

		fun updateNow(context: Context) {
			context.startForegroundService(Intent(context, WallpaperService::class.java)
				.apply { action = ACTION_UPDATE_NOW }
			)
		}

		fun applyPath(context: Context, path: String) {
			context.startForegroundService(Intent(context, WallpaperService::class.java)
				.apply {
					action = ACTION_APPLY_PATH
					putExtra(EXTRA_PATH, path)
				}
			)
		}
	}
}
