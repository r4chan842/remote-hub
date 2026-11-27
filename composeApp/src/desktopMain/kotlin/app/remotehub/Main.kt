package app.remotehub

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import app.remotehub.data.StoreKeys
import app.remotehub.domain.GitHubAccount
import app.remotehub.github.DeviceFlowClient
import app.remotehub.github.DevicePoll
import app.remotehub.github.GitHubClient
import app.remotehub.design.RemoteHubTheme
import app.remotehub.domain.AuthMethod
import app.remotehub.domain.DeviceFlowPhase
import app.remotehub.domain.DeviceFlowState
import app.remotehub.domain.NetworkState
import app.remotehub.domain.QuotaSnapshot
import app.remotehub.domain.TailnetStatus
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
import app.remotehub.ui.SettingsContent

@Composable
fun RemoteHubApp() {
    var destination by remember { mutableStateOf(Destination.Dashboard) }
    var creating by remember { mutableStateOf(false) }
    var themeMode by remember { mutableStateOf(0) }
    var localeTag by remember { mutableStateOf("fa") }
    var authMethod by remember { mutableStateOf(AuthMethod.DeviceFlow) }
    var patInput by remember { mutableStateOf("") }
    val store = remember { app.remotehub.data.createSecureStore() }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var account by remember {
        mutableStateOf(
            store.get(app.remotehub.data.StoreKeys.GITHUB_LOGIN)?.let {
                GitHubAccount(it, it, AuthMethod.entries.firstOrNull { method -> method.name == store.get(StoreKeys.GITHUB_METHOD) } ?: AuthMethod.PersonalAccessToken, emptyList(), emptyList(), "")
            },
        )
    }
    var authBusy by remember { mutableStateOf(false) }
    var authError by remember { mutableStateOf<String?>(null) }
    val idleDeviceFlow = DeviceFlowState(DeviceFlowPhase.Idle, "", DeviceFlowClient.GITHUB_DEVICE_URL, 0, 5)
    var deviceFlow by remember { mutableStateOf(idleDeviceFlow) }
    var deviceJob by remember { mutableStateOf<Job?>(null) }
    var clientIdInput by remember { mutableStateOf(store.get(StoreKeys.GITHUB_CLIENT_ID).orEmpty()) }

    suspend fun validateSignIn(token: String, method: AuthMethod) {
        val client = GitHubClient(token)
        try {
            val result = client.validate()
            store.put(StoreKeys.GITHUB_TOKEN, token)
            store.put(StoreKeys.GITHUB_LOGIN, result.user.login)
            store.put(StoreKeys.GITHUB_METHOD, method.name)
            account = GitHubAccount(result.user.login, result.user.name ?: result.user.login, method, result.scopes, result.missing, "")
            patInput = ""
        } finally {
            client.close()
        }
    }

    fun startDeviceSignIn() {
        if (deviceJob?.isActive == true) return
        val clientId = clientIdInput.trim().ifBlank { System.getenv("RH_GITHUB_CLIENT_ID").orEmpty().trim() }
        if (clientId.isBlank()) {
            authError = "Enter an OAuth Client ID with Device Flow enabled, or use a personal access token."
            return
        }
        authBusy = true
        authError = null
        deviceJob = scope.launch {
            val client = DeviceFlowClient(clientId)
            try {
                val code = client.requestCode()
                deviceFlow = DeviceFlowState(DeviceFlowPhase.WaitingForApproval, code.userCode, DeviceFlowClient.GITHUB_DEVICE_URL, code.expiresIn, code.interval)
                authBusy = false
                when (val result = client.awaitToken(code) { remaining -> deviceFlow = deviceFlow.copy(secondsRemaining = remaining) }) {
                    is DevicePoll.Granted -> {
                        authBusy = true
                        validateSignIn(result.token, AuthMethod.DeviceFlow)
                        deviceFlow = idleDeviceFlow
                    }
                    is DevicePoll.Failed -> {
                        deviceFlow = idleDeviceFlow
                        authError = result.reason
                    }
                    else -> Unit
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                deviceFlow = idleDeviceFlow
                authError = error.message ?: "Could not sign in to GitHub."
            } finally {
                authBusy = false
                client.close()
            }
        }
    }
    var authKeyInput by remember { mutableStateOf("") }
    var expiryAlert by remember { mutableStateOf(true) }
    var biometric by remember { mutableStateOf(false) }
    var form by remember {
        mutableStateOf(
            CreateForm(
                name = "",
                provider = "",
                region = "",
                osImage = "",
                machineSize = "",
                warningMinutes = 15,
                autoBackup = true,
                encryptedBackup = true,
            ),
        )
    }
    var wizardStep by remember { mutableStateOf(0) }

    val strings = stringsFor(localeTag)
    val darkTheme = themeMode == 2

    RemoteHubTheme(darkTheme = darkTheme) {
        CompositionLocalProvider(
            LocalStrings provides strings,
            LocalLayoutDirection provides if (strings.rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
        ) {
            val tailnet = TailnetStatus(
                state = NetworkState.Offline,
                tailnetName = "—",
                nodeName = "—",
                nodeAddress = "—",
                relayLabel = "—",
                latencyMillis = 0,
                transportLabel = "—",
                lastError = null,
            )
            val quota = QuotaSnapshot(0, 2000, 0, 20, "6h")

            val title = when {
                creating -> strings.createTitle
                destination == Destination.Dashboard -> strings.navDashboard
                destination == Destination.Servers -> strings.navServers
                destination == Destination.Network -> strings.navNetwork
                else -> strings.navSettings
            }

            AppShell(
                selected = destination,
                onSelect = {
                    destination = it
                    creating = false
                },
                expanded = true,
                title = title,
                floatingAction = {
                    if (destination == Destination.Dashboard && !creating) {
                        NewServerFab(onClick = { creating = true }, extended = true)
                    }
                },
            ) { padding ->
                val contentModifier = Modifier.padding(padding).padding(vertical = 4.dp)
                when {
                    creating -> CreateServerContent(
                        form = form,
                        wizardStep = wizardStep,
                        provisioning = false,
                        steps = emptyList(),
                        repositoryFullName = "—",
                        onNameChange = { form = form.copy(name = it) },
                        onWarningChange = { form = form.copy(warningMinutes = it) },
                        onAutoBackupChange = { form = form.copy(autoBackup = it) },
                        onBack = { if (wizardStep > 0) wizardStep-- else creating = false },
                        onNext = { wizardStep++ },
                        onCreate = {},
                        onCancel = { creating = false },
                        modifier = contentModifier,
                    )
                    destination == Destination.Dashboard -> DashboardContent(
                        account = null,
                        tailnet = tailnet,
                        providerReady = false,
                        servers = emptyList(),
                        quota = quota,
                        expanded = true,
                        onOpenSetup = { destination = Destination.Servers },
                        onOpenServer = {},
                        onConnect = {},
                        onCreate = { creating = true },
                        modifier = contentModifier,
                    )
                    destination == Destination.Servers -> GitHubAuthContent(
                        account = account,
                        method = authMethod,
                        deviceFlow = deviceFlow,
                        patInput = patInput,
                        clientIdInput = clientIdInput,
                        onClientIdChange = {
                            clientIdInput = it
                            store.put(StoreKeys.GITHUB_CLIENT_ID, it.trim())
                        },
                        requiredScopes = GitHubClient.REQUIRED_SCOPES,
                        onMethodChange = {
                            if (it != authMethod && !authBusy) {
                                deviceJob?.cancel()
                                deviceFlow = idleDeviceFlow
                                authError = null
                                authMethod = it
                            }
                        },
                        onPatChange = { patInput = it },
                        onCopyCode = {
                            runCatching {
                                java.awt.Toolkit.getDefaultToolkit().systemClipboard.setContents(java.awt.datatransfer.StringSelection(deviceFlow.userCode), null)
                            }.onFailure { authError = "Could not copy the device code." }
                        },
                        onOpenGitHub = {
                            if (deviceFlow.userCode.isBlank()) startDeviceSignIn()
                            else openInBrowser(DeviceFlowClient.GITHUB_DEVICE_URL)
                        },
                        onCreateToken = { openInBrowser("https://github.com/settings/tokens/new?scopes=repo,workflow&description=RemoteHub") },
                        onValidatePat = {
                            val token = patInput.trim()
                            authBusy = true
                            authError = null
                            scope.launch {
                                try {
                                    validateSignIn(token, AuthMethod.PersonalAccessToken)
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (error: Exception) {
                                    authError = error.message ?: "Could not sign in to GitHub."
                                } finally {
                                    authBusy = false
                                }
                            }
                        },
                        onDisconnect = {
                            store.remove(app.remotehub.data.StoreKeys.GITHUB_TOKEN)
                            store.remove(app.remotehub.data.StoreKeys.GITHUB_LOGIN)
                            store.remove(StoreKeys.GITHUB_METHOD)
                            account = null
                        },
                        error = authError,
                        busy = authBusy,
                        modifier = contentModifier,
                    )
                    destination == Destination.Network -> NetworkContent(
                        status = tailnet,
                        authKeyInput = authKeyInput,
                        onAuthKeyChange = { authKeyInput = it },
                        onConnect = {},
                        onDisconnect = {},
                        onOfficialSignIn = {},
                        modifier = contentModifier,
                    )
                    else -> SettingsContent(
                        themeMode = themeMode,
                        localeTag = localeTag,
                        expiryAlertEnabled = expiryAlert,
                        alertThresholdMinutes = form.warningMinutes,
                        biometricEnabled = biometric,
                        storageEncrypted = true,
                        appVersion = "0.1.0",
                        onThemeChange = { themeMode = it },
                        onLocaleChange = { localeTag = it },
                        onExpiryAlertChange = { expiryAlert = it },
                        onBiometricChange = { biometric = it },
                        onClearCredentials = {
                            patInput = ""
                            authKeyInput = ""
                        },
                        onOpenGuide = { openInBrowser("https://github.com/r4chan842/remote-hub#readme") },
                        modifier = contentModifier,
                    )
                }
            }
        }
    }
}

/** Opens a URL in the system browser; falls back to the OS opener when AWT Desktop is unavailable. */
fun openInBrowser(url: String) {
    val ok = runCatching {
        val d = java.awt.Desktop.getDesktop()
        if (java.awt.Desktop.isDesktopSupported() && d.isSupported(java.awt.Desktop.Action.BROWSE)) {
            d.browse(java.net.URI(url)); true
        } else false
    }.getOrDefault(false)
    if (!ok) {
        val os = System.getProperty("os.name").lowercase()
        val cmd = when {
            os.contains("win") -> listOf("rundll32", "url.dll,FileProtocolHandler", url)
            os.contains("mac") -> listOf("open", url)
            else -> listOf("xdg-open", url)
        }
        runCatching { ProcessBuilder(cmd).start() }
    }
}

fun main() {
    application {
        Window(
            onCloseRequest = ::exitApplication,
            state = rememberWindowState(width = 1280.dp, height = 880.dp),
            title = "RemoteHub",
        ) {
            RemoteHubApp()
        }
    }
}
