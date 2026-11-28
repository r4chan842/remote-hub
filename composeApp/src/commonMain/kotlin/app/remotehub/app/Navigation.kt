package app.remotehub.app

/**
 * Navigation as data, not as a mutable controller.
 *
 * The old model kept a single `screen` enum, so every destination change destroyed the
 * previous one: leaving Progress for Settings and coming back could not restore the
 * screen, and the fix had been a `settingsReturn` field that only handled one hop. This
 * model gives every tab its own back stack, which is the pattern the Material 3
 * navigation guidance describes for a navigation bar: switching tabs preserves each
 * tab's history, and Back walks the current tab before returning to the start tab.
 *
 * Being an immutable value inside [AppState] means it survives recomposition, is
 * trivially snapshotable for process death, and is unit-testable without a UI.
 */
enum class Tab { Home, Servers, Network, Settings;

    companion object {
        val start = Home
    }
}

/** Every place the app can show. Payloads travel with the route, not in global state. */
sealed interface Route {
    data object Setup : Route
    data object Create : Route
    data object Progress : Route
    data object Servers : Route
    data object Network : Route
    data object Settings : Route
    data object Logs : Route
    data class Detail(val serverId: String) : Route
    data class Guide(val guideId: String) : Route
    data class Remote(val serverId: String) : Route

    /** The tab this route belongs to, used when restoring a snapshot. */
    val tab: Tab
        get() = when (this) {
            Setup, Create, Progress -> Tab.Home
            Servers, is Detail -> Tab.Servers
            Network -> Tab.Network
            Settings, is Guide, Logs -> Tab.Settings
            is Remote -> Tab.Servers
        }

    /** A root is what a tab shows when its stack has one entry. */
    val isRoot: Boolean
        get() = when (this) {
            Setup, Servers, Network, Settings -> true
            else -> false
        }

    fun encode(): String = when (this) {
        Setup -> "setup"
        Create -> "create"
        Progress -> "progress"
        Servers -> "servers"
        Network -> "network"
        Settings -> "settings"
        Logs -> "logs"
        is Detail -> "detail:$serverId"
        is Guide -> "guide:$guideId"
        is Remote -> "remote:$serverId"
    }

    companion object {
        fun decode(raw: String): Route? = when {
            raw == "setup" -> Setup
            raw == "create" -> Create
            raw == "progress" -> Progress
            raw == "servers" -> Servers
            raw == "network" -> Network
            raw == "settings" -> Settings
            raw == "logs" -> Logs
            raw.startsWith("detail:") -> Detail(raw.removePrefix("detail:"))
            raw.startsWith("guide:") -> Guide(raw.removePrefix("guide:"))
            raw.startsWith("remote:") -> Remote(raw.removePrefix("remote:"))
            else -> null
        }

        fun rootOf(tab: Tab): Route = when (tab) {
            Tab.Home -> Setup
            Tab.Servers -> Servers
            Tab.Network -> Network
            Tab.Settings -> Settings
        }
    }
}

data class NavState(
    val tab: Tab = Tab.start,
    val stacks: Map<Tab, List<Route>> = Tab.entries.associateWith { listOf(Route.rootOf(it)) },
) {
    init {
        require(stacks.isNotEmpty()) { "navigation needs at least one stack" }
        Tab.entries.forEach { entry ->
            require(!stacks[entry].isNullOrEmpty()) { "tab $entry has an empty stack" }
        }
    }

    val stack: List<Route> get() = stacks.getValue(tab)

    val route: Route get() = stack.last()

    /** True when Back would leave the app rather than move within it. */
    val atStart: Boolean get() = tab == Tab.start && stack.size == 1

    val canGoBack: Boolean get() = !atStart

    /**
     * Switching tabs never clears the destination tab's history. Re-selecting the tab
     * you are already on pops it to its root, which is what the Material navigation
     * bar does.
     */
    fun selectTab(next: Tab): NavState =
        if (next == tab) copy(stacks = stacks + (next to listOf(Route.rootOf(next))))
        else copy(tab = next)

    /** Push a destination onto the current tab. Pushing the same route twice is a no-op. */
    fun push(next: Route): NavState {
        if (next == route) return this
        val target = if (next.isRoot) next.tab else tab
        val base = if (next.isRoot) listOf(next) else stacks.getValue(target) + next
        return copy(tab = target, stacks = stacks + (target to base))
    }

    /** Replace the top entry, for a step that supersedes the current one (Create → Progress). */
    fun replaceTop(next: Route): NavState {
        val replaced = stack.dropLast(1) + next
        return copy(stacks = stacks + (tab to replaced))
    }

    /**
     * Pop one entry. At a tab root, Back returns to the start tab instead of exiting,
     * matching Android's behaviour for a navigation bar. Returns null only when the
     * app should actually close.
     */
    fun pop(): NavState? = when {
        stack.size > 1 -> copy(stacks = stacks + (tab to stack.dropLast(1)))
        tab != Tab.start -> copy(tab = Tab.start)
        else -> null
    }

    /** Drop a tab's history back to its root, e.g. after a server is deleted. */
    fun resetTab(target: Tab): NavState =
        copy(stacks = stacks + (target to listOf(Route.rootOf(target))))

    /** Remove any route that points at a server which no longer exists. */
    fun pruneServers(existing: Set<String>): NavState {
        val cleaned = stacks.mapValues { (_, entries) ->
            entries.filter { entry ->
                when (entry) {
                    is Route.Detail -> entry.serverId in existing
                    is Route.Remote -> entry.serverId in existing
                    else -> true
                }
            }.ifEmpty { listOf(Route.rootOf(tab)) }
        }
        // A stack must never be empty; fall back to the tab's own root.
        val safe = cleaned.map { (key, entries) ->
            key to entries.ifEmpty { listOf(Route.rootOf(key)) }
        }.toMap()
        return copy(stacks = safe)
    }

    fun encode(): String =
        tab.name + "|" + Tab.entries.joinToString(";") { entry ->
            entry.name + "=" + stacks.getValue(entry).joinToString(",") { it.encode() }
        }

    companion object {
        fun decode(raw: String?): NavState {
            if (raw.isNullOrBlank()) return NavState()
            return runCatching {
                val parts = raw.split("|", limit = 2)
                val tab = Tab.valueOf(parts[0])
                val stacks = parts[1].split(";").mapNotNull { chunk ->
                    val halves = chunk.split("=", limit = 2)
                    val key = Tab.valueOf(halves[0])
                    val routes = halves[1].split(",").mapNotNull { Route.decode(it) }
                    if (routes.isEmpty()) null else key to routes
                }.toMap()
                val complete = Tab.entries.associateWith { entry ->
                    stacks[entry]?.ifEmpty { null } ?: listOf(Route.rootOf(entry))
                }
                NavState(tab, complete)
            }.getOrDefault(NavState())
        }
    }
}
