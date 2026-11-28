package app.remotehub.app

import app.remotehub.domain.GitHubAccount
import app.remotehub.domain.NetworkState
import app.remotehub.domain.ProvisionStep
import app.remotehub.domain.ServerSession
import app.remotehub.domain.TailnetStatus
import app.remotehub.server.SessionWorkflow

/**
 * The legacy screen vocabulary, kept as a derived view of [NavState].
 *
 * Screens read this; nothing writes it any more. Navigation is expressed as routes so a
 * destination can carry its own payload and live in a per-tab back stack.
 */
enum class AppScreen { Setup, Create, Progress, Detail, Remote, Servers, Network, Settings, Guide, Logs }

/** The two things that must be real before a server can be created. */
enum class SetupItem { GitHub, Network }

data class CreateForm(
    val name: String = "",
    val durationMinutes: Int = SessionWorkflow.DEFAULT_MINUTES,
)

/** One transient message for the snackbar host. Sequence makes repeats distinguishable. */
data class UiMessage(
    val seq: Long,
    val text: String,
    val isError: Boolean = false,
    val actionLabel: String? = null,
)

data class AppState(
    /** Where the user is. Owns a back stack per tab; see [NavState]. */
    val nav: NavState = NavState(),
    val guideId: String? = null,

    /** Creating a server, as a lifecycle that outlives every screen. */
    val operation: ProvisionOperation = ProvisionOperation(),

    // setup
    val tokenInput: String = "",
    val authKeyInput: String = "",
    val account: GitHubAccount? = null,
    val tailnet: TailnetStatus = TailnetStatus(
        state = NetworkState.Offline,
        tailnetName = "",
        nodeName = "",
        nodeAddress = "",
        relayLabel = "",
        latencyMillis = 0,
        transportLabel = "",
        lastError = null,
    ),

    // create
    val form: CreateForm = CreateForm(),
    val activeServerId: String? = null,

    // detail + remote
    val servers: List<ServerSession> = emptyList(),
    val passwordVisible: Boolean = false,
    val trackpadMode: Boolean = false,
    // The desktop owns the whole screen when this is set; it never disconnects.
    val remoteFullscreen: Boolean = false,
    val remoteConnecting: Boolean = false,
    // The soft keyboard needs a focused editor; this drives the invisible one.
    val keyboardVisible: Boolean = false,
    // Direct touch has no cursor, so the right-click tool arms the next tap.
    val rightClickArmed: Boolean = false,

    // device-wide tailnet
    val deviceVpn: Boolean = false,
    val vpnBusy: Boolean = false,
    val vpnInterface: String = "",

    // settings
    val themeMode: Int = 0,
    val localeTag: String = "fa",
    val expiryAlertEnabled: Boolean = true,
    val warningMinutes: Int = 15,
    val publicRepository: Boolean = true,
    val repositoryName: String = "remote-hub-sessions",

    // diagnostics
    val logs: List<LogEntry> = emptyList(),
    val logFilter: String = "all",
    val logQuery: String = "",
    val expandedLogSeq: Long? = null,
    val errorDetail: String? = null,
    val errorCode: String? = null,
    val crashNotice: String? = null,
    val environmentReport: String = "",

    // transient
    val busy: Boolean = false,
    val error: String? = null,
    val message: UiMessage? = null,
    val highlightItem: SetupItem? = null,
    val appVersion: String = "1.1.0",
) {
    /** Derived, never stored: the route is the truth. */
    val screen: AppScreen
        get() = when (val route = nav.route) {
            Route.Setup -> AppScreen.Setup
            Route.Create -> AppScreen.Create
            Route.Progress -> AppScreen.Progress
            Route.Servers -> AppScreen.Servers
            Route.Network -> AppScreen.Network
            Route.Settings -> AppScreen.Settings
            Route.Logs -> AppScreen.Logs
            is Route.Detail -> AppScreen.Detail
            is Route.Guide -> AppScreen.Guide
            is Route.Remote -> AppScreen.Remote
        }

    val tab: Tab get() = nav.tab

    // Compatibility views over the operation, so a screen that has not been migrated yet
    // still renders the live values instead of a stale copy.
    val provisioning: Boolean get() = operation.isActive
    val steps: List<ProvisionStep> get() = operation.steps
    val progressLog: List<String> get() = operation.log

    val githubReady: Boolean get() = account != null && account.missingScopes.isEmpty()
    val networkReady: Boolean get() = tailnet.state == NetworkState.Online
    val setupComplete: Boolean get() = githubReady && networkReady

    val missingItem: SetupItem?
        get() = when {
            !githubReady -> SetupItem.GitHub
            !networkReady -> SetupItem.Network
            else -> null
        }

    val activeServer: ServerSession? get() = servers.firstOrNull { it.id == activeServerId }

    /** Navigate by pushing a route onto the current tab's stack. */
    fun at(route: Route): AppState = copy(nav = nav.push(route))

    /** Navigate by replacing the current entry, for a step that supersedes it. */
    fun atTop(route: Route): AppState = copy(nav = nav.replaceTop(route))

    fun selectTab(next: Tab): AppState = copy(nav = nav.selectTab(next))

    /**
     * Routes that address a server keep working across a restart because the id is in the
     * route, but the detail screen also reads [activeServerId]; keep them in step.
     */
    fun focusedOn(serverId: String?): AppState = when (val route = nav.route) {
        is Route.Detail -> copy(activeServerId = route.serverId)
        is Route.Remote -> copy(activeServerId = route.serverId)
        else -> copy(activeServerId = serverId)
    }
}

sealed interface SideEffect {
    data class OpenUrl(val url: String) : SideEffect
    data class Copy(val label: String, val value: String) : SideEffect
    data class Notify(val title: String, val body: String) : SideEffect
    data class ShareFile(val path: String, val mimeType: String) : SideEffect

    /** Android must show the system VPN consent dialog from an Activity. */
    data object RequestVpnConsent : SideEffect

    /** Hide the system bars while the remote desktop is on screen. */
    data class Immersive(val enabled: Boolean) : SideEffect
}
