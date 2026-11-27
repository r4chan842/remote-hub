package app.remotehub.tools

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.remotehub.design.RemoteHubTheme
import app.remotehub.domain.AuthMethod
import app.remotehub.domain.BackupRecord
import app.remotehub.domain.ConnectionInfo
import app.remotehub.domain.DeviceFlowPhase
import app.remotehub.domain.DeviceFlowState
import app.remotehub.domain.GitHubAccount
import app.remotehub.domain.NetworkState
import app.remotehub.domain.OperationKind
import app.remotehub.domain.OperationRecord
import app.remotehub.domain.OperationResult
import app.remotehub.domain.ProvisionStep
import app.remotehub.domain.QuotaSnapshot
import app.remotehub.domain.ServerSession
import app.remotehub.domain.ServerSpec
import app.remotehub.domain.ServerState
import app.remotehub.domain.StepStatus
import app.remotehub.domain.TailnetStatus
import app.remotehub.i18n.AppStrings
import app.remotehub.i18n.LocalStrings
import app.remotehub.i18n.stringsFor
import app.remotehub.ui.AppShell
import app.remotehub.ui.CreateForm
import app.remotehub.ui.CreateServerContent
import app.remotehub.ui.DashboardContent
import app.remotehub.ui.Destination
import app.remotehub.ui.GitHubAuthContent
import app.remotehub.ui.NetworkContent
import app.remotehub.ui.NewServerFab
import app.remotehub.ui.RemoteContent
import app.remotehub.ui.RemoteMetrics
import app.remotehub.ui.ServerDetailContent
import app.remotehub.ui.SettingsContent
import java.io.File
import kotlinx.coroutines.Dispatchers
import org.jetbrains.skia.EncodedImageFormat

private val account = GitHubAccount(
    login = "r4chan842",
    displayName = "Remote Hub Operator",
    method = AuthMethod.DeviceFlow,
    grantedScopes = listOf("repo", "workflow", "read:user"),
    missingScopes = emptyList(),
    tokenExpiresLabel = "0.1.0",
)

private val tailnet = TailnetStatus(
    state = NetworkState.Online,
    tailnetName = "tailnet-8f21.ts.net",
    nodeName = "remotehub-client",
    nodeAddress = "100.94.12.7",
    relayLabel = "DERP · fra",
    latencyMillis = 38,
    transportLabel = "Direct · WireGuard",
    lastError = null,
)

private val quota = QuotaSnapshot(412, 2000, 1, 20, "6h")

private val spec = ServerSpec(4, 16, 128, "Windows Server 2025", "eu-central", "Hetzner Cloud")

private val connection = ConnectionInfo(
    address = "100.94.12.41",
    port = 3389,
    username = "rhoperator",
    password = "Xk7#mQ2vLp9!",
    certificateFingerprint = "9A:41:C7:2E:88:5B:D0:13",
)

private val readyServer = ServerSession(
    id = "srv-01",
    name = "build-agent-01",
    state = ServerState.Expiring,
    spec = spec,
    connection = connection,
    steps = emptyList(),
    uptimeLabel = "4h 52m",
    remainingLabel = "00:23:41",
    remainingFraction = 0.11f,
    warningThresholdMinutes = 15,
    workflowRunNumber = 128,
    workflowRunUrl = "https://github.com/r4chan842/remote-hub/actions/runs/128",
    repositoryFullName = "r4chan842/remote-hub",
    operations = listOf(
        OperationRecord("op-5", OperationKind.Backup, OperationResult.Success, "12:41", "backup-2026-11-27.enc · 42 MB"),
        OperationRecord("op-4", OperationKind.Connect, OperationResult.Success, "08:03", "FreeRDP · TLS · NLA"),
        OperationRecord("op-3", OperationKind.RerunWorkflow, OperationResult.Success, "07:58", "run #128"),
        OperationRecord("op-2", OperationKind.Provision, OperationResult.Success, "07:49", "8 steps · 6m 12s"),
        OperationRecord("op-1", OperationKind.Rebuild, OperationResult.Cancelled, "07:31", "cancelled by user"),
    ),
    backups = listOf(
        BackupRecord("bk-3", "2026-11-27 12:41", "42 MB", true, "a91f4c2"),
        BackupRecord("bk-2", "2026-11-27 09:10", "39 MB", true, "7de20b8"),
        BackupRecord("bk-1", "2026-11-26 21:02", "37 MB", true, "c30a915"),
    ),
    lastError = null,
)

private val provisioningSteps = listOf(
    ProvisionStep("account", StepStatus.Done, "r4chan842 · repo, workflow", 1200),
    ProvisionStep("repo", StepStatus.Done, "r4chan842/remote-hub", 2400),
    ProvisionStep("secrets", StepStatus.Done, "3 secrets · sealed box", 1800),
    ProvisionStep("dispatch", StepStatus.Done, "run #129", 900),
    ProvisionStep("runner", StepStatus.Running, "job: provision · in_progress", null),
    ProvisionStep("join", StepStatus.Pending, null, null),
    ProvisionStep("handshake", StepStatus.Pending, null, null),
    ProvisionStep("ready", StepStatus.Pending, null, null),
)

private val provisioningServer = ServerSession(
    id = "srv-02",
    name = "test-desktop-02",
    state = ServerState.Provisioning,
    spec = spec,
    connection = null,
    steps = provisioningSteps,
    uptimeLabel = "—",
    remainingLabel = "—",
    remainingFraction = 0f,
    warningThresholdMinutes = 15,
    workflowRunNumber = 129,
    workflowRunUrl = "https://github.com/r4chan842/remote-hub/actions/runs/129",
    repositoryFullName = "r4chan842/remote-hub",
    operations = emptyList(),
    backups = emptyList(),
    lastError = null,
)

private val stoppedServer = ServerSession(
    id = "srv-03",
    name = "scratch-03",
    state = ServerState.Stopped,
    spec = spec,
    connection = null,
    steps = emptyList(),
    uptimeLabel = "—",
    remainingLabel = "—",
    remainingFraction = 0f,
    warningThresholdMinutes = 30,
    workflowRunNumber = 117,
    workflowRunUrl = "https://github.com/r4chan842/remote-hub/actions/runs/117",
    repositoryFullName = "r4chan842/remote-hub",
    operations = emptyList(),
    backups = emptyList(),
    lastError = null,
)

private val deviceFlow = DeviceFlowState(
    phase = DeviceFlowPhase.WaitingForApproval,
    userCode = "7B4K-92QX",
    verificationUrl = "https://github.com/login/device",
    secondsRemaining = 742,
    pollIntervalSeconds = 5,
)

private val form = CreateForm(
    name = "build-agent-04",
    provider = "Hetzner Cloud",
    region = "eu-central · Falkenstein",
    osImage = "Windows Server 2025 Datacenter",
    machineSize = "4 vCPU · 16 GB · 128 GB",
    warningMinutes = 15,
    autoBackup = true,
    encryptedBackup = true,
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
    destination: Destination,
    title: String,
    fab: Boolean = false,
    content: @Composable (Modifier) -> Unit,
) {
    Frame(strings, dark) {
        AppShell(
            selected = destination,
            onSelect = {},
            expanded = expanded,
            title = title,
            floatingAction = { if (fab) NewServerFab(onClick = {}, extended = expanded) },
        ) { padding ->
            content(Modifier.padding(padding))
        }
    }
}

private fun screenContent(name: String, strings: AppStrings, variant: Variant): @Composable () -> Unit = {
    val expanded = variant.expanded
    when (name) {
        "01-dashboard" -> ShellFrame(strings, variant.dark, expanded, Destination.Dashboard, strings.navDashboard, fab = true) { m ->
            DashboardContent(
                account = account,
                tailnet = tailnet,
                providerReady = true,
                servers = listOf(readyServer, provisioningServer, stoppedServer),
                quota = quota,
                expanded = expanded,
                onOpenSetup = {},
                onOpenServer = {},
                onConnect = {},
                onCreate = {},
                modifier = m,
            )
        }
        "02-dashboard-empty" -> ShellFrame(strings, variant.dark, expanded, Destination.Dashboard, strings.navDashboard, fab = true) { m ->
            DashboardContent(
                account = null,
                tailnet = tailnet.copy(state = NetworkState.Offline),
                providerReady = false,
                servers = emptyList(),
                quota = QuotaSnapshot(0, 2000, 0, 20, "6h"),
                expanded = expanded,
                onOpenSetup = {},
                onOpenServer = {},
                onConnect = {},
                onCreate = {},
                modifier = m,
            )
        }
        "03-github-device" -> ShellFrame(strings, variant.dark, expanded, Destination.Servers, strings.githubTitle) { m ->
            GitHubAuthContent(
                account = null,
                method = AuthMethod.DeviceFlow,
                deviceFlow = deviceFlow,
                patInput = "",
                requiredScopes = listOf("repo", "workflow", "read:user"),
                onMethodChange = {},
                onPatChange = {},
                onCopyCode = {},
                onOpenGitHub = {},
                onValidatePat = {},
                onDisconnect = {},
                modifier = m,
            )
        }
        "04-github-pat" -> ShellFrame(strings, variant.dark, expanded, Destination.Servers, strings.githubTitle) { m ->
            GitHubAuthContent(
                account = account,
                method = AuthMethod.PersonalAccessToken,
                deviceFlow = deviceFlow,
                patInput = "github_pat_11ABCDEFG0placeholder",
                requiredScopes = listOf("repo", "workflow", "read:user"),
                onMethodChange = {},
                onPatChange = {},
                onCopyCode = {},
                onOpenGitHub = {},
                onValidatePat = {},
                onDisconnect = {},
                modifier = m,
            )
        }
        "05-network" -> ShellFrame(strings, variant.dark, expanded, Destination.Network, strings.networkTitle) { m ->
            NetworkContent(
                status = tailnet,
                authKeyInput = "tskey-auth-k8f2placeholder",
                onAuthKeyChange = {},
                onConnect = {},
                onDisconnect = {},
                onOfficialSignIn = {},
                modifier = m,
            )
        }
        "06-create-target" -> ShellFrame(strings, variant.dark, expanded, Destination.Servers, strings.createTitle) { m ->
            CreateServerContent(
                form = form,
                wizardStep = 0,
                provisioning = false,
                steps = emptyList(),
                repositoryFullName = "r4chan842/remote-hub",
                onNameChange = {},
                onWarningChange = {},
                onAutoBackupChange = {},
                onBack = {},
                onNext = {},
                onCreate = {},
                onCancel = {},
                modifier = m,
            )
        }
        "07-create-config" -> ShellFrame(strings, variant.dark, expanded, Destination.Servers, strings.createTitle) { m ->
            CreateServerContent(
                form = form,
                wizardStep = 1,
                provisioning = false,
                steps = emptyList(),
                repositoryFullName = "r4chan842/remote-hub",
                onNameChange = {},
                onWarningChange = {},
                onAutoBackupChange = {},
                onBack = {},
                onNext = {},
                onCreate = {},
                onCancel = {},
                modifier = m,
            )
        }
        "08-create-progress" -> ShellFrame(strings, variant.dark, expanded, Destination.Servers, strings.createTitle) { m ->
            CreateServerContent(
                form = form,
                wizardStep = 2,
                provisioning = true,
                steps = provisioningSteps,
                repositoryFullName = "r4chan842/remote-hub",
                onNameChange = {},
                onWarningChange = {},
                onAutoBackupChange = {},
                onBack = {},
                onNext = {},
                onCreate = {},
                onCancel = {},
                modifier = m,
            )
        }
        "09-server-detail" -> ShellFrame(strings, variant.dark, expanded, Destination.Servers, readyServer.name) { m ->
            ServerDetailContent(
                server = readyServer,
                passwordVisible = false,
                expanded = expanded,
                onTogglePassword = {},
                onConnect = {},
                onStop = {},
                onRestart = {},
                onRebuild = {},
                onRerun = {},
                onCreateBackup = {},
                onRestoreBackup = {},
                onOpenRun = {},
                modifier = m,
            )
        }
        "10-remote-desktop" -> Frame(strings, variant.dark) {
            RemoteContent(
                server = readyServer,
                metrics = RemoteMetrics(38, 60, 4200, if (expanded) "1920 × 1080" else "1280 × 720", trackpadMode = expanded, connecting = false),
                onDisconnect = {},
                onKeyboard = {},
                onClipboard = {},
                onCtrlAltDel = {},
                onToggleInputMode = {},
                onFullscreen = {},
            )
        }
        else -> ShellFrame(strings, variant.dark, expanded, Destination.Settings, strings.settingsTitle) { m ->
            SettingsContent(
                themeMode = if (variant.dark) 2 else 1,
                localeTag = strings.tag,
                expiryAlertEnabled = true,
                alertThresholdMinutes = 15,
                biometricEnabled = true,
                storageEncrypted = true,
                appVersion = "0.1.0",
                onThemeChange = {},
                onLocaleChange = {},
                onExpiryAlertChange = {},
                onBiometricChange = {},
                onClearCredentials = {},
                onOpenGuide = {},
                modifier = m,
            )
        }
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
        "01-dashboard",
        "02-dashboard-empty",
        "03-github-device",
        "04-github-pat",
        "05-network",
        "06-create-target",
        "07-create-config",
        "08-create-progress",
        "09-server-detail",
        "10-remote-desktop",
        "11-settings",
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
