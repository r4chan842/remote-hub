package app.remotehub.app

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.produceState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import app.remotehub.MainActivity
import app.remotehub.data.SecureStore
import app.remotehub.diag.Diagnostics
import app.remotehub.data.createSecretBox
import app.remotehub.data.createSecureStore
import app.remotehub.design.RemoteHubTheme
import app.remotehub.design.WarningBanner
import app.remotehub.design.Symbol
import app.remotehub.design.Symbols
import app.remotehub.i18n.LocalStrings
import app.remotehub.i18n.stringsFor
import app.remotehub.rdp.RdpKeyboardTarget
import app.remotehub.rdp.RdpSurface
import app.remotehub.ui.AppShell
import app.remotehub.ui.BackIcon
import app.remotehub.ui.CreateContent
import app.remotehub.ui.FindingAddressContent
import app.remotehub.ui.Destination
import app.remotehub.ui.GuideContent
import app.remotehub.ui.LogsContent
import app.remotehub.ui.NetworkContent
import app.remotehub.ui.ServersContent
import app.remotehub.ui.ProgressContent
import app.remotehub.ui.RemoteContent
import app.remotehub.ui.SessionDetailContent
import app.remotehub.ui.SettingsContent
import app.remotehub.ui.SetupContent
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * The single composition root. One screen at a time, in the order a new user needs:
 * checklist, create, progress, details, remote desktop. Settings and the guides sit
 * beside that path and never interrupt it.
 */
class AndroidAppHost(private val activity: ComponentActivity) {
    /** Only an Activity can show the system VPN dialog, so the Activity owns this. */
    var requestVpnConsent: (Intent) -> Unit = {}

    /** Hides the system bars while the remote desktop is fullscreen. */
    var setImmersive: (Boolean) -> Unit = {}

    fun onVpnConsent(granted: Boolean) = controller.onVpnConsent(granted)

    // An unhandled failure in a background job is recorded and shown, never fatal.
    private val failures = CoroutineExceptionHandler { _, error ->
        Diagnostics.error("app", "background task failed: ${error.message ?: error.javaClass.simpleName}", error)
        state = state.copy(busy = false, error = error.message ?: "background task failed")
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate + failures)
    private val store: SecureStore = createSecureStore()
    private val secretBox = createSecretBox()
    private val controller = SessionController(activity, store, secretBox, scope)

    var state by mutableStateOf(AppState())
        private set

    private var ticker: Job? = null

    init {
        controller.onState = { next -> state = next }
        controller.onEffect = { effect -> handleEffect(effect) }
        controller.restore()
    }

    private fun handleEffect(effect: SideEffect) {
        when (effect) {
            is SideEffect.OpenUrl -> (activity as? MainActivity)?.openExternal(effect.url)
            is SideEffect.Copy -> {
                val manager = activity.getSystemService(ClipboardManager::class.java)
                manager?.setPrimaryClip(ClipData.newPlainText(effect.label, effect.value))
                state = state.copy(toast = effect.label)
            }
            is SideEffect.Notify -> controller.postAlert(effect.title, effect.body)
            is SideEffect.ShareFile -> {
                runCatching {
                    val file = java.io.File(effect.path)
                    val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.logs", file)
                    val send = Intent(Intent.ACTION_SEND)
                        .setType(effect.mimeType)
                        .putExtra(Intent.EXTRA_STREAM, uri)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    activity.startActivity(Intent.createChooser(send, effect.path.substringAfterLast('/')))
                }.onFailure { Diagnostics.error("app", "sharing the log failed", it) }
            }
            is SideEffect.RequestVpnConsent -> {
                val consent = app.remotehub.net.TailscaleVpnService.consentIntent(activity)
                if (consent == null) controller.onVpnConsent(true) else requestVpnConsent(consent)
            }
            is SideEffect.Immersive -> setImmersive(effect.enabled)
        }
    }

    fun resume() {
        ticker?.cancel()
        ticker = scope.launch {
            while (isActive) {
                controller.tick()
                delay(1000)
            }
        }
        controller.refreshNetworkStatus()
    }

    fun pause() {
        ticker?.cancel()
        ticker = null
        controller.stopPolling()
    }

    fun release() {
        ticker?.cancel()
        controller.release()
        scope.cancel()
    }

    @Composable
    fun Content() {
        val strings = stringsFor(state.localeTag)
        val systemDark = isSystemInDarkTheme()
        val dark = when (state.themeMode) {
            1 -> false
            2 -> true
            else -> systemDark
        }

        val permissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { granted -> controller.setNotificationPermission(granted) }

        LaunchedEffect(Unit) {
            if (Build.VERSION.SDK_INT >= 33) {
                val granted = ContextCompat.checkSelfPermission(
                    activity,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) == PackageManager.PERMISSION_GRANTED
                if (granted) {
                    controller.setNotificationPermission(true)
                } else {
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            } else {
                controller.setNotificationPermission(true)
            }
        }

        LaunchedEffect(state.toast) {
            val message = state.toast ?: return@LaunchedEffect
            Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
            controller.clearToast()
        }

        RemoteHubTheme(darkTheme = dark) {
            CompositionLocalProvider(
                LocalStrings provides strings,
                LocalLayoutDirection provides if (strings.rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
            ) {
                val active = state.activeServer
                val devices by produceState(
                    initialValue = emptyList<app.remotehub.ui.TailnetDevice>(),
                    state.screen,
                    state.tailnet.state,
                ) {
                    value = kotlinx.coroutines.withContext(Dispatchers.IO) {
                        runCatching { controller.tailnetDevices() }.getOrDefault(emptyList())
                    }
                }

                // Step 5 is deliberately chrome-free: the desktop owns the whole screen.
                if (state.screen == AppScreen.Remote && active != null) {
                    BackHandler {
                        if (state.remoteFullscreen) {
                            controller.toggleRemoteFullscreen()
                        } else {
                            controller.disconnectRemote(active.id)
                        }
                    }
                    Box(Modifier.fillMaxSize()) {
                        RemoteContent(
                            server = active,
                            metrics = controller.remoteMetrics(active.id),
                            onDisconnect = { controller.disconnectRemote(active.id) },
                            onKeyboard = { controller.showKeyboard() },
                            onClipboard = { controller.pushClipboard(active.id) },
                            onCtrlAltDel = { controller.sendCtrlAltDel(active.id) },
                            onRightClick = { controller.rightClickRemote(active.id) },
                            onToggleInputMode = { controller.toggleInputMode() },
                            onFullscreen = { controller.toggleRemoteFullscreen() },
                            fullscreen = state.remoteFullscreen,
                            connecting = state.remoteConnecting,
                            rightClickArmed = state.rightClickArmed,
                            hostAspectRatio = controller.remoteAspectRatio(active.id),
                            desktopSurface = {
                                if (active.connection?.address.isNullOrBlank()) {
                                    FindingAddressContent()
                                } else {
                                    RdpSurface(
                                        session = controller.rdpSession(active.id),
                                        trackpadMode = state.trackpadMode,
                                        rightClickArmed = state.rightClickArmed,
                                        onRightClickConsumed = { controller.consumeRightClick() },
                                    )
                                }
                            },
                        )
                        // The IME needs a focused editor to deliver keystrokes into.
                        // It stays composed for the whole session so focus survives
                        // toggling, and it is 1dp so it never covers the desktop.
                        RdpKeyboardTarget(
                            visible = state.keyboardVisible,
                            onText = { controller.typeRemote(active.id, it) },
                            onScancode = { code, down -> controller.remoteKey(active.id, code, down) },
                            onPaste = { controller.pushClipboard(active.id) },
                            onImeVisibility = { controller.onImeVisibility(it) },
                            modifier = Modifier.align(Alignment.BottomStart).size(1.dp),
                        )
                        if (state.error != null) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                                WarningBanner(
                                    state.error!!,
                                    Modifier.padding(16.dp).clickable(onClick = { controller.clearError() }),
                                )
                            }
                        }
                    }
                    return@CompositionLocalProvider
                }

                val title = when (state.screen) {
                    AppScreen.Setup -> strings.setupTitle
                    AppScreen.Create -> strings.createTitle
                    AppScreen.Progress -> strings.progressTitle
                    AppScreen.Detail -> active?.name ?: strings.sessionDetail
                    AppScreen.Settings -> strings.settingsTitle
                    AppScreen.Guide -> strings.guides
                    AppScreen.Logs -> strings.logsTitle
                    AppScreen.Remote -> strings.remoteDesktop
                    AppScreen.Servers -> strings.serversTitle
                    AppScreen.Network -> strings.networkScreenTitle
                }
                val subtitle = when (state.screen) {
                    AppScreen.Setup -> strings.setupSubtitle
                    AppScreen.Create -> strings.createSubtitle
                    AppScreen.Progress -> strings.progressSubtitle
                    AppScreen.Servers -> strings.serversSubtitle
                    AppScreen.Network -> strings.networkScreenSubtitle
                    else -> null
                }
                val back: (@Composable () -> Unit)? = when (state.screen) {
                    AppScreen.Create -> { { BackIcon(onClick = { controller.goSetup() }) } }
                    AppScreen.Progress -> { { BackIcon(onClick = { controller.cancelProvision() }) } }
                    AppScreen.Detail -> { { BackIcon(onClick = { controller.goServers() }) } }
                    AppScreen.Settings -> { { BackIcon(onClick = { controller.leaveSettings() }) } }
                    AppScreen.Guide -> { { BackIcon(onClick = { controller.closeGuide() }) } }
                    AppScreen.Logs -> { { BackIcon(onClick = { controller.closeLogs() }) } }
                    else -> null
                }

                when (state.screen) {
                    AppScreen.Create -> BackHandler { controller.goSetup() }
                    AppScreen.Detail -> BackHandler { controller.goServers() }
                    AppScreen.Settings -> BackHandler { controller.leaveSettings() }
                    AppScreen.Servers, AppScreen.Network -> BackHandler { controller.goSetup() }
                    AppScreen.Guide -> BackHandler { controller.closeGuide() }
                    AppScreen.Logs -> BackHandler { controller.closeLogs() }
                    else -> Unit
                }

                AppShell(
                    selected = when (state.screen) {
                        AppScreen.Settings, AppScreen.Guide -> Destination.Settings
                        AppScreen.Servers, AppScreen.Detail -> Destination.Servers
                        AppScreen.Network -> Destination.Network
                        else -> Destination.Dashboard
                    },
                    onSelect = { destination ->
                        when (destination) {
                            Destination.Settings -> controller.goSettings()
                            Destination.Servers -> controller.goServers()
                            Destination.Network -> controller.goNetwork()
                            Destination.Dashboard -> controller.goSetup()
                        }
                    },
                    expanded = false,
                    title = title,
                    subtitle = subtitle,
                    navigationIcon = back,
                    actions = {
                        if (state.screen == AppScreen.Network) {
                            IconButton(onClick = { controller.openLogs("network") }) {
                                Symbol(Symbols.description, size = 22.dp)
                            }
                        }
                        if (state.screen == AppScreen.Setup || state.screen == AppScreen.Servers) {
                            IconButton(onClick = { controller.goSettings() }) {
                                Symbol(Symbols.settings, size = 22.dp)
                            }
                        }
                    },
                    floatingAction = if (state.screen == AppScreen.Servers) {
                        { app.remotehub.ui.NewServerFab(onClick = { controller.startCreate() }) }
                    } else {
                        null
                    },
                ) { padding ->
                    val content = Modifier.padding(padding).padding(vertical = 4.dp)
                    when (state.screen) {
                        AppScreen.Setup -> SetupContent(
                            state = state,
                            onTokenChange = { state = state.copy(tokenInput = it) },
                            onConnectGitHub = { controller.connectGitHub(state.tokenInput) },
                            onAuthKeyChange = { state = state.copy(authKeyInput = it) },
                            onConnectNetwork = { controller.connectNetwork(state.authKeyInput) },
                            onOpenUrl = { handleEffect(SideEffect.OpenUrl(it)) },
                            onCreate = { controller.startCreate() },
                            onOpenServer = { controller.openServer(it) },
                            onDisconnectGitHub = { controller.disconnectGitHub() },
                            onDisconnectNetwork = { controller.disconnectNetwork() },
                            onOpenLogs = { controller.openLogs("error") },
                            modifier = content,
                        )

                        AppScreen.Create -> CreateContent(
                            state = state,
                            onNameChange = { controller.setServerName(it) },
                            onDurationChange = { controller.setDuration(it) },
                            onStart = { controller.provision() },
                            onBack = { controller.goSetup() },
                            modifier = content,
                        )

                        AppScreen.Progress -> ProgressContent(
                            state = state,
                            onRetry = { controller.retryProvision() },
                            onCancel = { controller.cancelProvision() },
                            onOpenServer = { state.activeServerId?.let { controller.openServer(it) } },
                            modifier = content,
                        )

                        AppScreen.Detail -> if (active != null) {
                            SessionDetailContent(
                                server = active,
                                passwordVisible = state.passwordVisible,
                                busy = state.busy,
                                error = state.error,
                                onTogglePassword = { controller.togglePassword() },
                                onCopy = { label, value -> handleEffect(SideEffect.Copy(label, value)) },
                                onConnect = { controller.connectRemote(active.id) },
                                onRerun = { controller.rerunServer(active.id) },
                                onBackup = { controller.createBackup(active.id) },
                                onStop = { controller.stopServer(active.id) },
                                onOpenRun = { handleEffect(SideEffect.OpenUrl(active.workflowRunUrl)) },
                                modifier = content,
                            )
                        }

                        AppScreen.Settings -> SettingsContent(
                            themeMode = state.themeMode,
                            localeTag = state.localeTag,
                            expiryAlertEnabled = state.expiryAlertEnabled,
                            warningMinutes = state.warningMinutes,
                            onTheme = { controller.setTheme(it) },
                            onLocale = { controller.setLocale(it) },
                            onExpiryAlert = { controller.setExpiryAlert(it) },
                            onWarningMinutes = { controller.setWarningMinutes(it) },
                            onGuide = { controller.openGuide(it) },
                            onLogs = { controller.openLogs() },
                            modifier = content,
                        )

                        AppScreen.Guide -> GuideContent(
                            guideId = state.guideId ?: "github",
                            modifier = content,
                        )

                        AppScreen.Logs -> LogsContent(
                            entries = state.logs,
                            filter = state.logFilter,
                            query = state.logQuery,
                            expandedSeq = state.expandedLogSeq,
                            crashNotice = state.crashNotice,
                            environment = state.environmentReport,
                            onFilter = { controller.setLogFilter(it) },
                            onQuery = { controller.setLogQuery(it) },
                            onToggleDetail = { controller.toggleLogDetail(it) },
                            onCopy = { controller.copyLogs() },
                            onShare = { controller.shareLogs() },
                            onClear = { controller.clearLogs() },
                            onRefresh = { controller.refreshLogs() },
                            onDismissCrash = { controller.dismissCrashNotice() },
                            modifier = content,
                        )

                        AppScreen.Servers -> ServersContent(
                            servers = state.servers.sortedByDescending { it.startedAtEpochMs },
                            padding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                            onOpen = { controller.openServer(it) },
                            onConnect = { controller.connectRemote(it) },
                            onRestart = { controller.restartSession(it) },
                            onCreate = { controller.startCreate() },
                            modifier = content,
                        )

                        AppScreen.Network -> NetworkContent(
                            tailnet = state.tailnet,
                            devices = devices,
                            deviceVpn = state.deviceVpn,
                            vpnBusy = state.vpnBusy,
                            vpnInterface = state.vpnInterface,
                            padding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                            onToggleVpn = { controller.toggleDeviceVpn() },
                            onConnect = { controller.goSetup() },
                            onDisconnect = { controller.disconnectNetwork() },
                            onCopy = { label, value -> controller.copyValue(label, value) },
                            modifier = content,
                        )

                        AppScreen.Remote -> SetupContent(
                            state = state,
                            onTokenChange = { state = state.copy(tokenInput = it) },
                            onConnectGitHub = { controller.connectGitHub(state.tokenInput) },
                            onAuthKeyChange = { state = state.copy(authKeyInput = it) },
                            onConnectNetwork = { controller.connectNetwork(state.authKeyInput) },
                            onOpenUrl = { handleEffect(SideEffect.OpenUrl(it)) },
                            onCreate = { controller.startCreate() },
                            onOpenServer = { controller.openServer(it) },
                            onDisconnectGitHub = { controller.disconnectGitHub() },
                            onDisconnectNetwork = { controller.disconnectNetwork() },
                            onOpenLogs = { controller.openLogs("error") },
                            modifier = content,
                        )
                    }
                }
            }
        }
    }
}
