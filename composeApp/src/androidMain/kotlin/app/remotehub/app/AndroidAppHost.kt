package app.remotehub.app

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import app.remotehub.MainActivity
import app.remotehub.data.SecureStore
import app.remotehub.data.StoreKeys
import app.remotehub.data.createSecretBox
import app.remotehub.data.createSecureStore
import app.remotehub.design.RemoteHubTheme
import app.remotehub.domain.AuthMethod
import app.remotehub.domain.DeviceFlowPhase
import app.remotehub.domain.DeviceFlowState
import app.remotehub.domain.GitHubAccount
import app.remotehub.github.DeviceFlowClient
import app.remotehub.github.DevicePoll
import app.remotehub.github.GitHubClient
import app.remotehub.i18n.LocalStrings
import app.remotehub.i18n.stringsFor
import app.remotehub.net.SessionService
import app.remotehub.net.TailscaleNode
import app.remotehub.ui.AppShell
import app.remotehub.ui.BackIcon
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
import app.remotehub.ui.SettingsGuides
import app.remotehub.rdp.RdpSurface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class AndroidAppHost(private val activity: ComponentActivity) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val store: SecureStore = createSecureStore()
    private val secretBox = createSecretBox()
    private val controller = SessionController(activity, store, secretBox, scope)

    var state by mutableStateOf(AppState())
        private set

    private var ticker: Job? = null
    private var deviceFlowClient: DeviceFlowClient? = null

    init {
        controller.onState = { state = it }
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
            is SideEffect.ShareText -> {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, effect.text)
                }
                activity.startActivity(Intent.createChooser(intent, effect.text.take(24)))
            }
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
        deviceFlowClient?.close()
        controller.release()
        scope.cancel()
    }

    fun startDeviceFlow() {
        scope.launch {
            val client = deviceFlowClient ?: DeviceFlowClient(GITHUB_CLIENT_ID).also { deviceFlowClient = it }
            state = state.copy(busy = true, error = null)
            runCatching { client.requestCode() }
                .onSuccess { code ->
                    state = state.copy(
                        busy = false,
                        deviceFlow = DeviceFlowState(
                            phase = DeviceFlowPhase.CodeIssued,
                            userCode = code.userCode,
                            verificationUrl = code.verificationUri,
                            secondsRemaining = code.expiresIn,
                            pollIntervalSeconds = code.interval,
                        ),
                    )
                    handleEffect(SideEffect.Copy("user code", code.userCode))
                    pollDeviceFlow(client, code)
                }
                .onFailure { error ->
                    state = state.copy(busy = false, error = error.message ?: "device flow failed")
                }
        }
    }

    private suspend fun pollDeviceFlow(client: DeviceFlowClient, code: app.remotehub.github.DeviceCodeResponse) {
        state = state.copy(
            deviceFlow = state.deviceFlow.copy(phase = DeviceFlowPhase.WaitingForApproval),
        )
        when (val result = client.awaitToken(code) { remaining ->
            state = state.copy(deviceFlow = state.deviceFlow.copy(secondsRemaining = remaining))
        }) {
            is DevicePoll.Granted -> {
                state = state.copy(deviceFlow = state.deviceFlow.copy(phase = DeviceFlowPhase.Approved))
                controller.acceptToken(result.token, AuthMethod.DeviceFlow, result.scopes)
            }
            is DevicePoll.Failed -> {
                val phase = if (result.reason == "expired_token") DeviceFlowPhase.Expired else DeviceFlowPhase.Denied
                state = state.copy(
                    deviceFlow = state.deviceFlow.copy(phase = phase),
                    error = result.reason,
                )
            }
            else -> Unit
        }
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
                if (!granted) permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        RemoteHubTheme(darkTheme = dark) {
            CompositionLocalProvider(
                LocalStrings provides strings,
                LocalLayoutDirection provides if (strings.rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
            ) {
                val openServer = state.servers.firstOrNull { it.id == state.openServerId }
                val remoteServer = state.servers.firstOrNull { it.id == state.remoteServerId }
                val expanded = false

                val title = when {
                    state.showAuth -> strings.githubTitle
                    state.guideId != null -> strings.guides
                    remoteServer != null -> strings.remoteTitle
                    openServer != null -> openServer.name
                    state.creating -> strings.createTitle
                    state.destination == Destination.Dashboard -> strings.appName
                    state.destination == Destination.Servers -> strings.navServers
                    state.destination == Destination.Network -> strings.navNetwork
                    else -> strings.navSettings
                }

                val showBack = state.showAuth || state.guideId != null || remoteServer != null ||
                    openServer != null || state.creating

                AppShell(
                    selected = state.destination,
                    onSelect = {
                        state = state.copy(
                            destination = it,
                            creating = false,
                            openServerId = null,
                            remoteServerId = null,
                            guideId = null,
                            showAuth = false,
                        )
                    },
                    expanded = expanded,
                    title = title,
                    subtitle = state.account?.login?.let { "@$it" },
                    navigationIcon = if (showBack) {
                        {
                            BackIcon {
                                state = when {
                                    state.showAuth -> state.copy(showAuth = false)
                                    state.guideId != null -> state.copy(guideId = null)
                                    state.remoteServerId != null -> state.copy(remoteServerId = null)
                                    state.openServerId != null -> state.copy(openServerId = null)
                                    else -> state.copy(creating = false, wizardStep = 0)
                                }
                            }
                        }
                    } else {
                        null
                    },
                    floatingAction = {
                        if (state.destination == Destination.Dashboard && !state.creating &&
                            openServer == null && remoteServer == null && state.guideId == null
                        ) {
                            NewServerFab(onClick = { state = state.copy(creating = true) })
                        }
                    },
                ) { padding ->
                    val contentModifier = Modifier.padding(padding).padding(vertical = 4.dp)
                    when {
                        state.showAuth -> GitHubAuthContent(
                            account = state.account,
                            method = state.authMethod,
                            deviceFlow = state.deviceFlow,
                            patInput = state.patInput,
                            requiredScopes = GitHubClient.REQUIRED_SCOPES,
                            onMethodChange = { state = state.copy(authMethod = it) },
                            onPatChange = { state = state.copy(patInput = it) },
                            onCopyCode = { handleEffect(SideEffect.Copy("user code", state.deviceFlow.userCode)) },
                            onOpenGitHub = {
                                if (state.deviceFlow.userCode.isBlank()) startDeviceFlow()
                                else handleEffect(SideEffect.OpenUrl(state.deviceFlow.verificationUrl))
                            },
                            onValidatePat = {
                                controller.acceptToken(state.patInput, AuthMethod.PersonalAccessToken, null)
                            },
                            onDisconnect = { controller.disconnectGitHub() },
                            modifier = contentModifier,
                        )
                        state.guideId != null -> SettingsGuides(
                            guideId = state.guideId!!,
                            onClose = { state = state.copy(guideId = null) },
                            modifier = contentModifier,
                        )
                        remoteServer != null -> RemoteContent(
                            server = remoteServer,
                            metrics = controller.remoteMetrics(remoteServer.id),
                            onDisconnect = { controller.disconnectRemote(remoteServer.id) },
                            onKeyboard = { controller.showKeyboard() },
                            onClipboard = { controller.pushClipboard(remoteServer.id) },
                            onCtrlAltDel = { controller.sendCtrlAltDel(remoteServer.id) },
                            onToggleInputMode = { controller.toggleInputMode(remoteServer.id) },
                            onFullscreen = { controller.toggleFullscreen() },
                            modifier = contentModifier,
                            desktopSurface = {
                                RdpSurface(
                                    session = controller.rdpSession(remoteServer.id),
                                    trackpadMode = controller.remoteMetrics(remoteServer.id).trackpadMode,
                                )
                            },
                        )
                        openServer != null -> ServerDetailContent(
                            server = openServer,
                            passwordVisible = state.passwordVisible,
                            expanded = expanded,
                            onTogglePassword = { state = state.copy(passwordVisible = !state.passwordVisible) },
                            onConnect = { controller.connectRemote(openServer.id) },
                            onStop = { controller.stopServer(openServer.id) },
                            onRestart = { controller.restartServer(openServer.id) },
                            onRebuild = { controller.rebuildServer(openServer.id) },
                            onRerun = { controller.rerunWorkflow(openServer.id) },
                            onCreateBackup = { controller.createBackup(openServer.id) },
                            onRestoreBackup = { controller.restoreBackup(openServer.id, it) },
                            onOpenRun = { handleEffect(SideEffect.OpenUrl(openServer.workflowRunUrl)) },
                            modifier = contentModifier,
                        )
                        state.creating -> CreateServerContent(
                            form = state.form,
                            wizardStep = state.wizardStep,
                            provisioning = state.provisioning,
                            steps = controller.provisionSteps,
                            repositoryFullName = "${state.account?.login ?: ""}/${state.repositoryName}",
                            onNameChange = { state = state.copy(form = state.form.copy(name = it)) },
                            onWarningChange = { state = state.copy(form = state.form.copy(warningMinutes = it)) },
                            onAutoBackupChange = { state = state.copy(form = state.form.copy(autoBackup = it)) },
                            onBack = {
                                state = if (state.wizardStep > 0) {
                                    state.copy(wizardStep = state.wizardStep - 1)
                                } else {
                                    state.copy(creating = false)
                                }
                            },
                            onNext = { state = state.copy(wizardStep = state.wizardStep + 1) },
                            onCreate = { controller.provision(state.form) },
                            onCancel = { controller.cancelProvision() },
                            modifier = contentModifier,
                        )
                        state.destination == Destination.Dashboard || state.destination == Destination.Servers ->
                            DashboardContent(
                                account = state.account,
                                tailnet = state.tailnet,
                                providerReady = controller.providerReady,
                                servers = state.servers,
                                quota = controller.quota(),
                                expanded = expanded,
                                onOpenSetup = {
                                    state = if (state.account == null) {
                                        state.copy(showAuth = true)
                                    } else {
                                        state.copy(destination = Destination.Network)
                                    }
                                },
                                onOpenServer = { state = state.copy(openServerId = it) },
                                onConnect = { controller.connectRemote(it) },
                                onCreate = { state = state.copy(creating = true) },
                                modifier = contentModifier,
                            )
                        state.destination == Destination.Network -> NetworkContent(
                            status = state.tailnet,
                            authKeyInput = state.authKeyInput,
                            onAuthKeyChange = { state = state.copy(authKeyInput = it) },
                            onConnect = { controller.connectNetwork(state.authKeyInput) },
                            onDisconnect = { controller.disconnectNetwork() },
                            onOfficialSignIn = { controller.openTailscaleLogin() },
                            modifier = contentModifier,
                        )
                        else -> SettingsContent(
                            themeMode = state.themeMode,
                            localeTag = state.localeTag,
                            expiryAlertEnabled = state.expiryAlertEnabled,
                            alertThresholdMinutes = state.form.warningMinutes,
                            biometricEnabled = state.biometricEnabled,
                            storageEncrypted = true,
                            appVersion = state.appVersion,
                            onThemeChange = {
                                state = state.copy(themeMode = it)
                                store.put(StoreKeys.THEME, it.toString())
                            },
                            onLocaleChange = {
                                state = state.copy(localeTag = it)
                                store.put(StoreKeys.LOCALE, it)
                            },
                            onExpiryAlertChange = { state = state.copy(expiryAlertEnabled = it) },
                            onBiometricChange = { controller.setBiometric(it) },
                            onClearCredentials = { controller.clearCredentials() },
                            onOpenGuide = { state = state.copy(guideId = it) },
                            modifier = contentModifier,
                        )
                    }
                }

            }
        }
    }

    companion object {
        const val GITHUB_CLIENT_ID = "Ov23liRhRemoteHub"
    }
}
