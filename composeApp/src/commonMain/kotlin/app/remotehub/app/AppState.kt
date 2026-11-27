package app.remotehub.app

import app.remotehub.domain.GitHubAccount
import app.remotehub.domain.NetworkState
import app.remotehub.domain.ProvisionStep
import app.remotehub.domain.ServerSession
import app.remotehub.domain.TailnetStatus
import app.remotehub.server.SessionWorkflow

/** The linear path a new user walks: checklist, create, progress, detail, remote. */
enum class AppScreen { Setup, Create, Progress, Detail, Remote, Settings, Guide }

/** The two things that must be real before a server can be created. */
enum class SetupItem { GitHub, Network }

data class CreateForm(
    val name: String = "",
    val durationMinutes: Int = SessionWorkflow.DEFAULT_MINUTES,
)

data class AppState(
    val screen: AppScreen = AppScreen.Setup,
    val guideId: String? = null,

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

    // create + progress
    val form: CreateForm = CreateForm(),
    val provisioning: Boolean = false,
    val steps: List<ProvisionStep> = emptyList(),
    val progressLog: List<String> = emptyList(),
    val activeServerId: String? = null,

    // detail + remote
    val servers: List<ServerSession> = emptyList(),
    val passwordVisible: Boolean = false,
    val trackpadMode: Boolean = false,

    // settings
    val themeMode: Int = 0,
    val localeTag: String = "fa",
    val expiryAlertEnabled: Boolean = true,
    val warningMinutes: Int = 15,
    val publicRepository: Boolean = true,
    val repositoryName: String = "remote-hub-sessions",

    // transient
    val busy: Boolean = false,
    val error: String? = null,
    val toast: String? = null,
    val highlightItem: SetupItem? = null,
    val appVersion: String = "1.1.0",
) {
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
}

sealed interface SideEffect {
    data class OpenUrl(val url: String) : SideEffect
    data class Copy(val label: String, val value: String) : SideEffect
    data class Notify(val title: String, val body: String) : SideEffect
    data class ShareFile(val path: String, val mimeType: String) : SideEffect
}
