package xyz.attacktive.wallhavend.domain.service

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.provider.Settings
import androidx.annotation.VisibleForTesting
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.await
import androidx.work.workDataOf
import dagger.hilt.android.qualifiers.ApplicationContext
import xyz.attacktive.wallhavend.domain.model.AppSettings
import xyz.attacktive.wallhavend.domain.repository.RotationScheduleRepository
import xyz.attacktive.wallhavend.domain.repository.RotationScheduleState
import xyz.attacktive.wallhavend.domain.repository.SettingsRepository

@Singleton
class RotationScheduler @Inject constructor(
	@param:ApplicationContext private val context: Context,
	private val settingsRepository: SettingsRepository,
	private val scheduleRepository: RotationScheduleRepository
) {
	private val mutex = Mutex()
	private val alarmManager = context.getSystemService(AlarmManager::class.java)
	private val workManager by lazy { WorkManager.getInstance(context) }

	suspend fun start(): Boolean = mutex.withLock {
		val settings = settingsRepository.settings.first()
		if (settings.autoUpdateEnabled) {
			reconcileLocked(RotationReconciliationTrigger.FOREGROUND)
			return@withLock false
		}

		settingsRepository.setAutoUpdateEnabled(true)
		initializeLocked(settings.updateIntervalMinutes, scheduleRepository.read())

		true
	}

	suspend fun stop() = mutex.withLock {
		settingsRepository.setAutoUpdateEnabled(false)

		val state = scheduleRepository.read()
		scheduleRepository.write(
			state.copy(
				nextDueElapsedRealtime = null,
				intervalMinutes = null,
				bootCount = currentBootCount(),
				generation = state.generation + 1,
				inFlightDueElapsedRealtime = null,
				inFlightWorkId = null,
				lastCompletedDueElapsedRealtime = null
			)
		)

		cancelAlarm()
		workManager.cancelAllWorkByTag(AUTOMATIC_WORK_TAG)
	}

	/** Reconciles persisted intent with alarm state without performing an immediate rotation. */
	internal suspend fun reconcile(trigger: RotationReconciliationTrigger = RotationReconciliationTrigger.FOREGROUND) = mutex.withLock {
		reconcileLocked(trigger)
	}

	internal suspend fun handleAlarm(delivery: RotationDelivery) = mutex.withLock {
		val settings = settingsRepository.settings.first()
		val state = scheduleRepository.read()
		if (!isCurrentAlarmDelivery(settings.autoUpdateEnabled, state, delivery, currentBootCount())) {
			return@withLock
		}

		val now = SystemClock.elapsedRealtime()
		if (delivery.dueElapsedRealtime > now) {
			armAlarm(delivery)
			return@withLock
		}

		armAlarm(delivery, now + HANDOFF_RECOVERY_DELAY_MILLIS)
		enqueueAutomaticWork(delivery)

		val intervalMinutes = state.intervalMinutes ?: settings.updateIntervalMinutes
		val nextDue = nextDueAfter(delivery.dueElapsedRealtime, intervalMinutes * MILLIS_PER_MINUTE, now)
		val nextDelivery = delivery.copy(dueElapsedRealtime = nextDue)
		scheduleRepository.write(state.copy(nextDueElapsedRealtime = nextDue))
		armAlarm(nextDelivery)
	}

	internal suspend fun prepareAutomaticWork(delivery: RotationDelivery, workId: String): Boolean = mutex.withLock {
		val settings = settingsRepository.settings.first()
		val state = scheduleRepository.read()
		val currentBootCount = currentBootCount()
		val now = SystemClock.elapsedRealtime()
		val recoveredState = ensureSuccessorLocked(settings, state, delivery, currentBootCount, now)
		val claimAction = automaticWorkClaimAction(settings.autoUpdateEnabled, recoveredState, delivery, currentBootCount, now, workId)
		if (claimAction == AutomaticWorkClaimAction.REJECT) {
			return@withLock false
		}

		val claimedState = if (claimAction == AutomaticWorkClaimAction.CLAIM) {
			recoveredState.copy(inFlightDueElapsedRealtime = delivery.dueElapsedRealtime, inFlightWorkId = workId)
		} else {
			recoveredState
		}

		if (claimedState != recoveredState) {
			scheduleRepository.write(claimedState)
		}

		true
	}

	internal suspend fun completeAutomaticWork(delivery: RotationDelivery, workId: String) = mutex.withLock {
		val state = scheduleRepository.read()
		if (!isOwnedInFlightWork(state, delivery, workId)) {
			return@withLock
		}

		val lastCompletedDue = maxOf(state.lastCompletedDueElapsedRealtime ?: Long.MIN_VALUE, delivery.dueElapsedRealtime)
		scheduleRepository.write(
			state.copy(
				inFlightDueElapsedRealtime = null,
				inFlightWorkId = null,
				lastCompletedDueElapsedRealtime = lastCompletedDue
			)
		)
	}

	private suspend fun reconcileLocked(trigger: RotationReconciliationTrigger) {
		val settings = settingsRepository.settings.first()
		val state = scheduleRepository.read()
		val currentBootCount = currentBootCount()
		when (
			rotationReconciliationAction(
				settings.autoUpdateEnabled,
				settings.autoStartOnBoot,
				trigger,
				state,
				settings.updateIntervalMinutes,
				currentBootCount
			)
		) {
			RotationReconciliationAction.INACTIVE -> cancelAlarm()
			RotationReconciliationAction.ARM_EXISTING -> armAlarm(
				RotationDelivery(
					requireNotNull(state.nextDueElapsedRealtime),
					state.generation,
					currentBootCount
				)
			)
			RotationReconciliationAction.INITIALIZE -> initializeLocked(settings.updateIntervalMinutes, state)
		}
	}

	private suspend fun initializeLocked(intervalMinutes: Int, previousState: RotationScheduleState) {
		val bootCount = currentBootCount()
		val generation = previousState.generation + 1
		val nextDue = SystemClock.elapsedRealtime() + intervalMinutes * MILLIS_PER_MINUTE
		val state = RotationScheduleState(
			nextDueElapsedRealtime = nextDue,
			intervalMinutes = intervalMinutes,
			bootCount = bootCount,
			generation = generation
		)

		scheduleRepository.write(state)
		armAlarm(RotationDelivery(nextDue, generation, bootCount))
	}

	private suspend fun ensureSuccessorLocked(
		settings: AppSettings,
		state: RotationScheduleState,
		delivery: RotationDelivery,
		currentBootCount: Int,
		nowElapsedRealtime: Long
	): RotationScheduleState {
		if (!isActiveAutomaticDelivery(settings.autoUpdateEnabled, state, delivery, currentBootCount)) {
			return state
		}

		val nextDue = successorDueForWork(
			state.nextDueElapsedRealtime,
			delivery.dueElapsedRealtime,
			(state.intervalMinutes ?: settings.updateIntervalMinutes) * MILLIS_PER_MINUTE,
			nowElapsedRealtime
		) ?: return state
		val successorState = if (nextDue != state.nextDueElapsedRealtime) {
			state.copy(nextDueElapsedRealtime = nextDue)
		} else {
			state
		}

		if (successorState != state) {
			scheduleRepository.write(successorState)
		}

		armAlarm(RotationDelivery(nextDue, state.generation, currentBootCount))

		return successorState
	}

	private fun armAlarm(delivery: RotationDelivery, triggerElapsedRealtime: Long = delivery.dueElapsedRealtime) {
		alarmManager.setAndAllowWhileIdle(
			AlarmManager.ELAPSED_REALTIME_WAKEUP,
			triggerElapsedRealtime,
			alarmPendingIntent(delivery)
		)
	}

	private fun cancelAlarm() {
		alarmManager.cancel(alarmPendingIntent(RotationDelivery(0, 0, 0)))
	}

	private fun alarmPendingIntent(delivery: RotationDelivery) = PendingIntent.getBroadcast(
		context,
		ALARM_REQUEST_CODE,
		Intent(context, RotationAlarmReceiver::class.java)
			.setAction(ACTION_ROTATE)
			.putExtra(EXTRA_DUE_ELAPSED_REALTIME, delivery.dueElapsedRealtime)
			.putExtra(EXTRA_GENERATION, delivery.generation)
			.putExtra(EXTRA_BOOT_COUNT, delivery.bootCount),
		PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
	)

	private suspend fun enqueueAutomaticWork(delivery: RotationDelivery) {
		val work = OneTimeWorkRequestBuilder<RotationWorker>()
			.addTag(AUTOMATIC_WORK_TAG)
			.setInputData(
				workDataOf(
					EXTRA_DUE_ELAPSED_REALTIME to delivery.dueElapsedRealtime,
					EXTRA_GENERATION to delivery.generation,
					EXTRA_BOOT_COUNT to delivery.bootCount
				)
			)
			.build()

		workManager.enqueueUniqueWork(automaticWorkName(delivery), ExistingWorkPolicy.KEEP, work)
			.await()
	}

	private fun currentBootCount() = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, 0)

	companion object {
		internal const val ACTION_ROTATE = "xyz.attacktive.wallhavend.ROTATE"
		internal const val EXTRA_DUE_ELAPSED_REALTIME = "due_elapsed_realtime"
		internal const val EXTRA_GENERATION = "generation"
		internal const val EXTRA_BOOT_COUNT = "boot_count"
		internal const val AUTOMATIC_WORK_TAG = "automatic_wallpaper_rotation"
		private const val ALARM_REQUEST_CODE = 100
		private const val MILLIS_PER_MINUTE = 60_000L
		private const val HANDOFF_RECOVERY_DELAY_MILLIS = 60_000L
	}
}

internal data class RotationDelivery(val dueElapsedRealtime: Long, val generation: Long, val bootCount: Int)

internal enum class RotationReconciliationTrigger {
	FOREGROUND,
	BOOT,
	PACKAGE_REPLACED
}

@VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
internal enum class RotationReconciliationAction {
	INACTIVE,
	ARM_EXISTING,
	INITIALIZE
}

@VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
internal enum class AutomaticWorkClaimAction {
	REJECT,
	CLAIM,
	RESUME
}

@VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
internal fun rotationReconciliationAction(
	autoUpdateEnabled: Boolean,
	autoStartOnBoot: Boolean,
	trigger: RotationReconciliationTrigger,
	state: RotationScheduleState,
	intervalMinutes: Int,
	currentBootCount: Int
): RotationReconciliationAction {
	if (!autoUpdateEnabled) {
		return RotationReconciliationAction.INACTIVE
	}

	val hasDeadline = state.nextDueElapsedRealtime != null
	val sameBoot = state.bootCount == currentBootCount
	if (hasReusableSchedule(state, intervalMinutes, currentBootCount)) {
		return RotationReconciliationAction.ARM_EXISTING
	}

	return recoveryReconciliationAction(trigger, autoStartOnBoot, hasDeadline, sameBoot)
}

private fun hasReusableSchedule(state: RotationScheduleState, intervalMinutes: Int, currentBootCount: Int) =
	state.nextDueElapsedRealtime != null && state.bootCount == currentBootCount && state.intervalMinutes == intervalMinutes

private fun recoveryReconciliationAction(
	trigger: RotationReconciliationTrigger,
	autoStartOnBoot: Boolean,
	hasDeadline: Boolean,
	sameBoot: Boolean
) = when (trigger) {
	RotationReconciliationTrigger.FOREGROUND -> RotationReconciliationAction.INITIALIZE
	RotationReconciliationTrigger.BOOT -> initializationAction(autoStartOnBoot)
	RotationReconciliationTrigger.PACKAGE_REPLACED -> initializationAction(!hasDeadline || sameBoot || autoStartOnBoot)
}

private fun initializationAction(enabled: Boolean) = if (enabled) {
	RotationReconciliationAction.INITIALIZE
} else {
	RotationReconciliationAction.INACTIVE
}

@VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
internal fun isCurrentAlarmDelivery(
	autoUpdateEnabled: Boolean,
	state: RotationScheduleState,
	delivery: RotationDelivery,
	currentBootCount: Int
): Boolean {
	val scheduleMatches = state.nextDueElapsedRealtime == delivery.dueElapsedRealtime

	return scheduleMatches && isActiveAutomaticDelivery(autoUpdateEnabled, state, delivery, currentBootCount)
}

@VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
internal fun automaticWorkClaimAction(
	autoUpdateEnabled: Boolean,
	state: RotationScheduleState,
	delivery: RotationDelivery,
	currentBootCount: Int,
	nowElapsedRealtime: Long,
	workId: String
): AutomaticWorkClaimAction {
	if (!isActiveAutomaticDelivery(autoUpdateEnabled, state, delivery, currentBootCount)) {
		return AutomaticWorkClaimAction.REJECT
	}

	if (delivery.dueElapsedRealtime > nowElapsedRealtime || isCompletedDue(state, delivery)) {
		return AutomaticWorkClaimAction.REJECT
	}

	if (state.inFlightDueElapsedRealtime == null) {
		return AutomaticWorkClaimAction.CLAIM
	}

	return if (isOwnedInFlightWork(state, delivery, workId)) {
		AutomaticWorkClaimAction.RESUME
	} else {
		AutomaticWorkClaimAction.REJECT
	}
}

private fun isActiveAutomaticDelivery(
	autoUpdateEnabled: Boolean,
	state: RotationScheduleState,
	delivery: RotationDelivery,
	currentBootCount: Int
) = autoUpdateEnabled && state.generation == delivery.generation && state.bootCount == delivery.bootCount && delivery.bootCount == currentBootCount

private fun isCompletedDue(state: RotationScheduleState, delivery: RotationDelivery) =
	state.lastCompletedDueElapsedRealtime?.let { it >= delivery.dueElapsedRealtime } == true

private fun isOwnedInFlightWork(state: RotationScheduleState, delivery: RotationDelivery, workId: String) =
	state.inFlightDueElapsedRealtime == delivery.dueElapsedRealtime && state.inFlightWorkId == workId

@VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
internal fun automaticWorkName(delivery: RotationDelivery) =
	"${RotationScheduler.AUTOMATIC_WORK_TAG}:${delivery.bootCount}:${delivery.generation}:${delivery.dueElapsedRealtime}"

@VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
internal fun successorDueForWork(
	nextDueElapsedRealtime: Long?,
	deliveryDueElapsedRealtime: Long,
	intervalMillis: Long,
	nowElapsedRealtime: Long
): Long? {
	if (nextDueElapsedRealtime == null) {
		return null
	}

	return if (nextDueElapsedRealtime <= deliveryDueElapsedRealtime) {
		nextDueAfter(deliveryDueElapsedRealtime, intervalMillis, nowElapsedRealtime)
	} else {
		nextDueElapsedRealtime
	}
}

@VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
internal fun nextDueAfter(dueElapsedRealtime: Long, intervalMillis: Long, nowElapsedRealtime: Long): Long {
	require(intervalMillis > 0)
	if (nowElapsedRealtime < dueElapsedRealtime) {
		return dueElapsedRealtime
	}

	val intervalsElapsed = (nowElapsedRealtime - dueElapsedRealtime) / intervalMillis + 1

	return dueElapsedRealtime + intervalsElapsed * intervalMillis
}
