package app.remotehub

import app.remotehub.app.NavState
import app.remotehub.app.Route
import app.remotehub.app.Tab
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The scenario the redesign had to fix: start creating a server, wander through other
 * tabs while it runs, come back, and find the operation exactly where it was left.
 */
class NavigationTest {

    @Test
    fun everyTabStartsAtItsOwnRoot() {
        val nav = NavState()
        Tab.entries.forEach { tab ->
            val selected = nav.selectTab(tab)
            assertEquals(Route.rootOf(tab), selected.route, "tab $tab")
            assertEquals(1, selected.stack.size)
        }
    }

    @Test
    fun pushingDetailKeepsTheListUnderneath() {
        val nav = NavState().selectTab(Tab.Servers).push(Route.Detail("s1"))
        assertEquals(listOf(Route.Servers, Route.Detail("s1")), nav.stack)
        assertEquals(Tab.Servers, nav.tab)
    }

    @Test
    fun switchingTabsPreservesEveryStack() {
        var nav = NavState()
            .selectTab(Tab.Servers)
            .push(Route.Detail("s1"))
        nav = nav.selectTab(Tab.Settings).push(Route.Guide("github"))
        nav = nav.selectTab(Tab.Network)

        assertEquals(Route.Network, nav.route)
        assertEquals(listOf(Route.Servers, Route.Detail("s1")), nav.stacks.getValue(Tab.Servers))
        assertEquals(listOf(Route.Settings, Route.Guide("github")), nav.stacks.getValue(Tab.Settings))

        nav = nav.selectTab(Tab.Servers)
        assertEquals(Route.Detail("s1"), nav.route, "returning must restore the detail screen")
    }

    @Test
    fun theProvisionScenarioSurvivesNavigation() {
        // Create → Progress, then leave for three other destinations and come back.
        var nav = NavState().push(Route.Create).replaceTop(Route.Progress)
        assertEquals(Route.Progress, nav.route)

        nav = nav.selectTab(Tab.Settings)
        nav = nav.push(Route.Logs)
        nav = nav.selectTab(Tab.Network)
        nav = nav.selectTab(Tab.Servers).push(Route.Detail("other"))
        nav = nav.selectTab(Tab.Home)

        assertEquals(Route.Progress, nav.route, "the running operation's screen must still be on top")
        assertEquals(Tab.Home, nav.tab)
    }

    @Test
    fun reselectingTheCurrentTabPopsToItsRoot() {
        val nav = NavState().selectTab(Tab.Servers).push(Route.Detail("s1")).selectTab(Tab.Servers)
        assertEquals(Route.Servers, nav.route)
        assertEquals(1, nav.stack.size)
    }

    @Test
    fun backWalksTheStackThenReturnsToTheStartTab() {
        var nav: NavState? = NavState().selectTab(Tab.Servers).push(Route.Detail("s1"))
        nav = nav!!.pop()
        assertEquals(Route.Servers, nav!!.route)

        // At a non-start root, Back goes home rather than exiting.
        nav = nav.pop()
        assertEquals(Tab.Home, nav!!.tab)
        assertEquals(Route.Setup, nav.route)

        // Only the start root exits.
        assertTrue(nav.atStart)
        assertNull(nav.pop())
    }

    @Test
    fun backFromProgressDoesNotExitTheApp() {
        val nav = NavState().push(Route.Create).replaceTop(Route.Progress)
        assertFalse(nav.atStart)
        val popped = assertNotNull(nav.pop())
        assertEquals(Route.Setup, popped.route)
    }

    @Test
    fun pushingTheSameRouteTwiceDoesNotDuplicateIt() {
        val nav = NavState().push(Route.Create).push(Route.Create)
        assertEquals(2, nav.stack.size)
    }

    @Test
    fun replaceTopDoesNotGrowTheStack() {
        val nav = NavState().push(Route.Create)
        val replaced = nav.replaceTop(Route.Progress)
        assertEquals(nav.stack.size, replaced.stack.size)
        assertEquals(listOf(Route.Setup, Route.Progress), replaced.stack)
    }

    @Test
    fun pruningRemovesRoutesForDeletedServers() {
        val nav = NavState()
            .selectTab(Tab.Servers)
            .push(Route.Detail("gone"))
            .pruneServers(setOf("kept"))
        assertEquals(Route.Servers, nav.route)
        assertTrue(nav.stacks.values.all { it.isNotEmpty() })
    }

    @Test
    fun snapshotRoundTripsEveryStack() {
        val nav = NavState()
            .selectTab(Tab.Servers)
            .push(Route.Detail("s1"))
            .selectTab(Tab.Settings)
            .push(Route.Guide("rdp"))
            .push(Route.Logs)
        val restored = NavState.decode(nav.encode())
        assertEquals(nav, restored)
    }

    @Test
    fun corruptSnapshotsFallBackToAValidState() {
        listOf("", "garbage", "Home|", "Nope|Home=setup", "Home|Home=", "Home|Home=unknown")
            .forEach { raw ->
                val nav = NavState.decode(raw)
                assertTrue(nav.stacks.values.all { it.isNotEmpty() }, "raw=$raw")
                assertNotNull(nav.route)
            }
        assertEquals(NavState(), NavState.decode(null))
    }

    @Test
    fun everyRouteEncodesAndDecodes() {
        val routes = listOf(
            Route.Setup, Route.Create, Route.Progress, Route.Servers, Route.Network,
            Route.Settings, Route.Logs, Route.Detail("abc"), Route.Guide("rdp"), Route.Remote("abc"),
        )
        routes.forEach { assertEquals(it, Route.decode(it.encode()), it.encode()) }
        assertNull(Route.decode("nonsense"))
    }

    @Test
    fun aRemoteRouteBelongsToTheServersTab() {
        assertEquals(Tab.Servers, Route.Remote("s1").tab)
        assertEquals(Tab.Home, Route.Progress.tab)
        assertEquals(Tab.Settings, Route.Logs.tab)
    }
}
