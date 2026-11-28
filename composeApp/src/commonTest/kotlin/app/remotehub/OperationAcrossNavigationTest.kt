package app.remotehub

import app.remotehub.app.NavState
import app.remotehub.app.ProvisionOperation
import app.remotehub.app.ProvisionPhase
import app.remotehub.app.Route
import app.remotehub.app.Tab
import app.remotehub.domain.ProvisionStep
import app.remotehub.domain.StepStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The acceptance scenario, exercised on the real state objects rather than on a screen.
 *
 * The requirement was that starting a build, walking through several other screens and
 * coming back must show the true state of the build. Keeping a composable alive is not
 * enough: the operation has to live outside the navigation stack, keep advancing while
 * nothing renders it, and refuse a second concurrent attempt. These tests drive NavState
 * and ProvisionOperation together the way SessionController does.
 */
class OperationAcrossNavigationTest {
    private val stepIds = listOf("account", "repo", "secrets", "dispatch", "machine", "network", "ready")

    private fun steps(vararg statuses: StepStatus) =
        stepIds.mapIndexed { index, id ->
            ProvisionStep(id, statuses.getOrElse(index) { StepStatus.Pending }, null, null)
        }

    private fun running() = assertNotNull(ProvisionOperation().start("windows-desktop", 360, 1_000L))

    @Test
    fun theBuildKeepsAdvancingWhileTheUserIsOnOtherScreens() {
        var nav = NavState().push(Route.Create).replaceTop(Route.Progress)
        var op = running()
        assertEquals(ProvisionPhase.Validating, op.phase)
        val firstFraction = op.progressFraction

        // The user leaves for Settings, opens the log, checks the network, opens an
        // unrelated server, then returns to the Home tab.
        nav = nav.selectTab(Tab.Settings).push(Route.Logs)
        nav = nav.selectTab(Tab.Network)
        nav = nav.selectTab(Tab.Servers).push(Route.Detail("someone-else"))

        // Backend events keep arriving with no screen subscribed to them.
        op = op.withSteps(steps(StepStatus.Done, StepStatus.Done, StepStatus.Running))
        op = op.withLog("repository created")
        nav = nav.selectTab(Tab.Home)

        assertEquals(Route.Progress, nav.route, "returning must land on the running build")
        assertEquals(ProvisionPhase.CreatingRepository, op.phase)
        assertTrue(op.progressFraction > firstFraction, "progress must have moved on while off-screen")
        assertEquals("repository created", op.log.last())
        assertTrue(op.isActive)
    }

    @Test
    fun theResultIsStillThereWhenTheUserComesBackAfterItFinished() {
        var nav = NavState().push(Route.Create).replaceTop(Route.Progress)
        var op = assertNotNull(ProvisionOperation().start("qa-runner", 360, 1_000L))

        nav = nav.selectTab(Tab.Settings)
        op = op.withSteps(steps(*Array(stepIds.size) { StepStatus.Done }))
        op = op.complete("qa-runner", 129, 60_000L)
        nav = nav.selectTab(Tab.Home)

        assertEquals(ProvisionPhase.Completed, op.phase)
        assertEquals("qa-runner", op.serverId)
        assertEquals(129, op.runNumber)
        assertTrue(op.isTerminal)
        // Clearing the result is the user's decision, not a side effect of leaving.
        assertEquals(ProvisionPhase.Idle, op.acknowledge().phase)
    }

    @Test
    fun aFailureIsNotLostByLeavingTheProgressScreen() {
        var nav = NavState().push(Route.Create).replaceTop(Route.Progress)
        var op = running()
        op = op.withSteps(steps(StepStatus.Done, StepStatus.Running))

        nav = nav.selectTab(Tab.Network).push(Route.Logs)
        op = op.fail("workflow run failed: quota exceeded", "provision", 30_000L)
        nav = nav.selectTab(Tab.Home)

        assertEquals(ProvisionPhase.Failed, op.phase)
        assertEquals("workflow run failed: quota exceeded", op.errorMessage)
        assertEquals("repo", op.failedStep?.id)
        assertEquals(Route.Progress, nav.route)
    }

    @Test
    fun navigatingAwayDoesNotStartASecondBuild() {
        val op = running()
        var nav = NavState().push(Route.Create).replaceTop(Route.Progress)
        nav = nav.selectTab(Tab.Servers).push(Route.Create)

        // The controller asks the operation before doing any work, so a second tap on
        // "create" while one is running is refused regardless of which screen asked.
        assertNull(op.start("another-one", 120, 2_000L), "single flight must hold across screens")
        assertEquals(1, op.attempt)
        assertEquals("windows-desktop", op.serverName)
        assertEquals(Route.Create, nav.route, "the refused attempt must not disturb the stack")
    }

    @Test
    fun anInterruptedBuildIsReportedRatherThanLeftSpinningForever() {
        var op = running().withSteps(steps(StepStatus.Done, StepStatus.Running))
        assertTrue(op.isActive)

        // Process death: a restored operation cannot still be running, so the controller
        // marks it failed instead of restoring a spinner that never ends.
        op = op.fail("interrupted", "interrupted", 5_000L)
        assertEquals(ProvisionPhase.Failed, op.phase)
        assertTrue(op.isTerminal)
        assertEquals("interrupted", op.errorCode)
        // A retry is allowed once the attempt is terminal.
        assertEquals(2, assertNotNull(op.start("windows-desktop", 360, 6_000L)).attempt)
    }

    @Test
    fun theNavigationSnapshotRestoresTheScreenThatOwnsTheOperation() {
        val nav = NavState()
            .push(Route.Create)
            .replaceTop(Route.Progress)
            .selectTab(Tab.Settings)
            .push(Route.Logs)
            .selectTab(Tab.Servers)
            .push(Route.Detail("abc"))

        val restored = NavState.decode(nav.encode())
        assertEquals(nav.tab, restored.tab)
        assertEquals(Route.Detail("abc"), restored.route)
        assertEquals(Route.Progress, restored.selectTab(Tab.Home).route)
    }

    @Test
    fun cancellingFromAnotherScreenStillReachesTheOperation() {
        var nav = NavState().push(Route.Create).replaceTop(Route.Progress)
        var op = running().withSteps(steps(StepStatus.Done, StepStatus.Running))

        nav = nav.selectTab(Tab.Settings)
        op = op.cancel(9_000L)
        nav = nav.selectTab(Tab.Home)

        assertEquals(ProvisionPhase.Cancelled, op.phase)
        assertNull(op.runningStep, "nothing may keep running after a cancel")
        assertEquals(Route.Progress, nav.route)
    }
}
