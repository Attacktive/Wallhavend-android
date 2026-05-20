package xyz.attacktive.wallhavend

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class WallhavendApplication: Application() {
	override fun onCreate() {
		super.onCreate()
		createNotificationChannel()
	}

	private fun createNotificationChannel() {
		val channel = NotificationChannel(
			NOTIFICATION_CHANNEL_ID,
			"Wallpaper Service",
			NotificationManager.IMPORTANCE_LOW
		).apply {
			description = "Shows wallpaper auto-update status"
		}

		getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
	}

	companion object {
		const val NOTIFICATION_CHANNEL_ID = "wallhavend_service"
		const val NOTIFICATION_ID = 1
	}
}
