package xyz.attacktive.wallhavend.domain.service

import kotlinx.coroutines.CancellationException
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

class RotationWorker(appContext: Context, workerParameters: WorkerParameters): CoroutineWorker(appContext, workerParameters) {
	override suspend fun doWork(): Result {
		val delivery = rotationDelivery()
		if (!delivery.isValid()) {
			return Result.success()
		}

		val entryPoint = EntryPointAccessors.fromApplication(applicationContext, RotationWorkerEntryPoint::class.java)
		val scheduler = entryPoint.rotationScheduler()
		val workId = id.toString()

		return when (prepare(scheduler, delivery, workId)) {
			PreparationResult.SKIP -> Result.success()
			PreparationResult.RETRY -> Result.retry()
			PreparationResult.READY -> executeAndComplete(entryPoint, scheduler, delivery, workId)
		}
	}

	private fun rotationDelivery() = RotationDelivery(
		inputData.getLong(RotationScheduler.EXTRA_DUE_ELAPSED_REALTIME, -1),
		inputData.getLong(RotationScheduler.EXTRA_GENERATION, -1),
		inputData.getInt(RotationScheduler.EXTRA_BOOT_COUNT, -1)
	)

	private suspend fun prepare(scheduler: RotationScheduler, delivery: RotationDelivery, workId: String): PreparationResult = try {
		if (scheduler.prepareAutomaticWork(delivery, workId)) {
			PreparationResult.READY
		} else {
			PreparationResult.SKIP
		}
	} catch (error: CancellationException) {
		throw error
	} catch (_: Exception) {
		PreparationResult.RETRY
	}

	private suspend fun executeAndComplete(entryPoint: RotationWorkerEntryPoint, scheduler: RotationScheduler, delivery: RotationDelivery, workId: String): Result {
		val executionSucceeded = execute(entryPoint)

		return complete(scheduler, delivery, workId, executionSucceeded)
	}

	private suspend fun execute(entryPoint: RotationWorkerEntryPoint): Boolean = try {
		val executor = entryPoint.wallpaperRotationExecutor()
		executor.restoreState()
		executor.performUpdate()

		true
	} catch (error: CancellationException) {
		throw error
	} catch (_: Exception) {
		false
	}

	private suspend fun complete(scheduler: RotationScheduler, delivery: RotationDelivery, workId: String, executionSucceeded: Boolean): Result = try {
		scheduler.completeAutomaticWork(delivery, workId)
		if (executionSucceeded) {
			Result.success()
		} else {
			Result.failure()
		}
	} catch (error: CancellationException) {
		throw error
	} catch (_: Exception) {
		Result.retry()
	}

	private fun RotationDelivery.isValid() = dueElapsedRealtime >= 0 && generation >= 0 && bootCount >= 0

	private enum class PreparationResult {
		SKIP,
		RETRY,
		READY
	}
}

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface RotationWorkerEntryPoint {
	fun rotationScheduler(): RotationScheduler
	fun wallpaperRotationExecutor(): WallpaperRotationExecutor
}
