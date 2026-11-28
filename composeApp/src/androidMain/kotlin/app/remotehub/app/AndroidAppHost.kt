package app.remotehub.app

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import app.remotehub.MainActivity
import app.remotehub.data.SecureStore
import app.remotehub.data.createSecretBox
import app.remotehub.data.createSecureStore
import app.remotehub.design.InlineMessage
import app.remotehub.design.MessageTone
import app.remotehub.design.RemoteHubTheme
import app.remotehub.design.Symbols
import app.remotehub.diag.Diagnostics
import app.remotehub.i18n.LocalStrings
import app.remotehub.i18n.stringsFor
import app.remotehub.rdp.RdpKeyboardTarget
import app.remotehub.rdp.RdpSurface
import app.remotehub.ui.AppShell
import app.remotehub.ui.BackIcon
import app.remotehub.ui.BarAction
import app.remotehub.ui.CreateContent
import app.remotehub.ui.Destination
import app.remotehub.ui.FindingAddressContent
import app.remotehub.ui.GuideContent
import app.remotehub.ui.LogsContent
import app.remotehub.ui.NetworkContent
import app.remotehub.ui.NewServerFab
import app.remotehub.ui.ProgressContent
import app.remotehub.ui.RemoteContent
import app.remotehub.ui.ServersContent
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
 * The composition root.
 *
 * Navigation is now a pop/push over per-tab stacks, so Back is one call rather than a
 * per-screen hard-coded destination. Transient feedback goes through a Material 3
 * Snackbar instead of an Android Toast, which means it is themed, laid out inside the
 * Scaffold, and can carry an action.
 */
class AndroidAppHost(private val activity: ComponentActivity) {
    /** Only an Activity can show the system VPN dialog, so the Activity owns this. */
    var requestVpnConsent: (Intent) -> Unit = {}

    /** Hides the system bars while the remote desktop is fullscreen. */
    var setImmersive: (Boolean) -> Unit = {}

    fun onVpnConsent(granted: Boolean) = controller.onVpnConsent(granted)

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
                controller.copiedNotice()
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
        val snackbars = remember { SnackbarHostState() }

        val permissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { granted -> controller.setNotificationPermission(granted) }

        LaunchedEffect(Unit) {
            if (Build.VERSION.SDK_INT >= 33) {
                val granted = ContextCompat.checkSelfPermission(
                    activity,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) == PackageManager.PERMISSION_GRANTED
                if (granted) controller.setNotificationPermission(true)
                else permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                controller.setNotificationPermission(true)
            }
        }

        // One message at a time, keyed by sequence so an identical repeat still shows.
        LaunchedEffect(state.message?.seq) {
            val message = state.message ?: return@LaunchedEffect
            val result = snackbars.showSnackbar(
                message = message.text,
                actionLabel = message.actionLabel,
                withDismissAction = true,
            )
            if (result == SnackbarResult.ActionPerformed) controller.openProgress()
            controller.clearMessage()
        }

        RemoteHubTheme(darkTheme = dark) {
            CompositionLocalProvider(
                LocalStrings provides strings,
                LocalLayoutDirection provides if (strings.rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
            ) {
                val route = state.nav.route
                val active = state.activeServer

                // The desktop owns the whole screen; it is chrome-free by design.
                if (route is Route.Remote && active != null) {
                    BackHandler {
                        if (state.remoteFullscreen) controller.toggleRemoteFullscreen()
                        else controller.disconnectRemote(active.id)
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
                        // The IME needs a focused editor; 1dp so it never covers the desktop.
                        RdpKeyboardTarget(
                            visible = state.keyboardVisible,
                            onText = { controller.typeRemote(active.id, it) },
                            onScancode = { code, down -> controller.remoteKey(active.id, code, down) },
                            onPaste = { controller.pasteRemote(active.id) },
                            onImeVisibility = { controller.onImeVisibility(it) },
                            modifier = Modifier.align(Alignment.BottomStart).size(1.dp),
                        )
                        SnackbarHost(snackbars, Modifier.align(Alignment.BottomCenter)) { data -> Snackbar(data) }
                    }
                    return@CompositionLocalProvider
                }

                // One Back handler for the whole app: pop the current tab's stack.
                BackHandler(enabled = state.nav.canGoBack) { controller.goBack() }

                val title = when (route) {
                    Route.Setup -> strings.setupTitle
                    Route.Create -> strings.createTitle
                    Route.Progress -> strings.progressTitle
                    Route.Servers -> strings.serversTitle
                    Route.Network -> strings.networkScreenTitle
                    Route.Settings -> strings.settingsTitle
                    Route.Logs -> strings.logsTitle
                    is Route.Guide -> strings.guides
                    is Route.Detail -> state.servers.firstOrNull { it.id == route.serverId }?.name ?: strings.sessionDetail
                    is Route.Remote -> strings.remoteDesktop
                }

                AppShell(
                    selected = state.tab,
                    onSelect = { destination -> controller.goTab(destination) },
                    title = title,
                    navigationIcon = if (state.nav.stack.size > 1) {
                        { BackIcon(onClick = { controller.goBack() }) }
                    } else null,
                    actions = {
                        when (route) {
                            Route.Network -> BarAction(Symbols.description) { controller.openLogs("network") }
                            Route.Setup, Route.Servers -> BarAction(Symbols.settings) { controller.goSettings() }
                            else -> Unit
                        }
                    },
                    floatingAction = if (route == Route.Servers && state.servers.isNotEmpty()) {
                        { NewServerFab(onClick = { controller.startCreate() }) }
                    } else null,
                    snackbarHost = { SnackbarHost(snackbars) { data -> Snackbar(data) } },
                ) { padding ->
                    val content = Modifier.padding(padding)
                    Box(contentAlignment = Alignment.TopCenter) {
                        when (route) {
                            Route.Setup -> SetupContent(
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

                            Route.Create -> CreateContent(
                                state = state,
                                onNameChange = { controller.setServerName(it) },
                                onDurationChange = { controller.setDuration(it) },
                                onStart = { controller.provision() },
                                onBack = { controller.goBack() },
                                modifier = content,
                            )

                            Route.Progress -> ProgressContent(
                                state = state,
                                onRetry = { controller.retryProvision() },
                                onCancel = { controller.cancelProvision() },
                                onOpenServer = {
                                    state.operation.serverId?.let { controller.openServer(it) }
                                        ?: state.activeServerId?.let { controller.openServer(it) }
                                },
                                modifier = content,
                                onAcknowledge = { controller.startCreate() },
                                onOpenLogs = { controller.openLogs("error") },
                            )

                            is Route.Detail -> {
                                val server = state.servers.firstOrNull { it.id == route.serverId }
                                if (server == null) {
                                    InlineMessage(strings.serversEmpty, content, tone = MessageTone.Info)
                                } else {
                                    SessionDetailContent(
                                        server = server,
                                        passwordVisible = state.passwordVisible,
                                        busy = state.busy,
                                        error = state.error,
                                        onTogglePassword = { controller.togglePassword() },
                                        onCopy = { label, value -> handleEffect(SideEffect.Copy(label, value)) },
                                        onConnect = { controller.connectRemote(server.id) },
                                        onRerun = { controller.rerunServer(server.id) },
                                        onBackup = { controller.createBackup(server.id) },
                                        onStop = { controller.stopServer(server.id) },
                                        onOpenRun = { handleEffect(SideEffect.OpenUrl(server.workflowRunUrl)) },
                                        modifier = content,
                                    )
                                }
                            }

                            Route.Settings -> SettingsContent(
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

                            is Route.Guide -> GuideContent(guideId = route.guideId, modifier = content)

                            Route.Logs -> LogsContent(
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

                            Route.Servers -> ServersContent(
                                servers = state.servers.sortedByDescending { it.startedAtEpochMs },
                                padding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                                onOpen = { controller.openServer(it) },
                                onConnect = { controller.connectRemote(it) },
                                onRestart = { controller.restartSession(it) },
                                onCreate = { controller.startCreate() },
                                modifier = content,
                            )

                            Route.Network -> NetworkScreenWithDevices(
                                state = state,
                                onToggleVpn = { controller.toggleDeviceVpn() },
                                onConnect = { controller.goSetup() },
                                onDisconnect = { controller.disconnectNetwork() },
                                onCopy = { label, value -> controller.copyValue(label, value) },
                                modifier = content,
                                devicesFor = { controller.tailnetDevices() },
                            )

                            is Route.Remote -> Unit // handled above
                        }
                    }
                }
            }
        }
    }
}

/**
 * The peer list is fetched off the main thread and keyed on the tunnel state, so it is
 * refetched when the tunnel changes rather than on every recomposition.
 */
@Composable
private fun NetworkScreenWithDevices(
    state: AppState,
    onToggleVpn: () -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onCopy: (String, String) -> Unit,
    modifier: Modifier = Modifier,
    devicesFor: suspend () -> List<app.remotehub.ui.TailnetDevice>,
) {
    val devices by produceState(
        initialValue = emptyList<app.remotehub.ui.TailnetDevice>(),
        state.tailnet.state,
        state.tailnet.nodeAddress,
    ) {
        value = kotlinx.coroutines.withContext(Dispatchers.IO) {
            runCatching { devicesFor() }.getOrDefault(emptyList())
        }
    }
    NetworkContent(
        tailnet = state.tailnet,
        devices = devices,
        deviceVpn = state.deviceVpn,
        vpnBusy = state.vpnBusy,
        vpnInterface = state.vpnInterface,
        padding = androidx.compose.foundation.layout.PaddingValues(0.dp),
        onToggleVpn = onToggleVpn,
        onConnect = onConnect,
        onDisconnect = onDisconnect,
        onCopy = onCopy,
        modifier = modifier,
    )
}
