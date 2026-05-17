package xyz.attacktive.wallhavend.domain.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import xyz.attacktive.wallhavend.domain.repository.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {
	@Inject lateinit var settingsRepository: SettingsRepository

	override fun onReceive(context: Context, intent: Intent) {
		if (intent.action != Intent.ACTION_BOOT_COMPLETED) {
			return
		}

		val pending = goAsync()

		CoroutineScope(Dispatchers.IO).launch {
			try {
				val settings = settingsRepository.settings.first()
				if (settings.autoStartOnBoot) {
					WallpaperService.start(context)
				}
			} finally {
				pending.finish()
			}
		}
	}
}
