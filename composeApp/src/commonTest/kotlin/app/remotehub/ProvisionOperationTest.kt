package app.remotehub

import app.remotehub.app.ProvisionOperation
import app.remotehub.app.ProvisionPhase
import app.remotehub.domain.ProvisionStep
import app.remotehub.domain.StepStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProvisionOperationTest {

    private fun steps(vararg statuses: Pair<String, StepStatus>) =
        statuses.map { (id, status) -> ProvisionStep(id, status, null, null) }

    private val allIds = listOf("account", "repo", "secrets", "dispatch", "machine", "network", "ready")

    @Test
    fun idleIsNeitherActiveNorTerminal() {
        val op = ProvisionOperation()
        assertFalse(op.isActive)
        assertFalse(op.isTerminal)
        assertFalse(op.hasRun)
        assertEquals(0f, op.progressFraction)
    }

    @Test
    fun startingMovesToValidatingAndCountsTheAttempt() {
        val op = assertNotNull(ProvisionOperation().start("win-1", 360, 1_000L))
        assertEquals(ProvisionPhase.Validating, op.phase)
        assertTrue(op.isActive)
        assertEquals("win-1", op.serverName)
        assertEquals(360, op.durationMinutes)
        assertEquals(1, op.attempt)
        assertEquals(1_000L, op.startedAtEpochMs)
    }

    @Test
    fun aSecondConcurrentStartIsRefused() {
        val first = assertNotNull(ProvisionOperation().start("win-1", 360, 1_000L))
        assertNull(first.start("win-2", 120, 2_000L), "single-flight: one creation at a time")
        assertEquals("win-1", first.serverName)
        assertEquals(1, first.attempt)
    }

    @Test
    fun aNewAttemptIsAllowedAfterFailure() {
        val failed = assertNotNull(ProvisionOperation().start("win-1", 360, 1_000L))
            .fail("boom", "provision", 2_000L)
        val retry = assertNotNull(failed.start("win-1", 360, 3_000L))
        assertEquals(2, retry.attempt)
        assertNull(retry.errorMessage)
        assertTrue(retry.steps.isEmpty())
    }

    @Test
    fun phasesFollowTheRealWorkflowStages() {
        var op = assertNotNull(ProvisionOperation().start("win-1", 360, 0L))
        val expected = mapOf(
            "account" to ProvisionPhase.Initializing,
            "repo" to ProvisionPhase.CreatingRepository,
            "secrets" to ProvisionPhase.CreatingRepository,
            "dispatch" to ProvisionPhase.RunningWorkflow,
            "machine" to ProvisionPhase.RunningWorkflow,
            "network" to ProvisionPhase.Connecting,
            "ready" to ProvisionPhase.Connecting,
        )
        val statuses = allIds.associateWith { StepStatus.Pending }.toMutableMap()
        allIds.forEach { id ->
            statuses[id] = StepStatus.Running
            op = op.withSteps(statuses.map { (key, value) -> ProvisionStep(key, value, null, null) })
            assertEquals(expected.getValue(id), op.phase, "step $id")
            statuses[id] = StepStatus.Done
        }
    }

    @Test
    fun phasesNeverRewindWhenAnEarlierStepReportsAgain() {
        var op = assertNotNull(ProvisionOperation().start("win-1", 360, 0L))
        op = op.withSteps(
            steps(
                "account" to StepStatus.Done,
                "repo" to StepStatus.Done,
                "secrets" to StepStatus.Done,
                "dispatch" to StepStatus.Running,
            ),
        )
        assertEquals(ProvisionPhase.RunningWorkflow, op.phase)
        // A late re-report of an early step must not pull the phase backwards.
        op = op.withSteps(steps("account" to StepStatus.Running, "dispatch" to StepStatus.Running))
        assertEquals(ProvisionPhase.RunningWorkflow, op.phase)
    }

    @Test
    fun anUnknownStepIdKeepsTheCurrentPhase() {
        assertEquals(
            ProvisionPhase.RunningWorkflow,
            ProvisionPhase.forStep("some-new-backend-step", ProvisionPhase.RunningWorkflow),
        )
    }

    @Test
    fun progressAdvancesAndCountsRunningAsHalfAStage() {
        val op = assertNotNull(ProvisionOperation().start("win-1", 360, 0L)).withSteps(
            steps(
                "account" to StepStatus.Done,
                "repo" to StepStatus.Done,
                "secrets" to StepStatus.Running,
                "dispatch" to StepStatus.Pending,
            ),
        )
        assertEquals(0.625f, op.progressFraction)
        assertEquals("secrets", op.runningStep?.id)
    }

    @Test
    fun progressIsBoundedAndMonotonic() {
        var op = assertNotNull(ProvisionOperation().start("win-1", 360, 0L))
        var previous = 0f
        val statuses = allIds.associateWith { StepStatus.Pending }.toMutableMap()
        allIds.forEach { id ->
            statuses[id] = StepStatus.Running
            op = op.withSteps(statuses.map { (k, v) -> ProvisionStep(k, v, null, null) })
            assertTrue(op.progressFraction >= previous, "progress went backwards at $id")
            assertTrue(op.progressFraction in 0f..1f)
            previous = op.progressFraction
            statuses[id] = StepStatus.Done
        }
        op = op.withSteps(statuses.map { (k, v) -> ProvisionStep(k, v, null, null) })
        assertEquals(1f, op.progressFraction)
    }

    @Test
    fun completionMarksEveryRemainingStepDoneAndRecordsTheServer() {
        val op = assertNotNull(ProvisionOperation().start("win-1", 360, 0L))
            .withSteps(steps("account" to StepStatus.Done, "ready" to StepStatus.Running))
            .complete("srv-1", 129, 5_000L)
        assertEquals(ProvisionPhase.Completed, op.phase)
        assertTrue(op.isTerminal)
        assertFalse(op.isActive)
        assertEquals("srv-1", op.serverId)
        assertEquals(129, op.runNumber)
        assertEquals(5_000L, op.finishedAtEpochMs)
        assertTrue(op.steps.all { it.status == StepStatus.Done })
        assertEquals(1f, op.progressFraction)
    }

    @Test
    fun failureMarksTheRunningStepFailedAndKeepsTheReason() {
        val op = assertNotNull(ProvisionOperation().start("win-1", 360, 0L))
            .withSteps(steps("account" to StepStatus.Done, "machine" to StepStatus.Running))
            .fail("runner timed out", "provision", 9_000L)
        assertEquals(ProvisionPhase.Failed, op.phase)
        assertEquals("runner timed out", op.errorMessage)
        assertEquals("provision", op.errorCode)
        assertEquals("machine", op.failedStep?.id)
        assertEquals(StepStatus.Failed, op.steps.last().status)
    }

    @Test
    fun cancelReturnsRunningStepsToPending() {
        val op = assertNotNull(ProvisionOperation().start("win-1", 360, 0L))
            .withSteps(steps("account" to StepStatus.Done, "repo" to StepStatus.Running))
            .cancel(4_000L)
        assertEquals(ProvisionPhase.Cancelled, op.phase)
        assertEquals(StepStatus.Pending, op.steps.last().status)
        assertEquals(StepStatus.Done, op.steps.first().status)
    }

    @Test
    fun updatesAreIgnoredOnceTheAttemptIsFinished() {
        val done = assertNotNull(ProvisionOperation().start("win-1", 360, 0L)).complete("s", 1, 1L)
        assertEquals(done, done.withSteps(steps("account" to StepStatus.Running)))
        assertEquals(done, done.withLog("late line"))
        assertEquals(done, done.acknowledge().let { if (it.isActive) done else done })
    }

    @Test
    fun acknowledgingClearsAFinishedAttemptButNotARunningOne() {
        val running = assertNotNull(ProvisionOperation().start("win-1", 360, 0L))
        assertEquals(running, running.acknowledge())
        val failed = running.fail("x", null, 1L)
        val cleared = failed.acknowledge()
        assertEquals(ProvisionPhase.Idle, cleared.phase)
        assertEquals(1, cleared.attempt, "the attempt counter is history, not state")
    }

    @Test
    fun theLogIsBounded() {
        var op = assertNotNull(ProvisionOperation().start("win-1", 360, 0L))
        repeat(ProvisionOperation.MAX_LOG + 25) { op = op.withLog("line $it") }
        assertEquals(ProvisionOperation.MAX_LOG, op.log.size)
        assertEquals("line ${ProvisionOperation.MAX_LOG + 24}", op.log.last())
    }
}
