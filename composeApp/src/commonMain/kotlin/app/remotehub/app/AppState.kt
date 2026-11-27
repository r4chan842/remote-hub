package app.remotehub.app

import app.remotehub.domain.AuthMethod
import app.remotehub.domain.DeviceFlowPhase
import app.remotehub.domain.DeviceFlowState
import app.remotehub.domain.GitHubAccount
import app.remotehub.domain.NetworkState
import app.remotehub.domain.ServerSession
import app.remotehub.domain.TailnetStatus
import app.remotehub.ui.CreateForm
import app.remotehub.ui.Destination

data class AppState(
    val destination: Destination = Destination.Dashboard,
    val creating: Boolean = false,
    val wizardStep: Int = 0,
    val openServerId: String? = null,
    val remoteServerId: String? = null,
    val guideId: String? = null,
    val showAuth: Boolean = false,
    val themeMode: Int = 0,
    val localeTag: String = "fa",
    val authMethod: AuthMethod = AuthMethod.DeviceFlow,
    val patInput: String = "",
    val authKeyInput: String = "",
    val providerTokenInput: String = "",
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
    val deviceFlow: DeviceFlowState = DeviceFlowState(
        phase = DeviceFlowPhase.Idle,
        userCode = "",
        verificationUrl = "https://github.com/login/device",
        secondsRemaining = 0,
        pollIntervalSeconds = 5,
    ),
    val servers: List<ServerSession> = emptyList(),
    val form: CreateForm = CreateForm(
        name = "",
        provider = "hetzner",
        region = "fsn1",
        osImage = "ubuntu-24.04",
        machineSize = "cx22",
        warningMinutes = 15,
        autoBackup = true,
        encryptedBackup = true,
    ),
    val provisioning: Boolean = false,
    val busy: Boolean = false,
    val passwordVisible: Boolean = false,
    val expiryAlertEnabled: Boolean = true,
    val biometricEnabled: Boolean = false,
    val repositoryName: String = "remote-hub-sessions",
    val toast: String? = null,
    val error: String? = null,
    val appVersion: String = "1.0.0",
)

sealed interface SideEffect {
    data class OpenUrl(val url: String) : SideEffect
    data class Copy(val label: String, val value: String) : SideEffect
    data class Notify(val title: String, val body: String) : SideEffect
    data class ShareText(val text: String) : SideEffect
}
