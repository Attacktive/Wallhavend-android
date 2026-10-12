package xyz.attacktive.wallhavend

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.attacktive.wallhavend.domain.repository.RotationScheduleState
import xyz.attacktive.wallhavend.domain.service.AutomaticWorkClaimAction
import xyz.attacktive.wallhavend.domain.service.RotationDelivery
import xyz.attacktive.wallhavend.domain.service.RotationReconciliationAction
import xyz.attacktive.wallhavend.domain.service.RotationReconciliationTrigger
import xyz.attacktive.wallhavend.domain.service.automaticWorkClaimAction
import xyz.attacktive.wallhavend.domain.service.automaticWorkName
import xyz.attacktive.wallhavend.domain.service.isCurrentAlarmDelivery
import xyz.attacktive.wallhavend.domain.service.nextDueAfter
import xyz.attacktive.wallhavend.domain.service.rotationReconciliationAction
import xyz.attacktive.wallhavend.domain.service.successorDueForWork

class RotationSchedulerTest {
	@Test
	fun `overdue delivery advances to one phase-aligned future deadline`() {
		assertEquals(400L, nextDueAfter(dueElapsedRealtime = 100, intervalMillis = 100, nowElapsedRealtime = 350))
	}

	@Test
	fun `delivery exactly on a boundary advances to the following slot`() {
		assertEquals(400L, nextDueAfter(dueElapsedRealtime = 300, intervalMillis = 100, nowElapsedRealtime = 300))
	}

	@Test
	fun `early delivery keeps the original deadline`() {
		assertEquals(300L, nextDueAfter(dueElapsedRealtime = 300, intervalMillis = 100, nowElapsedRealtime = 250))
	}

	@Test
	fun `same boot and interval preserve the existing deadline`() {
		assertEquals(
			RotationReconciliationAction.ARM_EXISTING,
			reconciliationAction(
				trigger = RotationReconciliationTrigger.FOREGROUND,
				state = scheduleState()
			)
		)
	}

	@Test
	fun `foreground reconciliation resets a changed interval`() {
		assertEquals(
			RotationReconciliationAction.INITIALIZE,
			reconciliationAction(
				trigger = RotationReconciliationTrigger.FOREGROUND,
				state = scheduleState(intervalMinutes = 30)
			)
		)
	}

	@Test
	fun `boot recovery honors auto start setting`() {
		val previousBoot = scheduleState(bootCount = 4)

		assertEquals(
			RotationReconciliationAction.INITIALIZE,
			reconciliationAction(
				trigger = RotationReconciliationTrigger.BOOT,
				state = previousBoot,
				autoStartOnBoot = true
			)
		)
		assertEquals(
			RotationReconciliationAction.INACTIVE,
			reconciliationAction(
				trigger = RotationReconciliationTrigger.BOOT,
				state = previousBoot,
				autoStartOnBoot = false
			)
		)
	}

	@Test
	fun `package replacement does not bypass disabled boot recovery`() {
		assertEquals(
			RotationReconciliationAction.INACTIVE,
			reconciliationAction(
				trigger = RotationReconciliationTrigger.PACKAGE_REPLACED,
				state = scheduleState(bootCount = 4),
				autoStartOnBoot = false
			)
		)
	}

	@Test
	fun `package replacement bootstraps a legacy enabled install`() {
		assertEquals(
			RotationReconciliationAction.INITIALIZE,
			reconciliationAction(
				trigger = RotationReconciliationTrigger.PACKAGE_REPLACED,
				state = RotationScheduleState(),
				autoStartOnBoot = false
			)
		)
	}

	@Test
	fun `disabled scheduling is inert`() {
		assertEquals(
			RotationReconciliationAction.INACTIVE,
			reconciliationAction(
				trigger = RotationReconciliationTrigger.FOREGROUND,
				state = scheduleState(),
				autoUpdateEnabled = false
			)
		)
	}

	@Test
	fun `stale alarm deliveries are rejected`() {
		val state = scheduleState(generation = 3)

		assertTrue(isCurrentAlarmDelivery(true, state, delivery(), 5))
		assertFalse(isCurrentAlarmDelivery(false, state, delivery(), 5))
		assertFalse(isCurrentAlarmDelivery(true, state, delivery(dueElapsedRealtime = 900), 5))
		assertFalse(isCurrentAlarmDelivery(true, state, delivery(generation = 2), 5))
		assertFalse(isCurrentAlarmDelivery(true, state, delivery(bootCount = 4), 5))
	}

	@Test
	fun `new automatic work claims a ready slot`() {
		assertEquals(AutomaticWorkClaimAction.CLAIM, claimAction(scheduleState(generation = 3), workId = "worker-a"))
	}

	@Test
	fun `interrupted automatic work resumes with the same durable work id`() {
		val state = scheduleState(generation = 3).copy(inFlightDueElapsedRealtime = 1_000, inFlightWorkId = "worker-a")

		assertEquals(AutomaticWorkClaimAction.RESUME, claimAction(state, workId = "worker-a"))
		assertEquals(AutomaticWorkClaimAction.REJECT, claimAction(state, workId = "worker-b"))
	}

	@Test
	fun `completed automatic work cannot reclaim its slot`() {
		val state = scheduleState(generation = 3).copy(lastCompletedDueElapsedRealtime = 1_000)

		assertEquals(AutomaticWorkClaimAction.REJECT, claimAction(state, workId = "worker-a"))
	}

	@Test
	fun `different schedule slots use different unique work names`() {
		assertNotEquals(automaticWorkName(delivery(dueElapsedRealtime = 1_000)), automaticWorkName(delivery(dueElapsedRealtime = 2_000)))
		assertNotEquals(automaticWorkName(delivery(generation = 3)), automaticWorkName(delivery(generation = 4)))
		assertNotEquals(automaticWorkName(delivery(bootCount = 5)), automaticWorkName(delivery(bootCount = 6)))
	}

	@Test
	fun `redelivery of the same schedule slot uses the same unique work name`() {
		assertEquals(automaticWorkName(delivery()), automaticWorkName(delivery()))
	}

	@Test
	fun `worker recovery advances an uncommitted successor deadline`() {
		assertEquals(1_200L, successorDueForWork(nextDueElapsedRealtime = 1_000, deliveryDueElapsedRealtime = 1_000, intervalMillis = 100, nowElapsedRealtime = 1_150))
	}

	@Test
	fun `worker recovery preserves an already committed successor deadline`() {
		assertEquals(1_300L, successorDueForWork(nextDueElapsedRealtime = 1_300, deliveryDueElapsedRealtime = 1_000, intervalMillis = 100, nowElapsedRealtime = 1_150))
	}

	private fun claimAction(state: RotationScheduleState, workId: String) = automaticWorkClaimAction(
		autoUpdateEnabled = true,
		state = state,
		delivery = delivery(),
		currentBootCount = 5,
		nowElapsedRealtime = 1_000,
		workId = workId
	)

	private fun reconciliationAction(
		trigger: RotationReconciliationTrigger,
		state: RotationScheduleState,
		autoUpdateEnabled: Boolean = true,
		autoStartOnBoot: Boolean = true
	) = rotationReconciliationAction(
		autoUpdateEnabled = autoUpdateEnabled,
		autoStartOnBoot = autoStartOnBoot,
		trigger = trigger,
		state = state,
		intervalMinutes = 60,
		currentBootCount = 5
	)

	private fun scheduleState(
		nextDueElapsedRealtime: Long = 1_000,
		intervalMinutes: Int = 60,
		bootCount: Int = 5,
		generation: Long = 1
	) = RotationScheduleState(
		nextDueElapsedRealtime = nextDueElapsedRealtime,
		intervalMinutes = intervalMinutes,
		bootCount = bootCount,
		generation = generation
	)

	private fun delivery(
		dueElapsedRealtime: Long = 1_000,
		generation: Long = 3,
		bootCount: Int = 5
	) = RotationDelivery(dueElapsedRealtime, generation, bootCount)
}
