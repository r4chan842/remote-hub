package app.remotehub.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import app.remotehub.app.AppScreen
import app.remotehub.app.AppState
import app.remotehub.app.CreateForm
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
import app.remotehub.ui.Destination
import app.remotehub.ui.GuideContent
import app.remotehub.ui.ProgressContent
import app.remotehub.ui.RemoteContent
import app.remotehub.ui.RemoteMetrics
import app.remotehub.ui.SessionDetailContent
import app.remotehub.ui.SettingsContent
import app.remotehub.ui.SetupContent
import java.io.File
import kotlinx.coroutines.Dispatchers
import org.jetbrains.skia.EncodedImageFormat

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
    tailnetName = "tailnet-8f21.ts.net",
    nodeName = "remotehub-phone",
    nodeAddress = "100.94.12.7",
    relayLabel = "DERP · fra",
    latencyMillis = 38,
    transportLabel = "userspace",
    lastError = null,
)

private val spec = ServerSpec(4, 16, 14, "Windows Server 2025 Datacenter", "github-runner", "GitHub Actions")

private val readyServer = ServerSession(
    id = "windows-demo-a91f4c",
    name = "windows-desktop",
    state = ServerState.Expiring,
    spec = spec,
    connection = ConnectionInfo("100.94.12.41", 3389, "rhuser", "Xk7mQ2vLp9tR4nB8wZ1cD6yF", "9A:41:C7:2E"),
    steps = emptyList(),
    uptimeLabel = "5h 36m",
    remainingLabel = "00:23:41",
    remainingFraction = 0.11f,
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

private val progressSteps = listOf(
    ProvisionStep("account", StepStatus.Done, "@r4chan842", 1200),
    ProvisionStep("repo", StepStatus.Done, "r4chan842/remote-hub-sessions", 2400),
    ProvisionStep("secrets", StepStatus.Done, "3 secrets · sealed box", 1800),
    ProvisionStep("dispatch", StepStatus.Done, "run #129", 900),
    ProvisionStep("machine", StepStatus.Running, "4. Create the desktop account", null),
    ProvisionStep("network", StepStatus.Pending, null, null),
    ProvisionStep("ready", StepStatus.Pending, null, null),
)

private val emptySetup = AppState(screen = AppScreen.Setup)

private val readySetup = AppState(
    screen = AppScreen.Setup,
    account = account,
    tailnet = onlineTailnet,
)

private val createState = AppState(
    screen = AppScreen.Create,
    account = account,
    tailnet = onlineTailnet,
    form = CreateForm(name = "windows-desktop", durationMinutes = 360),
)

private val progressState = AppState(
    screen = AppScreen.Progress,
    account = account,
    tailnet = onlineTailnet,
    form = CreateForm(name = "windows-desktop", durationMinutes = 360),
    provisioning = true,
    steps = progressSteps,
    progressLog = listOf(
        "repository ready",
        "3 secrets sealed",
        "workflow committed",
        "run #129 dispatched",
        "1. Validate the request",
        "2. Enable Remote Desktop",
    ),
)

private val detailState = AppState(
    screen = AppScreen.Detail,
    account = account,
    tailnet = onlineTailnet,
    servers = listOf(readyServer),
    activeServerId = readyServer.id,
)

private data class Variant(val locale: String, val dark: Boolean, val expanded: Boolean) {
    fun suffix(): String = "$locale-${if (dark) "dark" else "light"}-${if (expanded) "desktop" else "phone"}"
}

private val variants = listOf(
    Variant("fa", true, false),
    Variant("fa", true, true),
    Variant("en", false, false),
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
    expanded: Boolean,
    title: String,
    subtitle: String? = null,
    back: Boolean = false,
    content: @Composable (Modifier) -> Unit,
) {
    Frame(strings, dark) {
        AppShell(
            selected = Destination.Dashboard,
            onSelect = {},
            expanded = expanded,
            title = title,
            subtitle = subtitle,
            navigationIcon = if (back) { { BackIcon(onClick = {}) } } else null,
        ) { padding ->
            content(Modifier.padding(padding))
        }
    }
}

private fun screenContent(name: String, strings: AppStrings, variant: Variant): @Composable () -> Unit = {
    val expanded = variant.expanded
    when (name) {
        "01-setup-empty" -> ShellFrame(strings, variant.dark, expanded, strings.setupTitle, strings.setupSubtitle) { m ->
            SetupContent(emptySetup, {}, {}, {}, {}, {}, {}, {}, {}, {}, m)
        }
        "02-setup-ready" -> ShellFrame(strings, variant.dark, expanded, strings.setupTitle, strings.setupSubtitle) { m ->
            SetupContent(readySetup, {}, {}, {}, {}, {}, {}, {}, {}, {}, m)
        }
        "03-create" -> ShellFrame(strings, variant.dark, expanded, strings.createTitle, strings.createSubtitle, back = true) { m ->
            CreateContent(createState, {}, {}, {}, {}, m)
        }
        "04-progress" -> ShellFrame(strings, variant.dark, expanded, strings.progressTitle, strings.progressSubtitle, back = true) { m ->
            ProgressContent(progressState, {}, {}, {}, m)
        }
        "05-detail" -> ShellFrame(strings, variant.dark, expanded, readyServer.name, back = true) { m ->
            SessionDetailContent(readyServer, false, false, null, {}, { _, _ -> }, {}, {}, {}, {}, {}, m)
        }
        "06-remote" -> Frame(strings, variant.dark) {
            RemoteContent(
                server = readyServer.copy(state = ServerState.Connected),
                metrics = RemoteMetrics(31, 38, 2400, "1600 × 900", true, true),
                onDisconnect = {},
                onKeyboard = {},
                onClipboard = {},
                onCtrlAltDel = {},
                onToggleInputMode = {},
                onFullscreen = {},
                desktopSurface = { Box(Modifier.fillMaxSize().background(Color(0xFF10233F))) },
            )
        }
        "07-settings" -> ShellFrame(strings, variant.dark, expanded, strings.settingsTitle, back = true) { m ->
            SettingsContent(0, variant.locale, true, 15, {}, {}, {}, {}, {}, m)
        }
        "08-guide" -> ShellFrame(strings, variant.dark, expanded, strings.guides, back = true) { m ->
            GuideContent("github", m)
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
    val names = listOf(
        "01-setup-empty",
        "02-setup-ready",
        "03-create",
        "04-progress",
        "05-detail",
        "06-remote",
        "07-settings",
        "08-guide",
    )
    var count = 0
    for (name in names) {
        for (variant in variants) {
            val strings = stringsFor(variant.locale)
            val width = if (variant.expanded) 1920 else 824
            val height = if (variant.expanded) 1320 else 1830
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
    if (count != names.size * variants.size) error("render count mismatch")
}
