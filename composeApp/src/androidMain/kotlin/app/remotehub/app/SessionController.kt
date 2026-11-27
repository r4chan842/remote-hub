package app.remotehub.app

import android.app.NotificationManager
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import app.remotehub.R
import app.remotehub.RemoteHubApplication
import app.remotehub.backup.BackupCodec
import app.remotehub.data.CryptoCheck
import app.remotehub.data.SecureStore
import app.remotehub.data.SecretBox
import app.remotehub.data.StoreKeys
import app.remotehub.data.randomPassword
import app.remotehub.diag.Diagnostics
import app.remotehub.domain.AuthMethod
import app.remotehub.domain.BackupRecord
import app.remotehub.domain.ConnectionInfo
import app.remotehub.domain.GitHubAccount
import app.remotehub.domain.NetworkState
import app.remotehub.domain.OperationKind
import app.remotehub.domain.OperationRecord
import app.remotehub.domain.OperationResult
import app.remotehub.domain.ProvisionStep
import app.remotehub.domain.ServerSession
import app.remotehub.domain.ServerSpec
import app.remotehub.domain.ServerState
import app.remotehub.domain.StepStatus
import app.remotehub.domain.TailnetStatus
import app.remotehub.github.GitHubClient
import app.remotehub.github.GitHubException
import app.remotehub.github.StoredServer
import app.remotehub.i18n.stringsFor
import app.remotehub.net.TailscaleNode
import app.remotehub.net.TailscaleVpnService
import app.remotehub.rdp.RdpKeys
import app.remotehub.rdp.RdpSession
import app.remotehub.server.ProvisionEvent
import app.remotehub.server.ProvisionRequest
import app.remotehub.server.Provisioner
import app.remotehub.server.SessionWorkflow
import app.remotehub.server.base64
import app.remotehub.ui.RemoteMetrics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Owns the whole linear flow: connect GitHub with a personal token, join the private
 * network, create the repository, dispatch the Windows workflow, follow it live, then
 * hand the session to the in-app remote desktop.
 */
class SessionController(
    private val context: Context,
    private val store: SecureStore,
    private val secretBox: SecretBox,
    private val scope: CoroutineScope,
) : TailscaleVpnService.Host {
    var onState: (AppState) -> Unit = {}
    var onEffect: (SideEffect) -> Unit = {}

    private var state = AppState()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }
    private var github: GitHubClient? = null
    private val node = TailscaleNode(context)
    private val rdpSessions = mutableMapOf<String, RdpSession>()
    private var vpnTargetAddress: String = ""
    private var vpnRebuildAttempts = 0
    private val metricsCache = mutableMapOf<String, RemoteMetrics>()
    private var provisionJob: Job? = null

    /** Where to return when leaving Settings; a live run always wins. */
    private var settingsReturn: AppScreen? = null
    private var watchJob: Job? = null
    private var notificationsAllowed = false
    private val alerted = mutableSetOf<String>()
    private var pendingLoginUrl: String? = null

    private fun publish(next: AppState) {
        // The last position is written whenever it changes, so closing the app in the
        // middle of something and reopening lands on the same session.
        if (next.activeServerId != state.activeServerId) {
            val id = next.activeServerId
            if (id.isNullOrBlank()) store.remove(StoreKeys.LAST_SERVER) else store.put(StoreKeys.LAST_SERVER, id)
        }
        if (next.screen != state.screen && next.screen != AppScreen.Remote) {
            store.put(StoreKeys.LAST_SCREEN, next.screen.name)
        }
        state = next
        onState(next)
    }

    // ------------------------------------------------------------- diagnostics

    /** Where the log screen returns to when it is closed. */
    private var logsReturnScreen = AppScreen.Setup

    private fun currentLogs(): List<LogEntry> = Diagnostics.snapshot(state.logFilter, state.logQuery)

    /**
     * Every failure goes through here: the raw engine text is recorded untouched,
     * and the user sees a translated sentence plus the next concrete step.
     */
    private fun fail(source: String, raw: String, throwable: Throwable? = null, silent: Boolean = false) {
        val strings = stringsFor(state.localeTag)
        // GitHubException keeps its API message intact for tests; the status is added
        // here so both the classifier and the log see "github HTTP 403: …".
        val status = (throwable as? GitHubException)?.status
        val described = if (status != null) "github HTTP $status: $raw" else raw
        val classified = classifyError(described, strings)
        Diagnostics.log(LogLevel.Error, source, classified.title, detail = described, throwable = throwable)
        Diagnostics.pullNative()
        if (silent) {
            publish(state.copy(busy = false, errorCode = classified.code, errorDetail = described))
            return
        }
        publish(
            state.copy(
                busy = false,
                error = classified.banner(),
                errorCode = classified.code,
                errorDetail = described,
                logs = if (state.screen == AppScreen.Logs) currentLogs() else state.logs,
            ),
        )
    }

    fun openLogs(filter: String = "all") {
        Diagnostics.pullNative()
        if (state.screen != AppScreen.Logs) logsReturnScreen = state.screen
        publish(
            state.copy(
                screen = AppScreen.Logs,
                logFilter = filter,
                logs = Diagnostics.snapshot(filter, state.logQuery),
                environmentReport = Diagnostics.environmentReport(),
            ),
        )
    }

    fun closeLogs() = publish(state.copy(screen = logsReturnScreen, expandedLogSeq = null))

    fun setLogFilter(filter: String) {
        Diagnostics.pullNative()
        publish(state.copy(logFilter = filter, logs = Diagnostics.snapshot(filter, state.logQuery), expandedLogSeq = null))
    }

    fun setLogQuery(query: String) =
        publish(state.copy(logQuery = query, logs = Diagnostics.snapshot(state.logFilter, query)))

    fun toggleLogDetail(seq: Long) =
        publish(state.copy(expandedLogSeq = if (state.expandedLogSeq == seq) null else seq))

    fun refreshLogs() {
        Diagnostics.pullNative()
        publish(state.copy(logs = currentLogs()))
    }

    fun copyValue(label: String, value: String) {
        if (value.isNotBlank()) onEffect(SideEffect.Copy(label, value))
    }

    fun copyLogs() {
        val text = Diagnostics.exportText(300)
        Diagnostics.info("app", "log copied to clipboard")
        onEffect(SideEffect.Copy(stringsFor(state.localeTag).logsCopied, text))
    }

    fun clearLogs() {
        Diagnostics.clear()
        publish(
            state.copy(
                logs = emptyList(),
                crashNotice = null,
                expandedLogSeq = null,
                toast = stringsFor(state.localeTag).logsCleared,
            ),
        )
    }

    fun shareLogs() {
        val file = Diagnostics.exportFile()
        if (file == null) {
            publish(state.copy(error = stringsFor(state.localeTag).errStorageTitle))
            return
        }
        Diagnostics.info("app", "log exported: ${file.name}")
        onEffect(SideEffect.ShareFile(file.absolutePath, "text/plain"))
    }

    fun dismissCrashNotice() = publish(state.copy(crashNotice = null))

    // ---------------------------------------------------------------- restore

    fun restore() {
        val token = store.get(StoreKeys.GITHUB_TOKEN)
        val login = store.get(StoreKeys.GITHUB_LOGIN)
        var next = state.copy(
            localeTag = store.get(StoreKeys.LOCALE) ?: "fa",
            themeMode = store.get(StoreKeys.THEME)?.toIntOrNull() ?: 0,
            authKeyInput = store.get(StoreKeys.TS_AUTH_KEY) ?: "",
            repositoryName = store.get(REPO_KEY) ?: "remote-hub-sessions",
            warningMinutes = store.get(StoreKeys.WARNING_MINUTES)?.toIntOrNull() ?: 15,
            expiryAlertEnabled = store.get(ALERT_KEY) != "false",
            trackpadMode = store.get(StoreKeys.TRACKPAD) == "true",
        )
        if (token != null && login != null) {
            github = GitHubClient(token)
            next = next.copy(
                account = GitHubAccount(
                    login = login,
                    displayName = login,
                    method = AuthMethod.PersonalAccessToken,
                    grantedScopes = store.get(StoreKeys.GITHUB_SCOPES)?.split(",")?.filter { it.isNotBlank() } ?: emptyList(),
                    missingScopes = emptyList(),
                    tokenExpiresLabel = "",
                ),
            )
        }
        store.get(StoreKeys.SERVERS)?.let { raw ->
            runCatching {
                val list = json.decodeFromString<List<StoredServer>>(raw)
                next = next.copy(servers = list.map { it.toSession() })
            }
        }
        Diagnostics.crashNotice?.let { notice ->
            val strings = stringsFor(next.localeTag)
            next = next.copy(
                crashNotice = "${strings.lastCrashTitle}\n${strings.lastCrashHint}",
                errorDetail = notice.detail.ifBlank { notice.message },
            )
        }
        // Reopen on the session the user was looking at, if it still exists.
        val lastId = store.get(StoreKeys.LAST_SERVER)
        val lastScreen = store.get(StoreKeys.LAST_SCREEN)
        if (lastId != null && next.servers.any { it.id == lastId }) {
            val restored = when (lastScreen) {
                AppScreen.Detail.name, AppScreen.Remote.name -> AppScreen.Detail
                AppScreen.Progress.name -> AppScreen.Detail
                else -> AppScreen.Setup
            }
            next = next.copy(activeServerId = lastId, screen = restored)
        } else if (lastScreen == AppScreen.Servers.name && next.servers.isNotEmpty()) {
            next = next.copy(screen = AppScreen.Servers)
        }
        Diagnostics.info(
            "app",
            "session restored: servers=${next.servers.size} github=${next.account != null} screen=${next.screen}",
        )
        // Prove the crypto engine works now, not halfway through creating a server.
        scope.launch {
            val problem = withContext(Dispatchers.IO) { CryptoCheck.run() }
            if (problem != null) withContext(Dispatchers.Main) { fail("crypto", problem) }
        }
        publish(next.copy(logs = Diagnostics.snapshot(next.logFilter, next.logQuery)))
        if (token != null) refreshAccount()
        val key = store.get(StoreKeys.TS_AUTH_KEY)
        if (!key.isNullOrBlank()) connectNetwork(key, silent = true)
        // The device-wide tunnel is a preference, not a one-shot action: bring it back
        // after a restart so other RDP clients keep working without touching the app.
        if (store.get(StoreKeys.DEVICE_VPN) == "true" && !key.isNullOrBlank()) {
            scope.launch {
                repeat(40) {
                    if (node.running) {
                        if (TailscaleVpnService.consentIntent(context) == null) startDeviceVpn()
                        return@launch
                    }
                    delay(1000)
                }
            }
        }
        next.servers.filter { it.state == ServerState.Provisioning }.forEach { watchRun(it.id) }
    }

    private fun persistServers() {
        val stored = state.servers.map {
            StoredServer(
                id = it.id,
                name = it.name,
                repository = it.repositoryFullName,
                runId = it.runId,
                runNumber = it.workflowRunNumber,
                runUrl = it.workflowRunUrl,
                startedAtEpochMs = it.startedAtEpochMs,
                durationMinutes = it.durationMinutes,
                warningMinutes = it.warningThresholdMinutes,
                address = it.connection?.address ?: "",
                port = it.connection?.port ?: 3389,
                username = it.connection?.username ?: "",
                password = it.connection?.password ?: "",
                state = it.state.name,
            )
        }
        store.put(StoreKeys.SERVERS, json.encodeToString(stored))
    }

    private fun StoredServer.toSession(): ServerSession = ServerSession(
        id = id,
        name = name,
        state = runCatching { ServerState.valueOf(state) }.getOrDefault(ServerState.Stopped),
        spec = ServerSpec(4, 16, 14, "Windows Server 2025", "github-runner", "github"),
        connection = if (username.isBlank()) null else ConnectionInfo(address, port, username, password, ""),
        steps = emptyList(),
        uptimeLabel = "",
        remainingLabel = "",
        remainingFraction = 0f,
        warningThresholdMinutes = warningMinutes,
        workflowRunNumber = runNumber,
        workflowRunUrl = runUrl,
        repositoryFullName = repository,
        operations = emptyList(),
        backups = emptyList(),
        lastError = null,
        runId = runId,
        startedAtEpochMs = startedAtEpochMs,
        durationMinutes = durationMinutes,
    )

    // ---------------------------------------------------------------- setup: GitHub

    fun connectGitHub(rawToken: String) {
        val token = rawToken.trim()
        if (token.isBlank()) {
            publish(state.copy(error = context.getString(R.string.err_token_empty)))
            return
        }
        scope.launch {
            publish(state.copy(busy = true, error = null))
            val client = GitHubClient(token)
            runCatching { client.validate() }
                .onSuccess { validation ->
                    github?.close()
                    github = client
                    store.put(StoreKeys.GITHUB_TOKEN, token)
                    store.put(StoreKeys.GITHUB_LOGIN, validation.user.login)
                    store.put(StoreKeys.GITHUB_METHOD, AuthMethod.PersonalAccessToken.name)
                    store.put(StoreKeys.GITHUB_SCOPES, validation.scopes.joinToString(","))
                    Diagnostics.info(
                        "github",
                        "token accepted for ${validation.user.login} · scopes=${validation.scopes.joinToString(",")} " +
                            "· missing=${validation.missing.joinToString(",")}",
                    )
                    publish(
                        state.copy(
                            busy = false,
                            tokenInput = "",
                            error = if (validation.missing.isEmpty()) null else context.getString(R.string.err_scopes),
                            account = GitHubAccount(
                                login = validation.user.login,
                                displayName = validation.user.name ?: validation.user.login,
                                method = AuthMethod.PersonalAccessToken,
                                grantedScopes = validation.scopes,
                                missingScopes = validation.missing,
                                tokenExpiresLabel = "",
                            ),
                        ),
                    )
                }
                .onFailure { error ->
                    client.close()
                    fail("github", error.message ?: context.getString(R.string.err_token_invalid), error)
                }
        }
    }

    private fun refreshAccount() {
        val client = github ?: return
        scope.launch {
            runCatching { client.validate() }.onSuccess { validation ->
                store.put(StoreKeys.GITHUB_SCOPES, validation.scopes.joinToString(","))
                publish(
                    state.copy(
                        account = state.account?.copy(
                            login = validation.user.login,
                            displayName = validation.user.name ?: validation.user.login,
                            grantedScopes = validation.scopes,
                            missingScopes = validation.missing,
                        ),
                    ),
                )
            }
        }
    }

    fun disconnectGitHub() {
        github?.close()
        github = null
        listOf(StoreKeys.GITHUB_TOKEN, StoreKeys.GITHUB_LOGIN, StoreKeys.GITHUB_METHOD, StoreKeys.GITHUB_SCOPES)
            .forEach { store.remove(it) }
        publish(state.copy(account = null))
    }

    // ---------------------------------------------------------------- setup: network

    fun connectNetwork(rawKey: String, silent: Boolean = false) {
        val key = rawKey.trim()
        if (key.isBlank()) {
            if (!silent) publish(state.copy(error = context.getString(R.string.err_key_empty)))
            return
        }
        scope.launch {
            publish(state.copy(busy = !silent, tailnet = state.tailnet.copy(state = NetworkState.Connecting)))
            store.put(StoreKeys.TS_AUTH_KEY, key)
            // A running node with the same key is left alone: restarting it would
            // tear down every live desktop and change the phone's tailnet address.
            val existing = node.status()
            if (existing.running && !state.deviceVpn) {
                applyNodeStatus(existing)
                node.startPolling { applyNodeStatus(it) }
                publish(state.copy(busy = !silent, error = null))
                return@launch
            }
            runCatching { node.start(hostname = PHONE_HOSTNAME, authKey = key, ephemeral = false) }
                .onSuccess { status ->
                    applyNodeStatus(status)
                    if (status.running) {
                        publish(state.copy(busy = false, error = null, errorCode = null, errorDetail = null))
                        node.startPolling { applyNodeStatus(it) }
                    } else {
                        // Show what the tunnel actually reported instead of a bare status word.
                        val message = status.error.ifBlank { context.getString(R.string.err_network) }
                        publish(state.copy(tailnet = state.tailnet.copy(state = NetworkState.Error, lastError = message)))
                        fail("network", message, silent = silent)
                    }
                }
                .onFailure { error ->
                    publish(
                        state.copy(
                            tailnet = state.tailnet.copy(
                                state = NetworkState.Error,
                                lastError = error.message ?: "tailscale start failed",
                            ),
                        ),
                    )
                    fail("network", error.message ?: context.getString(R.string.err_network), error, silent = silent)
                }
        }
    }

    private fun applyNodeStatus(status: app.remotehub.net.NodeStatus) {
        val networkState = when {
            status.running -> NetworkState.Online
            status.authUrl.isNotBlank() -> NetworkState.Authenticating
            status.state == "Starting" -> NetworkState.Connecting
            status.error.isNotBlank() -> NetworkState.Error
            else -> NetworkState.Offline
        }
        if (status.authUrl.isNotBlank()) pendingLoginUrl = status.authUrl
        publish(
            state.copy(
                tailnet = state.tailnet.copy(
                    state = networkState,
                    tailnetName = status.tailnet,
                    nodeName = status.hostname,
                    nodeAddress = status.ipv4,
                    relayLabel = status.relay,
                    transportLabel = if (status.userspace) "userspace" else "tun",
                    lastError = status.error.ifBlank { null },
                ),
            ),
        )
    }

    // ------------------------------------------------------------ device VPN

    /**
     * Moves the tailnet from "only this app" to "the whole phone".
     *
     * The interface has to carry this node's real tailnet address: a peer drops
     * packets whose source is not an address it knows for us. So the address is read
     * from the running node first, the VpnService is established with it, and only
     * then is the node restarted with the descriptor attached. The node is kept
     * non-ephemeral in this mode so control hands back the same address, and the
     * result is verified afterwards instead of assumed.
     */
    fun toggleDeviceVpn() {
        if (state.deviceVpn) {
            disableDeviceVpn()
            return
        }
        if (!node.running) {
            publish(state.copy(error = context.getString(R.string.err_network_first)))
            return
        }
        val consent = TailscaleVpnService.consentIntent(context)
        if (consent != null) {
            Diagnostics.info("vpn", "asking the system for VPN consent")
            onEffect(SideEffect.RequestVpnConsent)
            return
        }
        startDeviceVpn()
    }

    fun onVpnConsent(granted: Boolean) {
        if (!granted) {
            Diagnostics.warn("vpn", "VPN consent was declined")
            publish(state.copy(vpnBusy = false, error = stringsFor(state.localeTag).vpnDenied))
            return
        }
        startDeviceVpn()
    }

    private fun startDeviceVpn() {
        val status = node.status()
        if (!status.running || status.ipv4.isBlank()) {
            publish(state.copy(error = context.getString(R.string.err_network_first)))
            return
        }
        // Restarting the node drops every live desktop; do it deliberately.
        rdpSessions.values.forEach { it.close() }
        rdpSessions.clear()
        publish(state.copy(vpnBusy = true, error = null))
        TailscaleVpnService.host = this
        vpnTargetAddress = status.ipv4
        val intent = android.content.Intent(context, TailscaleVpnService::class.java)
            .putExtra(TailscaleVpnService.EXTRA_IPV4, status.ipv4)
            .putExtra(TailscaleVpnService.EXTRA_IPV6, status.ipv6)
            .putExtra(TailscaleVpnService.EXTRA_DOMAIN, status.dnsSuffix.ifBlank { status.tailnet })
            .putExtra(TailscaleVpnService.EXTRA_LABEL, status.hostname)
        runCatching { androidx.core.content.ContextCompat.startForegroundService(context, intent) }
            .onFailure {
                Diagnostics.error("vpn", "the VPN service could not start", it)
                publish(state.copy(vpnBusy = false, error = it.message ?: "VPN service failed"))
            }
    }

    override fun onTunReady(fd: Int, interfaceName: String) {
        scope.launch {
            val key = store.get(StoreKeys.TS_AUTH_KEY).orEmpty()
            if (key.isBlank()) {
                publish(state.copy(vpnBusy = false, deviceVpn = false))
                return@launch
            }
            Diagnostics.info("vpn", "restarting the node on the tunnel interface")
            // Non-ephemeral: the node keeps its identity, so the address on the
            // interface stays valid across the restart.
            val status = node.start(hostname = PHONE_HOSTNAME, authKey = key, ephemeral = false, tunFd = fd)
            Diagnostics.pullNative()
            applyNodeStatus(status)
            if (!status.running) {
                stopVpnService()
                publish(state.copy(vpnBusy = false, deviceVpn = false, error = status.error.ifBlank { "tunnel failed" }))
                return@launch
            }
            if (status.ipv4 != vpnTargetAddress) {
                // Control gave a different address. Rebuild the interface once with
                // the address the node actually owns; the node key is now persisted,
                // so the second attempt matches.
                Diagnostics.warn("vpn", "address changed ${vpnTargetAddress} -> ${status.ipv4}; rebuilding the interface")
                vpnRebuildAttempts += 1
                if (vpnRebuildAttempts <= 1) {
                    stopVpnService()
                    startDeviceVpn()
                    return@launch
                }
                // Two attempts and the address still moves: do not claim a tunnel
                // that cannot carry traffic. Fall back to the in-app tunnel.
                vpnRebuildAttempts = 0
                onTunFailed("the tailnet address kept changing while the interface was built")
                return@launch
            }
            vpnRebuildAttempts = 0
            store.put(StoreKeys.DEVICE_VPN, "true")
            node.startPolling { applyNodeStatus(it) }
            publish(
                state.copy(
                    deviceVpn = true,
                    vpnBusy = false,
                    vpnInterface = status.tunName.ifBlank { interfaceName },
                    error = null,
                ),
            )
            Diagnostics.info("vpn", "device-wide tailnet is up on ${status.ipv4}")
        }
    }

    override fun onTunFailed(reason: String) {
        stopVpnService()
        store.remove(StoreKeys.DEVICE_VPN)
        publish(state.copy(deviceVpn = false, vpnBusy = false, vpnInterface = ""))
        fail("vpn", reason)
        // Fall back to the in-app tunnel so the user is never left offline.
        val key = store.get(StoreKeys.TS_AUTH_KEY).orEmpty()
        if (key.isNotBlank()) scope.launch { node.start(PHONE_HOSTNAME, key, ephemeral = false) }
    }

    override fun onTunRevoked() {
        Diagnostics.warn("vpn", "VPN revoked by the system; falling back to the in-app tunnel")
        store.remove(StoreKeys.DEVICE_VPN)
        rdpSessions.values.forEach { it.close() }
        rdpSessions.clear()
        publish(state.copy(deviceVpn = false, vpnBusy = false, vpnInterface = ""))
        val key = store.get(StoreKeys.TS_AUTH_KEY).orEmpty()
        if (key.isNotBlank()) {
            scope.launch {
                applyNodeStatus(node.start(PHONE_HOSTNAME, key, ephemeral = false))
            }
        }
    }

    fun disableDeviceVpn() {
        publish(state.copy(vpnBusy = true))
        store.remove(StoreKeys.DEVICE_VPN)
        rdpSessions.values.forEach { it.close() }
        rdpSessions.clear()
        stopVpnService()
        val key = store.get(StoreKeys.TS_AUTH_KEY).orEmpty()
        scope.launch {
            if (key.isNotBlank()) applyNodeStatus(node.start(PHONE_HOSTNAME, key, ephemeral = false))
            publish(state.copy(deviceVpn = false, vpnBusy = false, vpnInterface = "", error = null))
        }
    }

    private fun stopVpnService() {
        runCatching {
            context.startService(
                android.content.Intent(context, TailscaleVpnService::class.java)
                    .setAction(TailscaleVpnService.ACTION_STOP),
            )
        }
    }

    fun disconnectNetwork() {
        scope.launch {
            rdpSessions.values.forEach { it.close() }
            rdpSessions.clear()
            if (state.deviceVpn) stopVpnService()
            store.remove(StoreKeys.DEVICE_VPN)
            node.stop()
            // The auth key is a credential, not session state: keep it so the next
            // launch signs back in on its own. Settings has the explicit "forget".
            publish(
                state.copy(
                    deviceVpn = false,
                    vpnInterface = "",
                    tailnet = state.tailnet.copy(state = NetworkState.Offline, nodeAddress = ""),
                ),
            )
        }
    }

    fun openTailscaleLogin() {
        val url = pendingLoginUrl ?: node.loginUrl()
        if (url.isNotBlank()) onEffect(SideEffect.OpenUrl(url))
    }

    fun refreshNetworkStatus() {
        if (node.running) applyNodeStatus(node.status())
    }

    fun stopPolling() = node.stopPolling()

    // ---------------------------------------------------------------- create

    fun startCreate() {
        val missing = state.missingItem
        if (missing != null) {
            publish(state.copy(screen = AppScreen.Setup, highlightItem = missing))
            return
        }
        publish(state.copy(screen = AppScreen.Create, error = null))
    }

    fun setServerName(name: String) = publish(state.copy(form = state.form.copy(name = name)))

    fun setDuration(minutes: Int) = publish(state.copy(form = state.form.copy(durationMinutes = minutes)))

    fun provision() {
        val client = github
        val owner = state.account?.login
        if (client == null || owner == null) {
            publish(state.copy(error = context.getString(R.string.err_github_first), screen = AppScreen.Setup))
            return
        }
        val authKey = store.get(StoreKeys.TS_AUTH_KEY)
        if (authKey.isNullOrBlank()) {
            publish(state.copy(error = context.getString(R.string.err_key_empty), screen = AppScreen.Setup))
            return
        }
        CryptoCheck.run()?.let { problem ->
            fail("crypto", problem)
            return
        }
        val form = state.form
        if (form.name.isBlank()) {
            publish(state.copy(error = context.getString(R.string.err_name_empty)))
            return
        }
        Diagnostics.info(
            "provision",
            "creating server name=${form.name} minutes=${form.durationMinutes} repo=${state.repositoryName}",
        )
        publish(state.copy(screen = AppScreen.Progress, provisioning = true, steps = emptyList(), progressLog = emptyList(), error = null))
        provisionJob = scope.launch {
            val provisioner = Provisioner(
                client = client,
                secretBox = secretBox,
                owner = owner,
                workflowTemplate = SessionWorkflow.render(CHECKOUT_SHA, UPLOAD_SHA),
            )
            runCatching {
                provisioner.run(
                    ProvisionRequest(
                        serverName = form.name,
                        repositoryName = state.repositoryName,
                        tailscaleAuthKey = authKey,
                        durationMinutes = form.durationMinutes,
                        warningMinutes = state.warningMinutes,
                        publicRepository = state.publicRepository,
                    ),
                ) { event -> applyProvisionEvent(event) }
            }.onSuccess { outcome ->
                val session = ServerSession(
                    id = outcome.sessionId,
                    name = form.name,
                    state = ServerState.Ready,
                    spec = ServerSpec(4, 16, 14, "Windows Server 2025", "github-runner", "github"),
                    connection = ConnectionInfo("", 3389, outcome.username, outcome.password, ""),
                    steps = state.steps,
                    uptimeLabel = "",
                    remainingLabel = SessionMath.formatDuration(outcome.durationMinutes * 60_000L),
                    remainingFraction = 1f,
                    warningThresholdMinutes = state.warningMinutes,
                    workflowRunNumber = outcome.runNumber,
                    workflowRunUrl = outcome.runUrl,
                    repositoryFullName = "$owner/${state.repositoryName}",
                    operations = listOf(
                        OperationRecord(
                            outcome.sessionId,
                            OperationKind.Provision,
                            OperationResult.Success,
                            "",
                            "run #${outcome.runNumber}",
                        ),
                    ),
                    backups = emptyList(),
                    lastError = null,
                    runId = outcome.runId,
                    startedAtEpochMs = outcome.startedAtEpochMs,
                    durationMinutes = outcome.durationMinutes,
                )
                publish(
                    state.copy(
                        provisioning = false,
                        servers = state.servers.filterNot { it.id == session.id } + session,
                        activeServerId = session.id,
                    ),
                )
                persistServers()
                resolveEndpoint(session.id)
                watchRun(session.id)
            }.onFailure { error ->
                publish(state.copy(provisioning = false))
                fail("provision", error.message ?: context.getString(R.string.err_provision), error)
            }
        }
    }

    private fun applyProvisionEvent(event: ProvisionEvent) {
        when (event) {
            is ProvisionEvent.Steps -> publish(state.copy(steps = event.steps))
            is ProvisionEvent.Log -> {
                Diagnostics.info("provision", event.line)
                publish(state.copy(progressLog = (state.progressLog + event.line).takeLast(20)))
            }
            is ProvisionEvent.Ready -> Diagnostics.info("provision", "session endpoint reported ready")
            is ProvisionEvent.Failed -> fail("provision", event.message)
        }
    }

    fun cancelProvision() {
        provisionJob?.cancel()
        provisionJob = null
        publish(state.copy(provisioning = false, screen = AppScreen.Setup))
    }

    fun retryProvision() = provision()

    /**
     * Restarts the server the user already has, without creating a second one.
     *
     * The existing run is re-run through the GitHub API, so the run id, run number,
     * session id, tailnet hostname and repository secrets are reused. A cancelled or
     * finished session comes back with the same address book entry and the same
     * password; only the clock starts again.
     */
    fun restartSession(serverId: String) {
        val session = state.servers.firstOrNull { it.id == serverId } ?: return
        val client = github
        if (client == null) {
            publish(state.copy(error = context.getString(R.string.err_token_empty)))
            return
        }
        val owner = session.repositoryFullName.substringBefore('/', "")
        val repo = session.repositoryFullName.substringAfter('/', "")
        if (session.runId <= 0L || owner.isBlank() || repo.isBlank()) {
            // Nothing to re-run yet: fall back to a fresh dispatch for this form.
            publish(state.copy(form = CreateForm(session.name, session.durationMinutes)))
            provision()
            return
        }
        rdpSessions.remove(serverId)?.close()
        scope.launch {
            publish(state.copy(busy = true, error = null, screen = AppScreen.Progress, activeServerId = serverId))
            Diagnostics.info("session", "re-running workflow run ${session.runId} for ${session.id}")
            runCatching { client.rerun(owner, repo, session.runId) }
                .onFailure { failure ->
                    Diagnostics.error("session", "rerun failed", failure)
                    publish(state.copy(busy = false))
                    fail("github", failure.message ?: "rerun failed", failure)
                    return@launch
                }
            val now = Clock.System.now().toEpochMilliseconds()
            updateServer(serverId) {
                it.copy(
                    state = ServerState.Provisioning,
                    startedAtEpochMs = now,
                    steps = emptyList(),
                    connection = it.connection?.copy(address = "") ?: it.connection,
                )
            }
            persistServers()
            publish(
                state.copy(
                    busy = false,
                    provisioning = true,
                    steps = listOf(
                        ProvisionStep(
                            id = "rerun",
                            status = StepStatus.Running,
                            detail = "run ${session.runId}",
                            elapsedMillis = null,
                        ),
                    ),
                    progressLog = listOf("re-running workflow run ${session.runId}"),
                ),
            )
            // GitHub needs a moment to open the new attempt; until then the run still
            // reads "completed" and the watcher would call the session finished.
            for (attempt in 1..20) {
                delay(2000)
                val fresh = runCatching { client.run(owner, repo, session.runId) }.getOrNull()
                if (fresh != null && fresh.status != "completed") break
            }
            watchRun(serverId)
        }
    }

    // ---------------------------------------------------------------- follow the run

    private fun watchRun(serverId: String) {
        val client = github ?: return
        watchJob?.cancel()
        watchJob = scope.launch {
            while (true) {
                val session = state.servers.firstOrNull { it.id == serverId } ?: return@launch
                val parts = session.repositoryFullName.split("/")
                if (parts.size != 2) return@launch
                val outcome = runCatching {
                    val run = client.run(parts[0], parts[1], session.runId)
                    val jobs = client.jobs(parts[0], parts[1], session.runId)
                    run to jobs
                }.getOrNull() ?: break
                val (run, jobs) = outcome
                val steps = jobs.flatMap { job ->
                    job.steps.map { step ->
                        ProvisionStep(
                            id = step.name,
                            status = when {
                                step.conclusion == "success" -> StepStatus.Done
                                step.conclusion == "failure" -> StepStatus.Failed
                                step.status == "in_progress" -> StepStatus.Running
                                else -> StepStatus.Pending
                            },
                            detail = step.conclusion,
                            elapsedMillis = null,
                        )
                    }
                }
                val ended = run.status == "completed"
                // The Progress screen reads state.steps, not server.steps. Re-running
                // an existing session only updated the model, so the screen showed
                // "running" with no process until the run finished.
                if (state.activeServerId == serverId) {
                    val logLine = "run ${run.runNumber}: ${run.status}" +
                        (run.conclusion?.let { " ($it)" } ?: "")
                    val log = (state.progressLog + logLine).takeLast(120)
                    publish(state.copy(steps = steps, progressLog = log, provisioning = !ended))
                }
                updateServer(serverId) { current ->
                    current.copy(
                        steps = steps,
                        state = when {
                            ended && run.conclusion == "success" -> ServerState.Stopped
                            ended -> ServerState.Failed
                            current.connection?.address?.isNotBlank() == true -> current.state
                            else -> ServerState.Provisioning
                        },
                        lastError = if (ended && run.conclusion != "success") "run ${run.conclusion}" else null,
                    )
                }
                if (ended) {
                    persistServers()
                    return@launch
                }
                if (state.servers.firstOrNull { it.id == serverId }?.connection?.address.isNullOrBlank()) {
                    resolveEndpoint(serverId)
                }
                // 10s made a re-run look frozen; 3s keeps the step list live.
                delay(3_000)
            }
        }
    }

    /** The phone is on the same tailnet, so the new node shows up as a peer. */
    private suspend fun resolveEndpoint(serverId: String) {
        val session = state.servers.firstOrNull { it.id == serverId } ?: return
        if (!node.running) return
        val hostname = "rh-${session.id}"
        val peers = withContext(Dispatchers.IO) { runCatching { node.peers() } }.getOrDefault(emptyList())
        if (peers.isEmpty()) {
            Diagnostics.info("network", "no tailnet peers yet while looking for $hostname")
        }
        val peer = peers.firstOrNull {
            it.hostname.equals(hostname, true) ||
                it.hostname.equals(session.id, true) ||
                it.dnsName.substringBefore('.').equals(hostname, true)
        }
        if (peer == null && peers.isNotEmpty()) {
            Diagnostics.info("network", "waiting for $hostname; peers: " + peers.joinToString { it.hostname })
        }
        if (peer != null && peer.ipv4.isNotBlank()) {
            updateServer(serverId) {
                it.copy(
                    connection = it.connection?.copy(address = peer.ipv4),
                    state = if (it.state == ServerState.Provisioning) ServerState.Ready else it.state,
                )
            }
            persistServers()
        }
    }

    private fun updateServer(id: String, transform: (ServerSession) -> ServerSession) {
        publish(state.copy(servers = state.servers.map { if (it.id == id) transform(it) else it }))
    }

    // ---------------------------------------------------------------- countdown

    fun tick() {
        if (state.screen == AppScreen.Logs) {
            Diagnostics.pullNative()
            publish(state.copy(logs = currentLogs()))
        }
        if (state.servers.isEmpty()) return
        val now = Clock.System.now().toEpochMilliseconds()
        var changed = false
        val updated = state.servers.map { session ->
            if (session.startedAtEpochMs == 0L) return@map session
            if (session.state == ServerState.Stopped || session.state == ServerState.Failed) return@map session
            val remaining = SessionMath.remainingMillis(session.startedAtEpochMs, session.durationMinutes, now)
            val fraction = SessionMath.remainingFraction(session.startedAtEpochMs, session.durationMinutes, now)
            val next = SessionMath.stateFor(
                session.state, session.startedAtEpochMs, session.durationMinutes,
                session.warningThresholdMinutes, now,
            )
            if (next == ServerState.Expiring && state.expiryAlertEnabled && alerted.add(session.id)) {
                onEffect(
                    SideEffect.Notify(
                        context.getString(R.string.expiry_title),
                        "${session.name}: ${SessionMath.formatDuration(remaining)}",
                    ),
                )
            }
            changed = true
            session.copy(
                state = next,
                remainingLabel = SessionMath.formatDuration(remaining),
                remainingFraction = fraction,
                uptimeLabel = SessionMath.formatDuration(now - session.startedAtEpochMs),
            )
        }
        if (!changed) return
        publish(state.copy(servers = updated))
        updated.forEach { session ->
            rdpSessions[session.id]?.let { metricsCache[session.id] = it.metrics() }
        }
    }

    // ---------------------------------------------------------------- remote desktop

    fun tailnetDevices(): List<app.remotehub.ui.TailnetDevice> =
        node.peers().map { app.remotehub.ui.TailnetDevice(it.hostname, it.ipv4, it.online) }

    fun remoteMetrics(serverId: String): RemoteMetrics =
        metricsCache[serverId] ?: RemoteMetrics(0, 0, 0, "—", state.trackpadMode, true)

    fun rdpSession(serverId: String): RdpSession? = rdpSessions[serverId]

    /** Width / height of the live host desktop, for a pixel-accurate viewport. */
    fun remoteAspectRatio(serverId: String): Float {
        val label = remoteMetrics(serverId).resolutionLabel
        val w = label.substringBefore('x').toIntOrNull() ?: 0
        val h = label.substringAfter('x', "").toIntOrNull() ?: 0
        return if (w > 0 && h > 0) w.toFloat() / h.toFloat() else 16f / 9f
    }

    /**
     * The engine reported a disconnect after the desktop was already live. This used
     * to be ignored entirely, so `connected` went false, the UI read that as "still
     * handshaking", and the last frame sat under a permanent connecting spinner.
     * Reconnect once; if that also fails, surface the real reason.
     */
    private fun handleRemoteDrop(serverId: String, reason: String) = scope.launch {
        // Runs off the engine callback thread: close() joins that thread.
        rdpSessions.remove(serverId)?.close()
        metricsCache.remove(serverId)
        updateServer(serverId) {
            if (it.state == ServerState.Connected) it.copy(state = ServerState.Ready) else it
        }
        stopSessionService()
        Diagnostics.warn("rdp", "session dropped: $reason")
        if (state.screen != AppScreen.Remote || state.activeServerId != serverId) {
            publish(state.copy(remoteConnecting = false))
            return@launch
        }
        publish(state.copy(remoteConnecting = true))
        delay(1200)
        if (state.screen == AppScreen.Remote && state.activeServerId == serverId) {
            Diagnostics.info("rdp", "reconnecting after a dropped link")
            connectRemote(serverId, allowAutoReconnect = false)
        } else {
            publish(state.copy(remoteConnecting = false))
        }
    }

    fun connectRemote(serverId: String, allowAutoReconnect: Boolean = true) {
        val session = state.servers.firstOrNull { it.id == serverId } ?: return
        val connection = session.connection
        if (connection == null || connection.address.isBlank()) {
            publish(state.copy(screen = AppScreen.Remote, activeServerId = serverId, error = null))
            scope.launch {
                repeat(30) {
                    resolveEndpoint(serverId)
                    if (state.servers.firstOrNull { it.id == serverId }?.connection?.address?.isNotBlank() == true) {
                        connectRemote(serverId)
                        return@launch
                    }
                    delay(4000)
                }
                publish(state.copy(error = context.getString(R.string.err_no_address)))
            }
            return
        }
        if (!node.running) {
            publish(state.copy(error = context.getString(R.string.err_network_first)))
            return
        }
        scope.launch {
            publish(
                state.copy(
                    screen = AppScreen.Remote,
                    activeServerId = serverId,
                    busy = true,
                    remoteConnecting = true,
                    error = null,
                ),
            )
            rdpSessions[serverId]?.let {
                publish(state.copy(busy = false, remoteConnecting = false))
                return@launch
            }
            // A quick reachability probe turns "RDP connect call failed" into a
            // sentence the user can act on, and it is logged either way.
            val reachable = node.probe(connection.address, connection.port, 8000)
            if (!reachable) {
                publish(state.copy(busy = false, remoteConnecting = false))
                fail("rdp", "connection refused: ${connection.address}:${connection.port} is not reachable over the tunnel")
                return@launch
            }
            // Two attempts: the graphics pipeline first, then the legacy bitmap path.
            // A host that negotiates EGFX but never delivers a surface must not leave
            // the user with a white desktop.
            var outcome: Result<Unit> = Result.failure(IllegalStateException("not started"))
            for (useGraphics in listOf(true, false)) {
                val rdp = RdpSession(context, node, session, store)
                rdpSessions[serverId] = rdp
                rdp.trackpadMode = state.trackpadMode
                rdp.onUpdate = { metrics ->
                    metricsCache[serverId] = metrics
                    publish(state.copy())
                }
                rdp.onError = { message -> fail("rdp", message) }
                rdp.onDropped = { reason ->
                    if (allowAutoReconnect) {
                        handleRemoteDrop(serverId, reason)
                    } else {
                        rdpSessions.remove(serverId)?.close()
                        metricsCache.remove(serverId)
                        updateServer(serverId) {
                            if (it.state == ServerState.Connected) it.copy(state = ServerState.Ready) else it
                        }
                        publish(state.copy(remoteConnecting = false))
                        fail("rdp", reason)
                    }
                }
                outcome = runCatching { rdp.connect(graphicsPipeline = useGraphics) }
                if (outcome.isSuccess) break
                val problem = outcome.exceptionOrNull()
                rdpSessions.remove(serverId)
                rdp.close()
                val noFrames = problem?.message?.contains("no graphics frames") == true
                if (!useGraphics || !noFrames) break
                Diagnostics.warn(
                    "rdp",
                    "the graphics pipeline produced no frames; retrying on the legacy bitmap path",
                    problem,
                )
            }
            outcome
                .onSuccess {
                    Diagnostics.info("rdp", "connected to ${session.connection?.address}:${session.connection?.port}")
                    updateServer(serverId) { it.copy(state = ServerState.Connected) }
                    startSessionService(session.name)
                    publish(state.copy(busy = false, remoteConnecting = false))
                }
                .onFailure { error ->
                    rdpSessions.remove(serverId)?.close()
                    publish(state.copy(remoteConnecting = false))
                    fail("rdp", error.message ?: context.getString(R.string.err_rdp), error)
                }
        }
    }

    /**
     * Fullscreen is a view mode. It used to be wired to disconnectRemote, so tapping
     * it aborted the connection and FreeRDP reported CONNECT_CANCELLED (0x0002000B).
     */
    fun toggleRemoteFullscreen() {
        val next = !state.remoteFullscreen
        publish(state.copy(remoteFullscreen = next))
        onEffect(SideEffect.Immersive(next))
    }

    fun disconnectRemote(serverId: String) {
        // A gesture interrupted by navigation could otherwise leave a modifier or a
        // mouse button held down on the host for the next session.
        rdpSessions[serverId]?.releaseModifiers()
        rdpSessions.remove(serverId)?.close()
        metricsCache.remove(serverId)
        updateServer(serverId) { if (it.state == ServerState.Connected) it.copy(state = ServerState.Ready) else it }
        stopSessionService()
        if (state.remoteFullscreen) onEffect(SideEffect.Immersive(false))
        publish(
            state.copy(
                screen = AppScreen.Detail,
                remoteFullscreen = false,
                remoteConnecting = false,
                keyboardVisible = false,
                rightClickArmed = false,
            ),
        )
    }

    /**
     * Toggling the IME alone did nothing: Android delivers keystrokes through an
     * InputConnection, which only exists for a focused text editor, and the desktop
     * is a Canvas. This drives the invisible editor in RdpKeyboardTarget instead.
     */
    fun showKeyboard() {
        publish(state.copy(keyboardVisible = !state.keyboardVisible))
    }

    fun hideKeyboard() {
        if (state.keyboardVisible) publish(state.copy(keyboardVisible = false))
    }

    fun onImeVisibility(visible: Boolean) {
        if (state.keyboardVisible != visible) publish(state.copy(keyboardVisible = visible))
    }

    fun typeRemote(serverId: String, text: String) {
        rdpSessions[serverId]?.typeText(text)
    }

    fun remoteKey(serverId: String, scancode: Int, down: Boolean) {
        rdpSessions[serverId]?.sendKey(scancode, down)
    }

    /**
     * Mouse pointer mode has a visible cursor, so the tool right-clicks immediately.
     * Direct touch has none, so it arms the next tap instead of guessing a position.
     */
    fun rightClickRemote(serverId: String) {
        val session = rdpSessions[serverId] ?: return
        if (state.trackpadMode) {
            session.rightClickAtPointer()
            if (state.rightClickArmed) publish(state.copy(rightClickArmed = false))
        } else {
            publish(state.copy(rightClickArmed = !state.rightClickArmed))
        }
    }

    fun consumeRightClick() {
        if (state.rightClickArmed) publish(state.copy(rightClickArmed = false))
    }

    fun pushClipboard(serverId: String) {
        val manager = context.getSystemService(ClipboardManager::class.java)
        val text = manager?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
        if (text.isNullOrBlank()) {
            publish(state.copy(error = context.getString(R.string.err_clipboard_empty)))
            return
        }
        val session = rdpSessions[serverId]
        val sent = session?.sendClipboard(text) ?: false
        if (sent && session != null) {
            // Publishing a format list only puts the text on the host clipboard.
            // Without the chord nothing is inserted, which is why the toolbar
            // button looked like it did nothing.
            scope.launch {
                delay(220)
                session.pressChord(RdpKeys.LCTRL, 0x2F) // V
            }
        }
        publish(state.copy(toast = if (sent) context.getString(R.string.clipboard_sent) else context.getString(R.string.clipboard_pending)))
    }

    /**
     * Paste from the phone keyboard.
     *
     * This types the text instead of using cliprdr: an IME paste has to land at
     * the caret immediately, and the clipboard channel is asynchronous -- the
     * host may not have fetched the format list yet when Ctrl+V arrives.
     */
    fun pasteRemote(serverId: String) {
        val manager = context.getSystemService(ClipboardManager::class.java)
        val text = manager?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
        if (text.isNullOrEmpty()) {
            publish(state.copy(error = context.getString(R.string.err_clipboard_empty)))
            return
        }
        val session = rdpSessions[serverId] ?: return
        scope.launch(Dispatchers.IO) {
            // Chunk so a long paste cannot flood the input queue in one burst.
            text.chunked(64).forEach { chunk ->
                session.typeText(chunk)
                delay(8)
            }
        }
    }

    fun sendCtrlAltDel(serverId: String) {
        rdpSessions[serverId]?.sendCtrlAltDel()
    }

    fun toggleInputMode() {
        val next = !state.trackpadMode
        store.put(StoreKeys.TRACKPAD, if (next) "true" else "false")
        publish(state.copy(trackpadMode = next))
        rdpSessions.values.forEach { it.trackpadMode = next }
    }

    // ---------------------------------------------------------------- lifecycle

    fun stopServer(serverId: String) {
        val client = github ?: return
        val session = state.servers.firstOrNull { it.id == serverId } ?: return
        Diagnostics.info("provision", "stopping server ${session.name} run=${session.runId}")
        scope.launch {
            val parts = session.repositoryFullName.split("/")
            if (parts.size != 2) return@launch
            runCatching { client.cancel(parts[0], parts[1], session.runId) }
                .onFailure { Diagnostics.warn("provision", "cancel run failed", it) }
            rdpSessions.remove(serverId)?.close()
            metricsCache.remove(serverId)
            updateServer(serverId) {
                it.copy(
                    state = ServerState.Stopped,
                    operations = it.operations + OperationRecord(
                        "stop-${Clock.System.now().toEpochMilliseconds()}",
                        OperationKind.Stop, OperationResult.Success, "", "cancelled",
                    ),
                )
            }
            persistServers()
            publish(state.copy(screen = AppScreen.Setup))
        }
    }

    /** Runs the same workflow again. A fresh machine, a fresh address, a fresh password. */
    fun rerunServer(serverId: String) {
        metricsCache.remove(serverId)
        restartSession(serverId)
    }

    // ---------------------------------------------------------------- backup

    fun createBackup(serverId: String) {
        val client = github ?: return
        val session = state.servers.firstOrNull { it.id == serverId } ?: return
        scope.launch {
            publish(state.copy(busy = true))
            val passphrase = store.get(StoreKeys.BACKUP_PASSPHRASE)
                ?: randomPassword(32).also { store.put(StoreKeys.BACKUP_PASSPHRASE, it) }
            runCatching {
                val payload = json.encodeToString(
                    BackupSnapshot(
                        sessionId = session.id,
                        name = session.name,
                        address = session.connection?.address ?: "",
                        username = session.connection?.username ?: "",
                        createdAt = Clock.System.now().toEpochMilliseconds(),
                        operations = session.operations.map { "${it.kind}:${it.result}" },
                    ),
                )
                val sealed = withContext(Dispatchers.Default) {
                    BackupCodec.encrypt(payload.encodeToByteArray(), passphrase)
                }
                val parts = session.repositoryFullName.split("/")
                val path = "backups/${session.id}/${Clock.System.now().toEpochMilliseconds()}.enc"
                val response = client.putContent(parts[0], parts[1], path, base64(sealed), "Encrypted backup for ${session.id}")
                val record = BackupRecord(
                    id = path,
                    createdAtLabel = SessionMath.formatDuration(0),
                    sizeLabel = SessionMath.formatBytes(sealed.size.toLong()),
                    encrypted = true,
                    commitRef = response.commit?.sha?.take(8) ?: "",
                )
                updateServer(serverId) {
                    it.copy(
                        backups = it.backups + record,
                        operations = it.operations + OperationRecord(
                            "backup-${record.id}", OperationKind.Backup, OperationResult.Success, "", path,
                        ),
                    )
                }
                persistServers()
            }.onSuccess {
                publish(state.copy(busy = false, toast = context.getString(R.string.backup_stored)))
            }.onFailure { error ->
                fail("backup", error.message ?: context.getString(R.string.err_backup), error)
            }
        }
    }

    // ---------------------------------------------------------------- settings

    fun setTheme(mode: Int) {
        publish(state.copy(themeMode = mode))
        store.put(StoreKeys.THEME, mode.toString())
    }

    fun setLocale(tag: String) {
        publish(state.copy(localeTag = tag))
        store.put(StoreKeys.LOCALE, tag)
    }

    fun setExpiryAlert(enabled: Boolean) {
        publish(state.copy(expiryAlertEnabled = enabled))
        store.put(ALERT_KEY, enabled.toString())
    }

    fun setWarningMinutes(minutes: Int) {
        publish(state.copy(warningMinutes = minutes))
        store.put(StoreKeys.WARNING_MINUTES, minutes.toString())
    }

    fun setPublicRepository(public: Boolean) = publish(state.copy(publicRepository = public))

    fun setNotificationPermission(granted: Boolean) {
        notificationsAllowed = granted
    }

    fun postAlert(title: String, body: String) {
        if (!notificationsAllowed) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val notification = NotificationCompat.Builder(context, RemoteHubApplication.ALERT_CHANNEL)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .build()
        manager.notify(title.hashCode(), notification)
    }

    private fun startSessionService(label: String) {
        val intent = Intent(context, app.remotehub.net.SessionService::class.java)
            .putExtra(app.remotehub.net.SessionService.EXTRA_LABEL, label)
        runCatching { context.startForegroundService(intent) }
    }

    private fun stopSessionService() {
        runCatching { context.stopService(Intent(context, app.remotehub.net.SessionService::class.java)) }
    }

    // ---------------------------------------------------------------- navigation

    fun openServer(serverId: String) {
        publish(state.copy(screen = AppScreen.Detail, activeServerId = serverId, error = null))
        scope.launch { resolveEndpoint(serverId) }
    }

    fun goSetup() = publish(state.copy(screen = AppScreen.Setup, error = null, highlightItem = null))

    /**
     * Back from Settings used to hard-code goSetup(), so leaving Settings during a
     * provision dropped the Progress screen and the run only reappeared when it
     * finished. Remember where the user came from and prefer a live run.
     */
    fun goSettings() {
        settingsReturn = state.screen
        publish(state.copy(screen = AppScreen.Settings))
    }

    fun leaveSettings() {
        val target = when {
            state.provisioning -> AppScreen.Progress
            settingsReturn != null && settingsReturn != AppScreen.Settings &&
                settingsReturn != AppScreen.Guide -> settingsReturn!!
            else -> AppScreen.Setup
        }
        settingsReturn = null
        publish(state.copy(screen = target, guideId = null))
    }

    fun goServers() = publish(state.copy(screen = AppScreen.Servers, error = null, activeServerId = null))

    fun goNetwork() = publish(state.copy(screen = AppScreen.Network, error = null))

    fun openGuide(id: String) = publish(state.copy(screen = AppScreen.Guide, guideId = id))

    fun closeGuide() = publish(state.copy(screen = AppScreen.Settings, guideId = null))

    fun togglePassword() = publish(state.copy(passwordVisible = !state.passwordVisible))

    fun clearError() = publish(state.copy(error = null))

    fun clearToast() = publish(state.copy(toast = null))

    fun clearCredentials() {
        rdpSessions.values.forEach { it.close() }
        rdpSessions.clear()
        scope.launch { node.stop() }
        github?.close()
        github = null
        store.clear()
        alerted.clear()
        publish(AppState(localeTag = state.localeTag, themeMode = state.themeMode))
    }

    fun release() {
        watchJob?.cancel()
        provisionJob?.cancel()
        rdpSessions.values.forEach { it.close() }
        rdpSessions.clear()
        node.release()
        github?.close()
        github = null
        stopSessionService()
    }

    companion object {
        const val PHONE_HOSTNAME = "remotehub-phone"
        const val REPO_KEY = "sessions.repo"
        const val ALERT_KEY = "settings.expiryalert"
        const val CHECKOUT_SHA = "11d5960a326750d5838078e36cf38b85af677262"
        const val UPLOAD_SHA = "ea165f8d65b6e75b540449e92b4886f43607fa02"
    }
}

@Serializable
data class BackupSnapshot(
    val sessionId: String,
    val name: String,
    val address: String,
    val username: String,
    val createdAt: Long,
    val operations: List<String>,
)
