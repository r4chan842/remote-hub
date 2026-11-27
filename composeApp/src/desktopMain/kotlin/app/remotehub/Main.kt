package app.remotehub

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import app.remotehub.app.AppScreen
import app.remotehub.app.AppState
import app.remotehub.app.CreateForm
import app.remotehub.design.RemoteHubTheme
import app.remotehub.domain.AuthMethod
import app.remotehub.domain.ConnectionInfo
import app.remotehub.domain.GitHubAccount
import app.remotehub.domain.NetworkState
import app.remotehub.domain.ProvisionStep
import app.remotehub.domain.ServerSession
import app.remotehub.domain.ServerSpec
import app.remotehub.domain.ServerState
import app.remotehub.domain.StepStatus
import app.remotehub.domain.TailnetStatus
import app.remotehub.i18n.LocalStrings
import app.remotehub.i18n.availableLocales
import app.remotehub.i18n.stringsFor
import app.remotehub.ui.AppShell
import app.remotehub.ui.BackIcon
import app.remotehub.ui.CreateContent
import app.remotehub.ui.Destination
import app.remotehub.ui.GuideContent
import app.remotehub.ui.LogsContent
import app.remotehub.ui.NetworkContent
import app.remotehub.ui.ProgressContent
import app.remotehub.ui.RemoteContent
import app.remotehub.ui.RemoteMetrics
import app.remotehub.ui.SessionDetailContent
import app.remotehub.ui.SettingsContent
import app.remotehub.ui.ServersContent
import app.remotehub.ui.SetupContent
import app.remotehub.ui.TailnetDevice

/**
 * Desktop is a design bench, not a shipping target: it shows the real Compose screens
 * so the layout can be reviewed in every locale and theme. Provisioning, the private
 * network and the remote desktop only run in the Android app.
 */
@Composable
fun RemoteHubApp() {
    var screen by remember { mutableStateOf(AppScreen.Setup) }
    var themeMode by remember { mutableStateOf(2) }
    var localeTag by remember { mutableStateOf("fa") }
    var connected by remember { mutableStateOf(false) }
    var form by remember { mutableStateOf(CreateForm(name = "windows-desktop", durationMinutes = 360)) }

    val strings = stringsFor(localeTag)
    val account = GitHubAccount("preview-user", "preview-user", AuthMethod.PersonalAccessToken, listOf("repo", "workflow"), emptyList(), "")
    val tailnet = TailnetStatus(
        state = if (connected) NetworkState.Online else NetworkState.Offline,
        tailnetName = "tailnet-preview",
        nodeName = "remotehub-phone",
        nodeAddress = if (connected) "100.94.12.7" else "",
        relayLabel = "DERP · fra",
        latencyMillis = 38,
        transportLabel = "userspace",
        lastError = null,
    )
    val server = ServerSession(
        id = "windows-preview",
        name = form.name.ifBlank { "windows-desktop" },
        state = ServerState.Ready,
        spec = ServerSpec(4, 16, 14, "Windows Server 2025 Datacenter", "github-runner", "GitHub Actions"),
        connection = ConnectionInfo("100.94.12.41", 3389, "rhuser", "preview-password", ""),
        steps = emptyList(),
        uptimeLabel = "1h 04m",
        remainingLabel = "04:56:00",
        remainingFraction = 0.82f,
        warningThresholdMinutes = 15,
        workflowRunNumber = 1,
        workflowRunUrl = "https://github.com",
        repositoryFullName = "preview-user/remote-hub-sessions",
        operations = emptyList(),
        backups = emptyList(),
        lastError = null,
        runId = 1,
        durationMinutes = form.durationMinutes,
    )
    val state = AppState(
        screen = screen,
        account = if (connected) account else null,
        tailnet = tailnet,
        form = form,
        provisioning = screen == AppScreen.Progress,
        steps = listOf(
            ProvisionStep("account", StepStatus.Done, "@preview-user", 1200),
            ProvisionStep("repo", StepStatus.Done, "preview-user/remote-hub-sessions", 2400),
            ProvisionStep("secrets", StepStatus.Done, "3 secrets", 1800),
            ProvisionStep("dispatch", StepStatus.Done, "run #1", 900),
            ProvisionStep("machine", StepStatus.Running, "2. Enable Remote Desktop", null),
            ProvisionStep("network", StepStatus.Pending, null, null),
            ProvisionStep("ready", StepStatus.Pending, null, null),
        ),
        servers = listOf(server),
        activeServerId = server.id,
        themeMode = themeMode,
        localeTag = localeTag,
    )

    RemoteHubTheme(darkTheme = themeMode == 2) {
        CompositionLocalProvider(
            LocalStrings provides strings,
            LocalLayoutDirection provides if (strings.rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
        ) {
            Row(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
                Column(
                    Modifier.width(220.dp).fillMaxHeight().background(MaterialTheme.colorScheme.surfaceContainerLow)
                        .verticalScroll(rememberScrollState()).padding(12.dp),
                ) {
                    Text(strings.appName, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    Text("design bench", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    AppScreen.entries.forEach { entry ->
                        TextButton(onClick = { screen = entry }, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                entry.name,
                                style = MaterialTheme.typography.labelLarge,
                                color = if (entry == screen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    availableLocales.forEach { (tag, name) ->
                        TextButton(onClick = { localeTag = tag }, modifier = Modifier.fillMaxWidth()) {
                            Text(name, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    TextButton(onClick = { themeMode = if (themeMode == 2) 1 else 2 }, modifier = Modifier.fillMaxWidth()) {
                        Text(strings.theme, style = MaterialTheme.typography.labelMedium)
                    }
                    TextButton(onClick = { connected = !connected }, modifier = Modifier.fillMaxWidth()) {
                        Text(strings.connected, style = MaterialTheme.typography.labelMedium)
                    }
                }

                Box(Modifier.weight(1f).fillMaxHeight()) {
                    if (screen == AppScreen.Remote) {
                        RemoteContent(
                            server = server.copy(state = ServerState.Connected),
                            metrics = RemoteMetrics(30, 38, 2400, "1600 × 900", true, true),
                            onDisconnect = { screen = AppScreen.Detail },
                            onKeyboard = {},
                            onClipboard = {},
                            onRightClick = {},
                            onCtrlAltDel = {},
                            onToggleInputMode = {},
                            onFullscreen = { screen = AppScreen.Detail },
                            desktopSurface = { Box(Modifier.fillMaxSize().background(Color(0xFF10233F))) },
                        )
                    } else {
                        AppShell(
                            selected = Destination.Dashboard,
                            onSelect = {},
                            expanded = false,
                            title = when (screen) {
                                AppScreen.Setup -> strings.setupTitle
                                AppScreen.Create -> strings.createTitle
                                AppScreen.Progress -> strings.progressTitle
                                AppScreen.Detail -> server.name
                                AppScreen.Settings -> strings.settingsTitle
                                AppScreen.Guide -> strings.guides
                                AppScreen.Remote -> strings.remoteDesktop
                                AppScreen.Logs -> strings.logsTitle
                                AppScreen.Servers -> strings.serversTitle
                                AppScreen.Network -> strings.networkScreenTitle
                            },
                            navigationIcon = if (screen == AppScreen.Setup) null else { { BackIcon(onClick = { screen = AppScreen.Setup }) } },
                        ) { padding ->
                            val m = Modifier.padding(padding)
                            when (screen) {
                                AppScreen.Setup -> SetupContent(
                                    state, {}, {}, {}, {}, {},
                                    { screen = AppScreen.Create },
                                    { screen = AppScreen.Detail },
                                    { connected = false }, { connected = false }, { screen = AppScreen.Logs }, m,
                                )
                                AppScreen.Create -> CreateContent(
                                    state,
                                    { form = form.copy(name = it) },
                                    { form = form.copy(durationMinutes = it) },
                                    { screen = AppScreen.Progress },
                                    { screen = AppScreen.Setup },
                                    m,
                                )
                                AppScreen.Progress -> ProgressContent(state, {}, { screen = AppScreen.Setup }, { screen = AppScreen.Detail }, m)
                                AppScreen.Detail -> SessionDetailContent(
                                    server, false, false, null, {}, { _, _ -> },
                                    { screen = AppScreen.Remote }, {}, {}, {}, {}, m,
                                )
                                AppScreen.Settings -> SettingsContent(
                                    themeMode, localeTag, true, 15,
                                    { themeMode = it }, { localeTag = it }, {}, {},
                                    { screen = AppScreen.Guide }, { screen = AppScreen.Logs }, m,
                                )
                                AppScreen.Guide -> GuideContent("github", m)
                                AppScreen.Logs -> LogsContent(
                                    entries = sampleLogs,
                                    filter = "all",
                                    query = "",
                                    expandedSeq = null,
                                    crashNotice = null,
                                    environment = "device: design bench\nandroid: 16 · API 36",
                                    onFilter = {}, onQuery = {}, onToggleDetail = {}, onCopy = {},
                                    onShare = {}, onClear = {}, onRefresh = {}, onDismissCrash = {},
                                    modifier = m,
                                )
                                AppScreen.Remote -> Unit
                                AppScreen.Servers -> ServersContent(
                                    servers = listOf(server),
                                    padding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                                    onOpen = { screen = AppScreen.Detail },
                                    onConnect = { screen = AppScreen.Remote },
                                    onRestart = { screen = AppScreen.Progress },
                                    onCreate = { screen = AppScreen.Create },
                                    modifier = m,
                                )
                                AppScreen.Network -> NetworkContent(
                                    tailnet = state.tailnet,
                                    devices = listOf(
                                        TailnetDevice("rh-server-a1b2c3", "100.101.212.73", true),
                                        TailnetDevice("rh-server-d4e5f6", "100.98.44.12", false),
                                    ),
                                    deviceVpn = true,
                                    vpnBusy = false,
                                    vpnInterface = "tun0",
                                    padding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                                    onToggleVpn = {}, onConnect = {}, onDisconnect = {}, onCopy = { _, _ -> },
                                    modifier = m,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private val sampleLogs = listOf(
    app.remotehub.app.LogEntry(3, 1_760_000_003_000, app.remotehub.app.LogLevel.Error, "network", "tailnet login: invalid key", "tsnet.Up: invalid authkey"),
    app.remotehub.app.LogEntry(2, 1_760_000_002_000, app.remotehub.app.LogLevel.Info, "node", "stage=login"),
    app.remotehub.app.LogEntry(1, 1_760_000_001_000, app.remotehub.app.LogLevel.Info, "app", "RemoteHub started"),
)

fun main() {
    application {
        Window(
            onCloseRequest = ::exitApplication,
            state = rememberWindowState(width = 1280.dp, height = 880.dp),
            title = "RemoteHub · design bench",
        ) {
            RemoteHubApp()
        }
    }
}
