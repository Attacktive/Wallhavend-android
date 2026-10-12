package xyz.attacktive.wallhavend.domain.service

import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class BootReceiver: BroadcastReceiver() {
	@Inject
	lateinit var rotationScheduler: RotationScheduler

	override fun onReceive(context: Context, intent: Intent) {
		val trigger = when (intent.action) {
			Intent.ACTION_BOOT_COMPLETED -> RotationReconciliationTrigger.BOOT
			Intent.ACTION_MY_PACKAGE_REPLACED -> RotationReconciliationTrigger.PACKAGE_REPLACED
			else -> return
		}

		val pendingResult = goAsync()
		val scope = CoroutineScope(Dispatchers.IO)
		scope.launch {
			try {
				rotationScheduler.reconcile(trigger)
			} finally {
				pendingResult.finish()
				scope.cancel()
			}
		}
	}
}
