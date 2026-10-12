package xyz.attacktive.wallhavend.domain.service

import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class RotationAlarmReceiver: BroadcastReceiver() {
	@Inject
	lateinit var rotationScheduler: RotationScheduler

	override fun onReceive(context: Context, intent: Intent) {
		if (intent.action != RotationScheduler.ACTION_ROTATE) {
			return
		}

		val delivery = RotationDelivery(
			intent.getLongExtra(RotationScheduler.EXTRA_DUE_ELAPSED_REALTIME, -1),
			intent.getLongExtra(RotationScheduler.EXTRA_GENERATION, -1),
			intent.getIntExtra(RotationScheduler.EXTRA_BOOT_COUNT, -1)
		)

		if (delivery.dueElapsedRealtime < 0 || delivery.generation < 0 || delivery.bootCount < 0) {
			return
		}

		val pendingResult = goAsync()
		val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
		scope.launch {
			try {
				rotationScheduler.handleAlarm(delivery)
			} finally {
				pendingResult.finish()
				scope.cancel()
			}
		}
	}
}
