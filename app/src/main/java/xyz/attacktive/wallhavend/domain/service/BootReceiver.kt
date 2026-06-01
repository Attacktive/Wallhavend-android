package xyz.attacktive.wallhavend.domain.service

import javax.inject.Inject
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import xyz.attacktive.wallhavend.domain.repository.SettingsRepository

@AndroidEntryPoint
class BootReceiver: BroadcastReceiver() {
	@Inject lateinit var settingsRepository: SettingsRepository

	override fun onReceive(context: Context, intent: Intent) {
		if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) {
			return
		}

		val pendingResult = goAsync()

		CoroutineScope(Dispatchers.IO).launch {
			try {
				val settings = settingsRepository.settings.first()
				if (settings.autoStartOnBoot) {
					WallpaperService.start(context)
				}
			} finally {
				pendingResult.finish()
			}
		}
	}
}
