package app.remotehub.app

import android.app.NotificationManager
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.view.inputmethod.InputMethodManager
import androidx.core.app.NotificationCompat
import app.remotehub.MainActivity
import app.remotehub.R
import app.remotehub.RemoteHubApplication
import app.remotehub.backup.BackupCodec
import app.remotehub.data.SecureStore
import app.remotehub.data.SecretBox
import app.remotehub.data.StoreKeys
import app.remotehub.data.randomPassword
import app.remotehub.domain.AuthMethod
import app.remotehub.domain.BackupRecord
import app.remotehub.domain.ConnectionInfo
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
import app.remotehub.github.GitHubClient
import app.remotehub.github.StoredServer
import app.remotehub.net.TailscaleApi
import app.remotehub.net.TailscaleNode
import app.remotehub.rdp.RdpSession
import app.remotehub.server.ProvisionEvent
import app.remotehub.server.ProvisionRequest
import app.remotehub.server.Provisioner
import app.remotehub.server.SessionWorkflow
import app.remotehub.server.base64
import app.remotehub.ui.CreateForm
import app.remotehub.ui.Destination
import app.remotehub.ui.RemoteMetrics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json

class SessionController(
    private val context: Context,
    private val store: SecureStore,
    private val secretBox: SecretBox,
    private val scope: CoroutineScope,
) {
    var onState: (AppState) -> Unit = {}
    var onEffect: (SideEffect) -> Unit = {}

    private var state = AppState()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }
    private var github: GitHubClient? = null
    private val node = TailscaleNode(context)
    private val rdpSessions = mutableMapOf<String, RdpSession>()
    private var provisionJob: Job? = null
    private var notificationsAllowed = false
    private val alerted = mutableSetOf<String>()
    private val metricsCache = mutableMapOf<String, RemoteMetrics>()
    private val passphrases = mutableMapOf<String, String>()

    var provisionSteps: List<ProvisionStep> = emptyList()
        private set

    val providerReady: Boolean get() = store.get(PROVIDER_TOKEN_KEY)?.isNotBlank() == true

    private fun publish(next: AppState) {
        state = next
        onState(next)
    }

    fun restore() {
        val token = store.get(StoreKeys.GITHUB_TOKEN)
        val login = store.get(StoreKeys.GITHUB_LOGIN)
        var next = state.copy(
            localeTag = store.get(StoreKeys.LOCALE) ?: "fa",
            themeMode = store.get(StoreKeys.THEME)?.toIntOrNull() ?: 0,
            authKeyInput = store.get(StoreKeys.TS_AUTH_KEY) ?: "",
            providerTokenInput = store.get(PROVIDER_TOKEN_KEY) ?: "",
            repositoryName = store.get(REPO_KEY) ?: "remote-hub-sessions",
        )
        if (token != null && login != null) {
            github = GitHubClient(token)
            next = next.copy(
                account = GitHubAccount(
                    login = login,
                    displayName = login,
                    method = AuthMethod.valueOf(store.get(StoreKeys.GITHUB_METHOD) ?: "PersonalAccessToken"),
                    grantedScopes = store.get(StoreKeys.GITHUB_SCOPES)?.split(",")?.filter { it.isNotBlank() } ?: emptyList(),
                    missingScopes = emptyList(),
                    tokenExpiresLabel = "",
                ),
            )
        }
        val stored = store.get(StoreKeys.SERVERS)
        if (stored != null) {
            runCatching {
                val list = json.decodeFromString<List<StoredServer>>(stored)
                next = next.copy(servers = list.map { it.toSession() })
            }
        }
        publish(next)
        if (token != null) refreshAccount()
        if (next.servers.any { it.state == ServerState.Provisioning }) {
            next.servers.filter { it.state == ServerState.Provisioning }.forEach { watchRun(it.id) }
        }
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
        spec = ServerSpec(0, 0, 0, "", "", ""),
        connection = if (address.isBlank()) null else ConnectionInfo(address, port, username, password, ""),
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

    fun acceptToken(token: String, method: AuthMethod, scopes: List<String>?) {
        val trimmed = token.trim()
        if (trimmed.isBlank()) {
            publish(state.copy(error = "token is empty"))
            return
        }
        scope.launch {
            publish(state.copy(busy = true, error = null))
            val client = GitHubClient(trimmed)
            runCatching { client.validate() }
                .onSuccess { validation ->
                    github?.close()
                    github = client
                    store.put(StoreKeys.GITHUB_TOKEN, trimmed)
                    store.put(StoreKeys.GITHUB_LOGIN, validation.user.login)
                    store.put(StoreKeys.GITHUB_METHOD, method.name)
                    store.put(StoreKeys.GITHUB_SCOPES, validation.scopes.joinToString(","))
                    publish(
                        state.copy(
                            busy = false,
                            patInput = "",
                            account = GitHubAccount(
                                login = validation.user.login,
                                displayName = validation.user.name ?: validation.user.login,
                                method = method,
                                grantedScopes = validation.scopes,
                                missingScopes = validation.missing,
                                tokenExpiresLabel = "",
                            ),
                            destination = Destination.Network,
                        ),
                    )
                }
                .onFailure { error ->
                    client.close()
                    publish(state.copy(busy = false, error = error.message ?: "validation failed"))
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
        store.remove(StoreKeys.GITHUB_TOKEN)
        store.remove(StoreKeys.GITHUB_LOGIN)
        store.remove(StoreKeys.GITHUB_METHOD)
        store.remove(StoreKeys.GITHUB_SCOPES)
        publish(state.copy(account = null))
    }

    fun setProviderToken(token: String) {
        store.put(PROVIDER_TOKEN_KEY, token.trim())
        publish(state.copy(providerTokenInput = token.trim()))
    }

    fun connectNetwork(authKey: String) {
        val key = authKey.trim()
        if (key.isBlank()) {
            publish(state.copy(error = "auth key is empty"))
            return
        }
        scope.launch {
            publish(state.copy(tailnet = state.tailnet.copy(state = NetworkState.Authenticating), busy = true))
            store.put(StoreKeys.TS_AUTH_KEY, key)
            runCatching {
                node.start(hostname = "remotehub-phone", authKey = key, ephemeral = true)
            }.onSuccess { status ->
                publish(
                    state.copy(
                        busy = false,
                        tailnet = TailnetStatus(
                            state = if (status.running) NetworkState.Online else NetworkState.Error,
                            tailnetName = status.tailnet,
                            nodeName = status.hostname,
                            nodeAddress = status.ipv4,
                            relayLabel = status.relay,
                            latencyMillis = 0,
                            transportLabel = if (status.userspace) "userspace" else "tun",
                            lastError = status.error.ifBlank { null },
                        ),
                    ),
                )
                node.startPolling { update -> applyNodeStatus(update) }
            }.onFailure { error ->
                publish(
                    state.copy(
                        busy = false,
                        tailnet = state.tailnet.copy(
                            state = NetworkState.Error,
                            lastError = error.message ?: "tailscale start failed",
                        ),
                    ),
                )
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
        if (status.authUrl.isNotBlank() && state.tailnet.state == NetworkState.Authenticating) {
            pendingLoginUrl = status.authUrl
        }
    }

    private var pendingLoginUrl: String? = null

    fun openTailscaleLogin() {
        val url = pendingLoginUrl ?: node.loginUrl()
        if (url.isBlank()) {
            connectNetwork(state.authKeyInput)
            return
        }
        onEffect(SideEffect.OpenUrl(url))
    }

    fun refreshNetworkStatus() {
        if (!node.running) return
        applyNodeStatus(node.status())
    }

    fun stopPolling() = node.stopPolling()

    fun disconnectNetwork() {
        scope.launch {
            rdpSessions.values.forEach { it.close() }
            rdpSessions.clear()
            node.stop()
            publish(state.copy(tailnet = state.tailnet.copy(state = NetworkState.Offline, nodeAddress = "")))
        }
    }

    fun quota(): QuotaSnapshot = QuotaSnapshot(
        usedMinutes = state.servers.sumOf { it.durationMinutes - (SessionMath.remainingMillis(it.startedAtEpochMs, it.durationMinutes, Clock.System.now().toEpochMilliseconds()) / 60000).toInt() },
        includedMinutes = 2000,
        concurrentJobs = state.servers.count { it.state == ServerState.Provisioning },
        concurrentJobLimit = 20,
        jobMaxDurationLabel = "20m",
    )

    fun provision(form: CreateForm) {
        val client = github
        val owner = state.account?.login
        if (client == null || owner == null) {
            publish(state.copy(error = "connect GitHub first"))
            return
        }
        val authKey = store.get(StoreKeys.TS_AUTH_KEY)
        if (authKey.isNullOrBlank()) {
            publish(state.copy(error = "connect the private network first"))
            return
        }
        val providerToken = store.get(PROVIDER_TOKEN_KEY)
        if (providerToken.isNullOrBlank()) {
            publish(state.copy(error = "add your cloud provider token in Settings"))
            return
        }
        if (form.name.isBlank()) {
            publish(state.copy(error = "server name is required"))
            return
        }
        publish(state.copy(provisioning = true, error = null))
        provisionSteps = emptyList()
        provisionJob = scope.launch {
            val provisioner = Provisioner(
                client = client,
                secretBox = secretBox,
                owner = owner,
                workflowTemplate = SessionWorkflow.render(CHECKOUT_SHA, UPLOAD_SHA),
                provider = form.provider,
            )
            runCatching {
                provisioner.run(
                    ProvisionRequest(
                        serverName = form.name,
                        repositoryName = state.repositoryName,
                        tailscaleAuthKey = authKey,
                        providerToken = providerToken,
                        durationMinutes = 120,
                        warningMinutes = form.warningMinutes,
                        desktopWidth = 1600,
                        desktopHeight = 900,
                        region = form.region,
                        machineSize = form.machineSize,
                        osImage = form.osImage,
                        autoBackup = form.autoBackup,
                    ),
                ) { event ->
                    when (event) {
                        is ProvisionEvent.Steps -> {
                            provisionSteps = event.steps
                            publish(state.copy())
                        }
                        is ProvisionEvent.Log -> Unit
                        is ProvisionEvent.Ready -> Unit
                        is ProvisionEvent.Failed -> publish(state.copy(error = event.message))
                    }
                }
            }.onSuccess { outcome ->
                val session = ServerSession(
                    id = outcome.sessionId,
                    name = form.name,
                    state = ServerState.Provisioning,
                    spec = ServerSpec(0, 0, 0, form.osImage, form.region, form.provider),
                    connection = ConnectionInfo("", 3389, outcome.username, outcome.password, ""),
                    steps = provisionSteps,
                    uptimeLabel = "",
                    remainingLabel = "",
                    remainingFraction = 1f,
                    warningThresholdMinutes = form.warningMinutes,
                    workflowRunNumber = outcome.runNumber,
                    workflowRunUrl = outcome.runUrl,
                    repositoryFullName = "$owner/${state.repositoryName}",
                    operations = listOf(
                        OperationRecord(outcome.sessionId, OperationKind.Provision, OperationResult.InProgress, "", "dispatched"),
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
                        creating = false,
                        wizardStep = 0,
                        servers = state.servers + session,
                        openServerId = session.id,
                    ),
                )
                persistServers()
                watchRun(session.id)
            }.onFailure { error ->
                publish(state.copy(provisioning = false, error = error.message ?: "provisioning failed"))
            }
        }
    }

    fun cancelProvision() {
        provisionJob?.cancel()
        provisionJob = null
        publish(state.copy(provisioning = false))
    }

    private fun watchRun(serverId: String) {
        val client = github ?: return
        scope.launch {
            val session = state.servers.firstOrNull { it.id == serverId } ?: return@launch
            val parts = session.repositoryFullName.split("/")
            if (parts.size != 2) return@launch
            runCatching {
                val run = client.run(parts[0], parts[1], session.runId)
                val jobs = client.jobs(parts[0], parts[1], session.runId)
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
                updateServer(serverId) { current ->
                    current.copy(
                        steps = steps,
                        state = when {
                            run.status != "completed" -> ServerState.Provisioning
                            run.conclusion == "success" -> ServerState.Ready
                            else -> ServerState.Failed
                        },
                        lastError = if (run.conclusion == "failure") "workflow failed" else null,
                    )
                }
                if (run.status == "completed" && run.conclusion == "success") {
                    resolveEndpoint(serverId)
                } else if (run.status != "completed") {
                    kotlinx.coroutines.delay(6000)
                    watchRun(serverId)
                }
            }
        }
    }

    private suspend fun resolveEndpoint(serverId: String) {
        val session = state.servers.firstOrNull { it.id == serverId } ?: return
        val apiToken = store.get(StoreKeys.TS_API_TOKEN)
        if (apiToken.isNullOrBlank()) {
            val peer = node.peers().firstOrNull { it.hostname.equals(session.id, true) || it.hostname.contains(session.id, true) }
            if (peer != null && peer.ipv4.isNotBlank()) {
                updateServer(serverId) {
                    it.copy(connection = it.connection?.copy(address = peer.ipv4), state = ServerState.Ready)
                }
                persistServers()
            } else {
                kotlinx.coroutines.delay(8000)
                resolveEndpoint(serverId)
            }
            return
        }
        val api = TailscaleApi(apiToken)
        runCatching {
            val device = api.findDevice("rh-${session.id}")
            if (device != null) {
                val address = device.addresses.firstOrNull { it.count { c -> c == '.' } == 3 } ?: ""
                updateServer(serverId) {
                    it.copy(connection = it.connection?.copy(address = address), state = ServerState.Ready)
                }
                persistServers()
            }
        }
        api.close()
    }

    private fun updateServer(id: String, transform: (ServerSession) -> ServerSession) {
        publish(state.copy(servers = state.servers.map { if (it.id == id) transform(it) else it }))
    }

    fun tick() {
        if (state.servers.isEmpty()) return
        val now = Clock.System.now().toEpochMilliseconds()
        var changed = false
        val updated = state.servers.map { session ->
            if (session.startedAtEpochMs == 0L) return@map session
            val remaining = SessionMath.remainingMillis(session.startedAtEpochMs, session.durationMinutes, now)
            val fraction = SessionMath.remainingFraction(session.startedAtEpochMs, session.durationMinutes, now)
            val nextState = SessionMath.stateFor(session.state, session.startedAtEpochMs, session.durationMinutes, session.warningThresholdMinutes, now)
            if (nextState == ServerState.Expiring && state.expiryAlertEnabled && alerted.add(session.id)) {
                onEffect(
                    SideEffect.Notify(
                        context.getString(R.string.expiry_title),
                        "${session.name}: ${SessionMath.formatDuration(remaining)}",
                    ),
                )
            }
            changed = true
            session.copy(
                state = nextState,
                remainingLabel = SessionMath.formatDuration(remaining),
                remainingFraction = fraction,
                uptimeLabel = SessionMath.formatDuration(now - session.startedAtEpochMs),
            )
        }
        if (changed) {
            publish(state.copy(servers = updated))
            updated.forEach { session ->
                val rdp = rdpSessions[session.id] ?: return@forEach
                metricsCache[session.id] = rdp.metrics()
            }
            if (state.remoteServerId != null) publish(state.copy())
        }
    }

    fun remoteMetrics(serverId: String): RemoteMetrics =
        metricsCache[serverId] ?: RemoteMetrics(0, 0, 0, "—", false, true)

    fun connectRemote(serverId: String) {
        val session = state.servers.firstOrNull { it.id == serverId } ?: return
        val connection = session.connection
        if (connection == null || connection.address.isBlank()) {
            publish(state.copy(error = "endpoint is not published yet"))
            return
        }
        if (!node.running) {
            publish(state.copy(error = "connect the private network first"))
            return
        }
        scope.launch {
            publish(state.copy(remoteServerId = serverId, busy = true))
            metricsCache[serverId] = RemoteMetrics(0, 0, 0, "—", false, true)
            val existing = rdpSessions[serverId]
            if (existing != null) {
                publish(state.copy(busy = false))
                return@launch
            }
            val rdp = RdpSession(context, node, session, store)
            rdpSessions[serverId] = rdp
            rdp.onUpdate = { metrics ->
                metricsCache[serverId] = metrics
                publish(state.copy())
            }
            rdp.onError = { message -> publish(state.copy(error = message)) }
            runCatching { rdp.connect() }
                .onSuccess {
                    updateServer(serverId) { it.copy(state = ServerState.Connected) }
                    startSessionService(session.name)
                    publish(state.copy(busy = false))
                }
                .onFailure { error ->
                    rdpSessions.remove(serverId)
                    publish(state.copy(busy = false, error = error.message ?: "rdp connect failed"))
                }
        }
    }

    fun disconnectRemote(serverId: String) {
        rdpSessions.remove(serverId)?.close()
        metricsCache.remove(serverId)
        updateServer(serverId) { it.copy(state = ServerState.Ready) }
        stopSessionService()
        publish(state.copy(remoteServerId = null))
    }

    fun rdpSession(serverId: String): RdpSession? = rdpSessions[serverId]

    fun showKeyboard() {
        val manager = context.getSystemService(InputMethodManager::class.java)
        manager?.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0)
    }

    fun pushClipboard(serverId: String) {
        val manager = context.getSystemService(ClipboardManager::class.java)
        val text = manager?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
        if (text.isNullOrBlank()) {
            publish(state.copy(error = "clipboard is empty"))
            return
        }
        val sent = rdpSessions[serverId]?.sendClipboard(text) ?: false
        publish(state.copy(toast = if (sent) "clipboard sent" else "clipboard channel not ready"))
    }

    fun sendCtrlAltDel(serverId: String) {
        rdpSessions[serverId]?.sendCtrlAltDel()
    }

    fun toggleInputMode(serverId: String) {
        val current = metricsCache[serverId] ?: return
        metricsCache[serverId] = current.copy(trackpadMode = !current.trackpadMode)
        rdpSessions[serverId]?.trackpadMode = !current.trackpadMode
        publish(state.copy())
    }

    fun toggleFullscreen() {
        (context as? MainActivity)?.let { activity ->
            activity.window.decorView.systemUiVisibility =
                activity.window.decorView.systemUiVisibility xor android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
        }
    }

    fun stopServer(serverId: String) = lifecycle(serverId, "destroy", OperationKind.Stop, ServerState.Stopping)

    fun restartServer(serverId: String) = lifecycle(serverId, "provision", OperationKind.Restart, ServerState.Provisioning)

    fun rebuildServer(serverId: String) = lifecycle(serverId, "provision", OperationKind.Rebuild, ServerState.Provisioning)

    private fun lifecycle(serverId: String, action: String, kind: OperationKind, nextState: ServerState) {
        val client = github ?: return
        val session = state.servers.firstOrNull { it.id == serverId } ?: return
        scope.launch {
            updateServer(serverId) { it.copy(state = nextState) }
            val parts = session.repositoryFullName.split("/")
            if (parts.size != 2) return@launch
            runCatching {
                if (action == "destroy") {
                    runCatching { client.cancel(parts[0], parts[1], session.runId) }
                }
                client.dispatch(
                    parts[0], parts[1], SessionWorkflow.FILE_NAME, "main",
                    mapOf("session_id" to session.id, "action" to action, "minutes" to session.durationMinutes.toString()),
                )
            }.onSuccess {
                updateServer(serverId) {
                    it.copy(
                        operations = it.operations + OperationRecord(
                            id = "${kind.name}-${Clock.System.now().toEpochMilliseconds()}",
                            kind = kind,
                            result = OperationResult.InProgress,
                            timestampLabel = "",
                            message = action,
                        ),
                    )
                }
                if (action == "destroy") {
                    rdpSessions.remove(serverId)?.close()
                    metricsCache.remove(serverId)
                    val apiToken = store.get(StoreKeys.TS_API_TOKEN)
                    if (!apiToken.isNullOrBlank()) {
                        val api = TailscaleApi(apiToken)
                        runCatching {
                            api.findDevice("rh-${session.id}")?.let { api.deleteDevice(it.id) }
                        }
                        api.close()
                    }
                    updateServer(serverId) { it.copy(state = ServerState.Stopped) }
                } else {
                    kotlinx.coroutines.delay(6000)
                    val latest = runCatching { client.latestDispatchRun(parts[0], parts[1]) }.getOrNull()
                    if (latest != null) {
                        updateServer(serverId) {
                            it.copy(runId = latest.id, workflowRunNumber = latest.runNumber, workflowRunUrl = latest.htmlUrl)
                        }
                        watchRun(serverId)
                    }
                }
                persistServers()
            }.onFailure { error ->
                updateServer(serverId) { it.copy(state = ServerState.Failed, lastError = error.message) }
            }
        }
    }

    fun rerunWorkflow(serverId: String) {
        val client = github ?: return
        val session = state.servers.firstOrNull { it.id == serverId } ?: return
        scope.launch {
            val parts = session.repositoryFullName.split("/")
            if (parts.size != 2) return@launch
            runCatching { client.rerun(parts[0], parts[1], session.runId) }
                .onSuccess {
                    updateServer(serverId) {
                        it.copy(
                            state = ServerState.Provisioning,
                            operations = it.operations + OperationRecord(
                                "rerun-${Clock.System.now().toEpochMilliseconds()}",
                                OperationKind.RerunWorkflow,
                                OperationResult.InProgress,
                                "",
                                "run ${session.runId}",
                            ),
                        )
                    }
                    watchRun(serverId)
                }
                .onFailure { error -> publish(state.copy(error = error.message)) }
        }
    }

    fun createBackup(serverId: String) {
        val client = github ?: return
        val session = state.servers.firstOrNull { it.id == serverId } ?: return
        scope.launch {
            publish(state.copy(busy = true))
            val passphrase = passphrases.getOrPut(serverId) {
                store.get(StoreKeys.BACKUP_PASSPHRASE) ?: randomPassword(32).also {
                    store.put(StoreKeys.BACKUP_PASSPHRASE, it)
                }
            }
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
                publish(state.copy(busy = false, toast = "backup stored"))
            }.onFailure { error ->
                publish(state.copy(busy = false, error = error.message ?: "backup failed"))
            }
        }
    }

    fun restoreBackup(serverId: String, backupId: String) {
        publish(state.copy(toast = "restore requires a running host: $backupId"))
    }

    fun setBiometric(enabled: Boolean) {
        publish(state.copy(biometricEnabled = enabled))
        store.put(BIOMETRIC_KEY, enabled.toString())
    }

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
        val intent = Intent(context, app.remotehub.net.SessionService::class.java).apply {
            putExtra(app.remotehub.net.SessionService.EXTRA_LABEL, label)
        }
        runCatching { context.startForegroundService(intent) }
    }

    private fun stopSessionService() {
        runCatching { context.stopService(Intent(context, app.remotehub.net.SessionService::class.java)) }
    }

    fun clearCredentials() {
        rdpSessions.values.forEach { it.close() }
        rdpSessions.clear()
        scope.launch { node.stop() }
        github?.close()
        github = null
        store.clear()
        passphrases.clear()
        alerted.clear()
        publish(AppState(localeTag = state.localeTag, themeMode = state.themeMode))
    }

    fun release() {
        rdpSessions.values.forEach { it.close() }
        rdpSessions.clear()
        node.release()
        github?.close()
        github = null
        stopSessionService()
    }

    companion object {
        const val PROVIDER_TOKEN_KEY = "provider.token"
        const val REPO_KEY = "sessions.repo"
        const val BIOMETRIC_KEY = "settings.biometric"
        const val CHECKOUT_SHA = "11d5960a326750d5838078e36cf38b85af677262"
        const val UPLOAD_SHA = "ea165f8d65b6e75b540449e92b4886f43607fa02"
    }
}

@kotlinx.serialization.Serializable
data class BackupSnapshot(
    val sessionId: String,
    val name: String,
    val address: String,
    val username: String,
    val createdAt: Long,
    val operations: List<String>,
)
