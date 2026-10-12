package xyz.attacktive.wallhavend.domain.service

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import android.app.ForegroundServiceStartNotAllowedException
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.VisibleForTesting
import androidx.core.app.NotificationCompat
import dagger.hilt.android.AndroidEntryPoint
import xyz.attacktive.wallhavend.MainActivity
import xyz.attacktive.wallhavend.R
import xyz.attacktive.wallhavend.WallhavendApplication.Companion.NOTIFICATION_CHANNEL_ID
import xyz.attacktive.wallhavend.WallhavendApplication.Companion.NOTIFICATION_ID
import xyz.attacktive.wallhavend.domain.model.AppError
import xyz.attacktive.wallhavend.domain.model.WallpaperSource
import xyz.attacktive.wallhavend.domain.repository.ServiceStateRepository

@AndroidEntryPoint
class WallpaperService: Service() {
	@Inject
	lateinit var executor: WallpaperRotationExecutor

	@Inject
	lateinit var stateRepository: ServiceStateRepository

	@VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
	internal var serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

	private val oneShotTracker = OneShotTracker()

	override fun onBind(intent: Intent?) = null

	override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
		if (!promoteToForeground(startId)) {
			return START_NOT_STICKY
		}

		when (wallpaperServiceCommand(intent?.action)) {
			WallpaperServiceCommand.UPDATE_NOW -> launchOneShot(startId) { executor.performUpdate(forceDownload = true) }
			WallpaperServiceCommand.ROLL_NOW -> launchOneShot(startId) { executor.performUpdate() }
			WallpaperServiceCommand.APPLY_PATH -> launchOneShot(startId) {
				intent?.getStringExtra(EXTRA_PATH)
					?.let { executor.applySpecificPath(it) }
			}
			WallpaperServiceCommand.UNKNOWN -> stopSelfResult(startId)
		}

		return START_NOT_STICKY
	}

	private fun promoteToForeground(startId: Int): Boolean {
		try {
			startForeground(NOTIFICATION_ID, buildNotification())
		} catch (error: RuntimeException) {
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && error is ForegroundServiceStartNotAllowedException) {
				stopSelfResult(startId)
				return false
			}

			throw error
		}

		return true
	}

	private fun launchOneShot(startId: Int, block: suspend () -> Unit) {
		oneShotTracker.start(startId)
		serviceScope.launch {
			try {
				block()
			} finally {
				oneShotTracker.finish()
					?.let { stopSelfResult(it) }
			}
		}
	}

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

		return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
			.setSmallIcon(R.drawable.ic_wallpaper)
			.setContentTitle(getString(R.string.app_name))
			.setContentText(contentText)
			.setContentIntent(openIntent)
			.setOngoing(true)
			.build()
	}

	override fun onDestroy() {
		super.onDestroy()
		serviceScope.cancel()
	}

	companion object {
		const val ACTION_UPDATE_NOW = "xyz.attacktive.wallhavend.UPDATE_NOW"
		const val ACTION_ROLL_NOW = "xyz.attacktive.wallhavend.ROLL_NOW"
		const val ACTION_APPLY_PATH = "xyz.attacktive.wallhavend.APPLY_PATH"
		const val EXTRA_PATH = "path"

		fun updateNow(context: Context) {
			start(context, ACTION_UPDATE_NOW)
		}

		fun rollNow(context: Context) {
			start(context, ACTION_ROLL_NOW)
		}

		fun applyPath(context: Context, path: String) {
			val intent = Intent(context, WallpaperService::class.java)
				.apply {
					action = ACTION_APPLY_PATH
					putExtra(EXTRA_PATH, path)
				}

			context.startForegroundService(intent)
		}

		private fun start(context: Context, action: String) {
			val intent = Intent(context, WallpaperService::class.java)
				.apply { this.action = action }

			context.startForegroundService(intent)
		}
	}
}

@VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
internal enum class WallpaperServiceCommand {
	UPDATE_NOW,
	ROLL_NOW,
	APPLY_PATH,
	UNKNOWN
}

@VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
internal fun wallpaperServiceCommand(action: String?) = when (action) {
	WallpaperService.ACTION_UPDATE_NOW -> WallpaperServiceCommand.UPDATE_NOW
	WallpaperService.ACTION_ROLL_NOW -> WallpaperServiceCommand.ROLL_NOW
	WallpaperService.ACTION_APPLY_PATH -> WallpaperServiceCommand.APPLY_PATH
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
