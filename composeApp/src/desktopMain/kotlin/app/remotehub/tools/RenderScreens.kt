package app.remotehub.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.remotehub.app.AppState
import app.remotehub.app.CreateForm
import app.remotehub.app.NavState
import app.remotehub.app.ProvisionOperation
import app.remotehub.app.ProvisionPhase
import app.remotehub.app.Route
import app.remotehub.app.Tab
import app.remotehub.design.RemoteHubTheme
import app.remotehub.domain.AuthMethod
import app.remotehub.domain.BackupRecord
import app.remotehub.domain.ConnectionInfo
import app.remotehub.domain.GitHubAccount
import app.remotehub.domain.NetworkState
import app.remotehub.domain.ProvisionStep
import app.remotehub.domain.ServerSession
import app.remotehub.domain.ServerSpec
import app.remotehub.domain.ServerState
import app.remotehub.domain.StepStatus
import app.remotehub.domain.TailnetStatus
import app.remotehub.i18n.AppStrings
import app.remotehub.i18n.LocalStrings
import app.remotehub.i18n.stringsFor
import app.remotehub.ui.AppShell
import app.remotehub.ui.BackIcon
import app.remotehub.ui.CreateContent
import app.remotehub.ui.GuideContent
import app.remotehub.ui.LogsContent
import app.remotehub.ui.NetworkContent
import app.remotehub.ui.ProgressContent
import app.remotehub.ui.RemoteContent
import app.remotehub.ui.RemoteMetrics
import app.remotehub.ui.ServersContent
import app.remotehub.ui.SessionDetailContent
import app.remotehub.ui.SettingsContent
import app.remotehub.ui.SetupContent
import app.remotehub.ui.TailnetDevice
import java.io.File
import kotlinx.coroutines.Dispatchers
import org.jetbrains.skia.EncodedImageFormat

// --------------------------------------------------------------------------- fixtures

private val account = GitHubAccount(
    login = "r4chan842",
    displayName = "Remote Hub",
    method = AuthMethod.PersonalAccessToken,
    grantedScopes = listOf("repo", "workflow"),
    missingScopes = emptyList(),
    tokenExpiresLabel = "",
)

private val onlineTailnet = TailnetStatus(
    state = NetworkState.Online,
    tailnetName = "r4chan842.github",
    nodeName = "remotehub-phone.tail612f7d.ts.net",
    nodeAddress = "100.67.232.80",
    relayLabel = "DERP · fra",
    latencyMillis = 38,
    transportLabel = "userspace",
    lastError = null,
)

private val offlineTailnet = onlineTailnet.copy(
    state = NetworkState.Offline,
    nodeAddress = "",
    tailnetName = "",
    nodeName = "",
    relayLabel = "",
    latencyMillis = 0,
    lastError = "tailnet login: invalid key",
)

private val spec = ServerSpec(4, 16, 14, "Windows Server 2025 Datacenter", "github-runner", "GitHub Actions")

private val readyServer = ServerSession(
    id = "windows-demo-a91f4c",
    name = "windows-desktop",
    state = ServerState.Ready,
    spec = spec,
    connection = ConnectionInfo("100.64.34.100", 3389, "rhuser", "Xk7mQ2vLp9tR4nB8wZ1cD6yF", "9A:41:C7:2E"),
    steps = emptyList(),
    uptimeLabel = "5h 36m",
    remainingLabel = "02:23:41",
    remainingFraction = 0.42f,
    warningThresholdMinutes = 15,
    workflowRunNumber = 128,
    workflowRunUrl = "https://github.com/r4chan842/remote-hub-sessions/actions/runs/128",
    repositoryFullName = "r4chan842/remote-hub-sessions",
    operations = emptyList(),
    backups = listOf(
        BackupRecord("bk-2", "2026-11-27 09:10", "39 MB", true, "7de20b8"),
        BackupRecord("bk-1", "2026-11-26 21:02", "37 MB", true, "c30a915"),
    ),
    lastError = null,
    runId = 128,
    startedAtEpochMs = 1_764_000_000_000,
    durationMinutes = 360,
)

private val stepIds = listOf("account", "repo", "secrets", "dispatch", "machine", "network", "ready")

private fun steps(runningIndex: Int, failedIndex: Int = -1): List<ProvisionStep> =
    stepIds.mapIndexed { index, id ->
        ProvisionStep(
            id = id,
            status = when {
                index == failedIndex -> StepStatus.Failed
                index < runningIndex -> StepStatus.Done
                index == runningIndex -> StepStatus.Running
                else -> StepStatus.Pending
            },
            detail = when {
                index == failedIndex -> "runner exited with code 1"
                id == "account" && index < runningIndex -> "@r4chan842"
                id == "repo" && index < runningIndex -> "r4chan842/remote-hub-sessions"
                id == "dispatch" && index <= runningIndex -> "run #129"
                else -> null
            },
            elapsedMillis = if (index < runningIndex) 1200L * (index + 1) else null,
        )
    }

private val runningOperation = ProvisionOperation(
    phase = ProvisionPhase.RunningWorkflow,
    serverName = "windows-desktop",
    durationMinutes = 360,
    steps = steps(4),
    log = listOf("repository ready", "3 secrets sealed", "run #129 dispatched", "2. Enable Remote Desktop"),
    startedAtEpochMs = 1_764_000_000_000,
    attempt = 1,
)

private val failedOperation = runningOperation.copy(
    phase = ProvisionPhase.Failed,
    steps = steps(4, failedIndex = 4),
    errorMessage = "The runner stopped before Remote Desktop was enabled.",
    errorCode = "workflow",
    finishedAtEpochMs = 1_764_000_600_000,
)

private val completedOperation = runningOperation.copy(
    phase = ProvisionPhase.Completed,
    steps = steps(7),
    serverId = readyServer.id,
    runNumber = 129,
    finishedAtEpochMs = 1_764_000_900_000,
)

private val logSample = listOf(
    app.remotehub.app.LogEntry(
        seq = 3, timeMillis = 1_760_000_003_000, level = app.remotehub.app.LogLevel.Error,
        source = "network", message = "tailnet login: invalid key",
        detail = "tsnet.Up: invalid authkey\nstage=login",
    ),
    app.remotehub.app.LogEntry(2, 1_760_000_002_000, app.remotehub.app.LogLevel.Warn, "health", "no DERP connection"),
    app.remotehub.app.LogEntry(1, 1_760_000_001_000, app.remotehub.app.LogLevel.Info, "app", "RemoteHub started"),
)

private fun baseState(route: Route) = AppState(
    nav = NavState().let { nav ->
        val withTab = nav.selectTab(route.tab)
        if (route.isRoot) withTab else withTab.push(route)
    },
    account = account,
    tailnet = onlineTailnet,
    servers = listOf(readyServer),
    activeServerId = readyServer.id,
)

private val manyServers = listOf(
    readyServer.copy(state = ServerState.Connected),
    readyServer.copy(id = "s2", name = "Windows 11 · build", state = ServerState.Ready),
    readyServer.copy(id = "s3", name = "Windows Server · test", state = ServerState.Stopped),
    readyServer.copy(id = "s4", name = "Windows 11 · expired", state = ServerState.Failed),
    readyServer.copy(id = "s5", name = "qa-runner", state = ServerState.Expiring, remainingFraction = 0.08f, remainingLabel = "00:07:12"),
    readyServer.copy(id = "s6", name = "staging-desktop", state = ServerState.Provisioning),
)

// --------------------------------------------------------------------------- variants

private data class Variant(val locale: String, val dark: Boolean, val expanded: Boolean) {
    fun suffix(): String = "$locale-${if (dark) "dark" else "light"}-${if (expanded) "expanded" else "compact"}"
}

private val variants = listOf(
    Variant("fa", true, false),
    Variant("en", false, false),
    Variant("ar", true, false),
    Variant("en", false, true),
)

@Composable
private fun Frame(strings: AppStrings, dark: Boolean, content: @Composable () -> Unit) {
    RemoteHubTheme(darkTheme = dark) {
        CompositionLocalProvider(
            LocalStrings provides strings,
            LocalLayoutDirection provides if (strings.rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
        ) {
            content()
        }
    }
}

@Composable
private fun ShellFrame(
    strings: AppStrings,
    dark: Boolean,
    title: String,
    tab: Tab,
    back: Boolean = false,
    fab: Boolean = false,
    content: @Composable (Modifier) -> Unit,
) {
    Frame(strings, dark) {
        AppShell(
            selected = tab,
            onSelect = {},
            title = title,
            navigationIcon = if (back) { { BackIcon(onClick = {}) } } else null,
            floatingAction = if (fab) { { app.remotehub.ui.NewServerFab(onClick = {}) } } else null,
        ) { padding ->
            content(Modifier.padding(padding))
        }
    }
}

private val screenNames = listOf(
    "01-setup-empty",
    "02-setup-partial",
    "03-setup-ready",
    "04-create",
    "05-progress-running",
    "06-progress-failed",
    "07-progress-completed",
    "08-detail-ready",
    "09-detail-stopped",
    "10-detail-no-address",
    "11-servers-empty",
    "12-servers-many",
    "13-settings",
    "14-guide",
    "15-logs",
    "16-network-online",
    "17-network-offline",
    "18-remote",
)

private fun screenContent(name: String, strings: AppStrings, variant: Variant): @Composable () -> Unit = {
    val noPadding = PaddingValues(0.dp)
    when (name) {
        "01-setup-empty" -> ShellFrame(strings, variant.dark, strings.setupTitle, Tab.Home) { m ->
            SetupContent(AppState(), {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, m)
        }
        "02-setup-partial" -> ShellFrame(strings, variant.dark, strings.setupTitle, Tab.Home) { m ->
            SetupContent(AppState(account = account), {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, m)
        }
        "03-setup-ready" -> ShellFrame(strings, variant.dark, strings.setupTitle, Tab.Home) { m ->
            SetupContent(baseState(Route.Setup), {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, m)
        }
        "04-create" -> ShellFrame(strings, variant.dark, strings.createTitle, Tab.Home, back = true) { m ->
            CreateContent(
                baseState(Route.Create).copy(form = CreateForm("windows-desktop", 360)),
                {}, {}, {}, {}, m,
            )
        }
        "05-progress-running" -> ShellFrame(strings, variant.dark, strings.progressTitle, Tab.Home, back = true) { m ->
            ProgressContent(baseState(Route.Progress).copy(operation = runningOperation), {}, {}, {}, m)
        }
        "06-progress-failed" -> ShellFrame(strings, variant.dark, strings.progressTitle, Tab.Home, back = true) { m ->
            ProgressContent(baseState(Route.Progress).copy(operation = failedOperation), {}, {}, {}, m)
        }
        "07-progress-completed" -> ShellFrame(strings, variant.dark, strings.progressTitle, Tab.Home, back = true) { m ->
            ProgressContent(baseState(Route.Progress).copy(operation = completedOperation), {}, {}, {}, m)
        }
        "08-detail-ready" -> ShellFrame(strings, variant.dark, readyServer.name, Tab.Servers, back = true) { m ->
            SessionDetailContent(readyServer, false, false, null, {}, { _, _ -> }, {}, {}, {}, {}, {}, m)
        }
        "09-detail-stopped" -> ShellFrame(strings, variant.dark, readyServer.name, Tab.Servers, back = true) { m ->
            SessionDetailContent(readyServer.copy(state = ServerState.Stopped), false, false, null, {}, { _, _ -> }, {}, {}, {}, {}, {}, m)
        }
        "10-detail-no-address" -> ShellFrame(strings, variant.dark, readyServer.name, Tab.Servers, back = true) { m ->
            SessionDetailContent(
                readyServer.copy(state = ServerState.Provisioning, connection = readyServer.connection?.copy(address = "")),
                false, false, null, {}, { _, _ -> }, {}, {}, {}, {}, {}, m,
            )
        }
        "11-servers-empty" -> ShellFrame(strings, variant.dark, strings.serversTitle, Tab.Servers) { m ->
            ServersContent(emptyList(), noPadding, {}, {}, {}, {}, m)
        }
        "12-servers-many" -> ShellFrame(strings, variant.dark, strings.serversTitle, Tab.Servers, fab = true) { m ->
            ServersContent(manyServers, noPadding, {}, {}, {}, {}, m)
        }
        "13-settings" -> ShellFrame(strings, variant.dark, strings.settingsTitle, Tab.Settings) { m ->
            SettingsContent(0, variant.locale, true, 15, {}, {}, {}, {}, {}, {}, m)
        }
        "14-guide" -> ShellFrame(strings, variant.dark, strings.guides, Tab.Settings, back = true) { m ->
            GuideContent("github", m)
        }
        "15-logs" -> ShellFrame(strings, variant.dark, strings.logsTitle, Tab.Settings, back = true) { m ->
            LogsContent(
                entries = logSample,
                filter = "all",
                query = "",
                expandedSeq = logSample.first().seq,
                crashNotice = null,
                environment = "device: design bench · android 16 (API 36)",
                onFilter = {}, onQuery = {}, onToggleDetail = {}, onCopy = {},
                onShare = {}, onClear = {}, onRefresh = {}, onDismissCrash = {},
                modifier = m,
            )
        }
        "16-network-online" -> ShellFrame(strings, variant.dark, strings.networkScreenTitle, Tab.Network) { m ->
            NetworkContent(
                tailnet = onlineTailnet,
                devices = listOf(
                    TailnetDevice("rh-server-a1b2c3", "100.101.212.73", true),
                    TailnetDevice("rh-server-d4e5f6", "100.98.44.12", false),
                ),
                deviceVpn = true,
                vpnBusy = false,
                vpnInterface = "tun0",
                padding = noPadding,
                onToggleVpn = {}, onConnect = {}, onDisconnect = {}, onCopy = { _, _ -> },
                modifier = m,
            )
        }
        "17-network-offline" -> ShellFrame(strings, variant.dark, strings.networkScreenTitle, Tab.Network) { m ->
            NetworkContent(
                tailnet = offlineTailnet,
                devices = emptyList(),
                deviceVpn = false,
                vpnBusy = false,
                vpnInterface = "",
                padding = noPadding,
                onToggleVpn = {}, onConnect = {}, onDisconnect = {}, onCopy = { _, _ -> },
                modifier = m,
            )
        }
        "18-remote" -> Frame(strings, variant.dark) {
            RemoteContent(
                server = readyServer.copy(state = ServerState.Connected),
                metrics = RemoteMetrics(31, 58, 2400, "1600 × 900", true, false),
                onDisconnect = {}, onKeyboard = {}, onClipboard = {}, onRightClick = {},
                onCtrlAltDel = {}, onToggleInputMode = {}, onFullscreen = {},
                hostAspectRatio = 16f / 9f,
                desktopSurface = { Box(Modifier.fillMaxSize().background(Color(0xFF10233F))) },
            )
        }
        else -> error("unknown screen $name")
    }
}

@OptIn(ExperimentalComposeUiApi::class)
private fun render(width: Int, height: Int, density: Float, target: File, content: @Composable () -> Unit) {
    val scene = ImageComposeScene(
        width = width,
        height = height,
        density = Density(density),
        coroutineContext = Dispatchers.Default,
        content = content,
    )
    try {
        var image = scene.render()
        repeat(12) {
            Thread.sleep(90)
            image = scene.render()
        }
        val data = image.encodeToData(EncodedImageFormat.PNG) ?: error("PNG encoding failed for ${target.name}")
        target.parentFile.mkdirs()
        target.writeBytes(data.bytes)
    } finally {
        scene.close()
    }
}

fun main(args: Array<String>) {
    val outDir = File(args.firstOrNull() ?: "build/screens")
    outDir.mkdirs()
    var count = 0
    for (name in screenNames) {
        for (variant in variants) {
            val strings = stringsFor(variant.locale)
            // Compact is a 412x915 dp phone; expanded is a 1280x900 dp tablet window.
            val width = if (variant.expanded) 1920 else 824
            val height = if (variant.expanded) 1350 else 1830
            val density = if (variant.expanded) 1.5f else 2f
            val target = File(outDir, "$name--${variant.suffix()}.png")
            render(width, height, density, target) {
                screenContent(name, strings, variant)()
            }
            count++
            println("rendered ${target.name} ${target.length()} bytes")
        }
    }
    println("total=$count out=${outDir.absolutePath}")
    if (count != screenNames.size * variants.size) error("render count mismatch")
}
